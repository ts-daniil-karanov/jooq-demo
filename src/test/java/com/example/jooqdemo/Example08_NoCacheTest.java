package com.example.jooqdemo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problem 5 counterpart — jOOQ has no first-level (L1) entity cache; every SELECT hits the DB.
 *
 * <p>Hibernate's {@code EntityManager} keeps every loaded entity in an in-memory L1 cache for the
 * duration of the transaction. A subsequent {@code findById()} for the same ID always returns the
 * cached (potentially stale) value — even if another statement in the same session modified the row.
 * Fixing this requires explicit {@code em.clear()} calls that are invisible in the service code.
 *
 * <p>jOOQ has no entity cache. Every {@code select()} statement sends a real SQL query to the
 * database and returns the current value. There is no "warm cache", no "stale read", and no
 * {@code em.clear()} to remember.
 *
 * <p>Compare to {@link com.example.hibernatedemo.Example08_FirstLevelCacheTest}:
 * in Hibernate, {@code findById()} after a native SQL UPDATE returns the OLD cached price —
 * the code sees a stale value without any warning or exception.
 */
class Example08_NoCacheTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("After an UPDATE, the next SELECT always returns the fresh DB value — no cache")
    void selectAfterUpdateReturnsCurrentValue() {
        Long bookId = libraryService.findBookByTitle("The Quiet Algorithm").id();
        BigDecimal newPrice = new BigDecimal("1234.56");

        var result = libraryService.demonstrateNoEntityCache(bookId, newPrice);

        // After the UPDATE jOOQ immediately sees the new value — no em.clear() needed
        assertThat(result.afterUpdate())
                .as("re-select after UPDATE returns current DB value — no stale cache")
                .isEqualByComparingTo(newPrice);
    }

    @Test
    @DisplayName("Both reads reflect actual DB state — before and after values always consistent")
    void beforeAndAfterValuesAreConsistent() {
        Long bookId = libraryService.findBookByTitle("The Quiet Algorithm").id();
        BigDecimal original = libraryService.findBookByTitle("The Quiet Algorithm").price();
        BigDecimal newPrice = original.add(BigDecimal.valueOf(500));

        var result = libraryService.demonstrateNoEntityCache(bookId, newPrice);

        // beforeUpdate was read before the UPDATE — matches original
        assertThat(result.beforeUpdate())
                .as("pre-update read reflects DB state before the UPDATE")
                .isEqualByComparingTo(original);

        // afterUpdate was read after the UPDATE — reflects new value
        assertThat(result.afterUpdate())
                .as("post-update read immediately reflects the new value")
                .isEqualByComparingTo(newPrice);

        // The key difference from Hibernate: beforeUpdate != afterUpdate
        // In Hibernate, BOTH would return the original (stale L1 cache) until em.clear() is called.
        assertThat(result.beforeUpdate())
                .isNotEqualByComparingTo(result.afterUpdate());
    }
}
