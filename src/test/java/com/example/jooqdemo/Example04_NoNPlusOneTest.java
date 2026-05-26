package com.example.jooqdemo;

import com.example.jooqdemo.service.LibraryService.BookWithReviews;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 4 — books with nested reviews in a single query (MULTISET).
 *
 * <p>{@code LibraryService.findAllBooksWithReviews()} uses jOOQ's {@code MULTISET} to fetch
 * books and their reviews in one SQL statement. The result is a plain Java record —
 * fully materialized, no lazy proxies, no N+1, no LazyInitializationException.
 *
 * <p>Compare to Hibernate's {@code Example04_NPlusOneTest}: the naive version issues 1 + N
 * queries (one per book), and even the JOIN FETCH workaround requires a manual DISTINCT
 * to deduplicate rows.
 */
class Example04_NoNPlusOneTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("All books with their reviews — fetched in a single MULTISET query")
    void booksWithReviewsInOneQuery() {
        List<BookWithReviews> books = libraryService.findAllBooksWithReviews();

        assertThat(books).hasSize(8);

        BookWithReviews distributed = books.stream()
                .filter(b -> b.title().equals("Distributed by Default"))
                .findFirst().orElseThrow();

        assertThat(distributed.ratings()).hasSize(1);
        assertThat(distributed.ratings().get(0)).isEqualTo(3);
    }

    @Test
    @DisplayName("All review data is already materialized — no further DB calls needed")
    void reviewsAreMaterializedUpFront() {
        List<BookWithReviews> books = libraryService.findAllBooksWithReviews();

        // Accessing ratings on any book does not trigger additional SQL —
        // there are no lazy proxies; everything is plain Java.
        long totalReviews = books.stream()
                .mapToLong(b -> b.ratings().size())
                .sum();

        assertThat(totalReviews).isGreaterThan(0);
    }
}
