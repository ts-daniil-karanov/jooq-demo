import org.jooq.meta.jaxb.Logging

import java.io.File
import java.net.URLClassLoader

plugins {
    id("java")
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("nu.studer.jooq") version "9.0"
}

group = "com.example"
version = "0.1.0"

extra["testcontainers.version"] = "1.21.3"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val jooqVersion = "3.19.16"

// ── Database connection for code generation ───────────────────────────────────
// Matches docker-compose.yml defaults. Override with env vars if needed:
//   CODEGEN_DB_URL / CODEGEN_DB_USER / CODEGEN_DB_PASSWORD
val codegenDbUrl  = System.getenv("CODEGEN_DB_URL")      ?: "jdbc:mariadb://localhost:3306/jooq_demo"
val codegenDbUser = System.getenv("CODEGEN_DB_USER")     ?: "jooq"
val codegenDbPass = System.getenv("CODEGEN_DB_PASSWORD") ?: "jooq"

// ── Flyway classpath (isolated — the Flyway Gradle plugin is incompatible with Gradle 9) ────
// Resolved at task-execution time via URLClassLoader; never leaks into the main compile classpath.
val codegenFlyway: Configuration by configurations.creating {
    isTransitive = true
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.mariadb.jdbc:mariadb-java-client")
    implementation("org.flywaydb:flyway-mysql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MariaDB driver on the jOOQ generator classpath (needed to connect at generateJooq time)
    jooqGenerator("org.mariadb.jdbc:mariadb-java-client")

    // Flyway JARs for the standalone flywayMigrate task (versions come from the Spring Boot BOM)
    codegenFlyway("org.flywaydb:flyway-core")
    codegenFlyway("org.flywaydb:flyway-mysql")
    codegenFlyway("org.mariadb.jdbc:mariadb-java-client")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

// ── Flyway migration task ─────────────────────────────────────────────────────
// The official Flyway Gradle plugin (org.flywaydb.flyway) references the removed
// JavaPluginConvention API and throws on Gradle 9.  Instead we resolve the Flyway
// JARs into the isolated `codegenFlyway` configuration and invoke Flyway
// programmatically through URLClassLoader + reflection so nothing leaks into the
// main compile or runtime classpaths.
tasks.register("flywayMigrate") {
    group = "database"
    description = "Runs Flyway migrations against the local MariaDB instance (alternative to docker compose up -d)"

    val migrationsDir = layout.projectDirectory.dir("src/main/resources/db/migration").asFile
    inputs.dir(migrationsDir)
    // Re-run whenever the resolved JARs change (e.g. after a Flyway version bump)
    inputs.files(codegenFlyway)

    doLast {
        val urls = codegenFlyway.resolve().map { it.toURI().toURL() }.toTypedArray()
        // Platform classloader as parent — gives Flyway access to java.sql.* without
        // leaking any Gradle or project classes into the isolated loader.
        val loader = URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
        // Flyway 10 discovers database plugins via ServiceLoader, which defaults to the
        // thread context classloader.  Swap it in so ServiceLoader finds flyway-mysql.
        val originalCtxCl = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = loader
        try {
            val flywayClass = loader.loadClass("org.flywaydb.core.Flyway")

            // Flyway.configure() → FluentConfiguration (static factory)
            var cfg: Any = flywayClass.getMethod("configure").invoke(null)!!

            // Fluent chain — each method returns `this` (FluentConfiguration).
            // Cast to Any before .javaClass so Kotlin resolves the non-nullable extension.
            cfg = (cfg as Any).javaClass
                .getMethod("dataSource", String::class.java, String::class.java, String::class.java)
                .invoke(cfg, codegenDbUrl, codegenDbUser, codegenDbPass)!!

            // locations(String...) is a vararg; cast to Any so Kotlin doesn't spread the array
            cfg = (cfg as Any).javaClass
                .getMethod("locations", Array<String>::class.java)
                .invoke(cfg, arrayOf("filesystem:${migrationsDir.absolutePath}") as Any)!!

            cfg = (cfg as Any).javaClass
                .getMethod("cleanOnValidationError", Boolean::class.javaPrimitiveType)
                .invoke(cfg, true)!!

            // FluentConfiguration.load() → Flyway instance
            val flyway: Any = (cfg as Any).javaClass.getMethod("load").invoke(cfg)!!

            // Flyway.migrate() → MigrateResult
            val result: Any = (flyway as Any).javaClass.getMethod("migrate").invoke(flyway)!!
            // MigrateResult.migrationsExecuted is a public field in Flyway 10, not a getter
            val count = (result as Any).javaClass.getField("migrationsExecuted").get(result) as Int
            logger.lifecycle("Flyway: $count migration(s) applied.")
        } finally {
            Thread.currentThread().contextClassLoader = originalCtxCl
            loader.close()
        }
    }
}

// ── jOOQ code generation from the real MariaDB schema ────────────────────────
// Generated classes are written to src/main/generated/ and committed to git.
// This means ./gradlew build works without any running database.
//
// To regenerate after a schema change:
//   docker compose up -d        ← starts MariaDB + runs Flyway migrations
//   ./gradlew generateJooq      ← connects to the live DB, writes src/main/generated/
//   git add src/main/generated
//   git commit
jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false) // never auto-regenerate on compile
            jooqConfiguration.apply {
                logging = Logging.WARN
                jdbc.apply {
                    driver   = "org.mariadb.jdbc.Driver"
                    url      = codegenDbUrl
                    user     = codegenDbUser
                    password = codegenDbPass
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name        = "org.jooq.meta.mariadb.MariaDBDatabase"
                        inputSchema = "jooq_demo"
                        // Exclude Flyway's own bookkeeping table from the generated code.
                        excludes    = "flyway_schema_history"
                    }
                    generate.apply {
                        isDeprecated     = false
                        isRecords        = true
                        isImmutablePojos = false
                        isFluentSetters  = false
                    }
                    target.apply {
                        packageName = "com.example.jooqdemo.generated"
                        // Committed to git — no running DB needed to compile or test.
                        directory   = "src/main/generated"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

// ── Source sets ───────────────────────────────────────────────────────────────
// src/main/generated is committed to git and treated as a regular source directory.
// compileJava does NOT depend on generateJooq — the committed files compile as-is.
sourceSets {
    main {
        java {
            srcDir("src/main/generated")
        }
    }
}

// generateJooq must see the current schema → run migrations first
tasks.named("generateJooq") {
    dependsOn("flywayMigrate")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // On macOS with Rancher Desktop there is no /var/run/docker.sock.
    // Testcontainers falls back to EnvironmentAndSystemPropertyClientProviderStrategy only when
    // DOCKER_HOST is present in the *environment* (not just as a system property).
    val rdSock = "${System.getProperty("user.home")}/.rd/docker.sock"
    if (System.getenv("DOCKER_HOST") == null && File(rdSock).exists()) {
        environment("DOCKER_HOST", "unix://$rdSock")
        // docker-java reads api.version via system properties (not DOCKER_API_VERSION env var).
        systemProperty("api.version", "1.47")
    }
}
