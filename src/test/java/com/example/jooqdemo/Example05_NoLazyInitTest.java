package com.example.jooqdemo;

import com.example.jooqdemo.service.LibraryService.BookDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problem 2 counterpart — jOOQ has no sessions, no proxies, no LazyInitializationException.
 *
 * <p>Hibernate loads entities as proxies. Accessing a lazy-loaded collection outside the
 * session that loaded the entity throws {@code LazyInitializationException} at runtime.
 * The compiler cannot detect this — it only blows up when the code runs.
 *
 * <p>jOOQ returns plain Java records that are fully materialized at fetch time.
 * Every field is populated in the same SQL query. There are no proxies, no sessions,
 * no transaction scope dependency. Accessing any field after the service method returns
 * is always safe — forever.
 *
 * <p>Compare to {@link com.example.hibernatedemo.Example05_LazyInitializationExceptionTest}:
 * touching {@code book.getReviews()} after the session closes throws
 * {@code LazyInitializationException} even though the object is in memory.
 */
class Example05_NoLazyInitTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("All fields accessible outside any session — no LazyInitializationException possible")
    void allFieldsAccessibleAfterMethodReturns() {
        // The @Transactional boundary is inside findBookByTitle — it has already closed.
        BookDetail book = libraryService.findBookByTitle("The Quiet Algorithm");

        // Plain Java record — no proxy, no session dependency, no exception.
        assertThat(book.title()).isEqualTo("The Quiet Algorithm");
        assertThat(book.price()).isPositive();
        assertThat(book.authorName()).isNotBlank();
    }

    @Test
    @DisplayName("jOOQ result is a value object — no entity state, no session, no footguns")
    void resultIsFullyMaterializedValueObject() {
        BookDetail book = libraryService.findBookByTitle("The Quiet Algorithm");

        // Calling this method 100 times in any thread, any context, will always work.
        // There is no concept of "detached" in jOOQ.
        for (int i = 0; i < 5; i++) {
            assertThat(book.title()).isNotBlank();
        }
    }
}
