import org.jooq.meta.jaxb.Logging

import java.io.File

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

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.mariadb.jdbc:mariadb-java-client")
    implementation("org.flywaydb:flyway-mysql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // MariaDB driver on the jOOQ generator classpath (needed to connect at generateJooq time)
    jooqGenerator("org.mariadb.jdbc:mariadb-java-client")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mariadb")
    testImplementation("org.testcontainers:testcontainers")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
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
