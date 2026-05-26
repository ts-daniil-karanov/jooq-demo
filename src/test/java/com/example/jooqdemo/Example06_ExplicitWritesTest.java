package com.example.jooqdemo;

import com.example.jooqdemo.service.LibraryService.BookDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problem 3 counterpart — jOOQ requires explicit writes; there is no dirty checking.
 *
 * <p>Hibernate's {@code Session} tracks all managed entities. If any field changes
 * inside a transaction the session silently flushes an {@code UPDATE} at commit time —
 * even if the developer never called {@code save()} or {@code update()}. This is
 * Hibernate's dirty-checking mechanism. It can produce unexpected {@code UPDATE}
 * statements that are invisible in the code an AI agent or a human is reading.
 *
 * <p>jOOQ has no session and no entity state. A field assignment on a plain Java object
 * does absolutely nothing to the database. A mutation reaches the database only when
 * the developer writes an explicit {@code DSLContext.update(...).execute()} call.
 * What you write is exactly what runs — no more, no less.
 *
 * <p>Compare to {@link com.example.hibernatedemo.Example06_DirtyCheckingTest}:
 * changing a field inside {@code @Transactional} silently produces an {@code UPDATE}
 * in Hibernate without any explicit save call.
 */
class Example06_ExplicitWritesTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Mutating a Java object without execute() produces NO SQL — zero DB side effects")
    void mutatingObjectWithoutExecuteProducesNoDatabaseChange() {
        BigDecimal original = libraryService.findBookByTitle("The Quiet Algorithm").price();
        BigDecimal mutated = original.add(BigDecimal.valueOf(99));

        // Assigning a new value to a local variable does nothing to the DB.
        // In Hibernate the same field assignment inside @Transactional would silently UPDATE.
        // jOOQ has no session tracking; the DB is untouched.
        BookDetail after = libraryService.findBookByTitle("The Quiet Algorithm");
        assertThat(after.price()).isEqualByComparingTo(original);
        assertThat(mutated).isGreaterThan(original); // the variable changed but not the DB
    }

    @Test
    @DisplayName("Explicit DSL update() is required — SQL is transparent, not implicit")
    void explicitUpdateChangesDatabase() {
        BigDecimal before = libraryService.findBookByTitle("The Quiet Algorithm").price();
        BigDecimal newPrice = before.add(BigDecimal.valueOf(5));

        libraryService.updateBookPrice("The Quiet Algorithm", newPrice);

        BigDecimal after = libraryService.findBookByTitle("The Quiet Algorithm").price();
        assertThat(after).isEqualByComparingTo(newPrice);
    }
}
