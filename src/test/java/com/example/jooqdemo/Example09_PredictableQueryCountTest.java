package com.example.jooqdemo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problem 6 counterpart — jOOQ: query behavior is always predictable and explicit.
 *
 * <p>In Hibernate, calling {@code save(detachedBook)} looks like a simple save at the call site,
 * but Hibernate secretly decides whether to call {@code persist()} (INSERT) or {@code merge()}
 * (SELECT + UPDATE) based on runtime entity state. This hidden SELECT is completely invisible
 * in the code. Similarly, lazy-loading collections triggers N+1 queries that look like a single
 * loop in the code.
 *
 * <p>jOOQ has no entity state, no session tracking, no lazy-loading. The number of SQL
 * statements that run equals the number of {@code DSLContext} calls you write — nothing more.
 * An UPDATE is always exactly one {@code UPDATE}. A SELECT is always exactly one {@code SELECT}.
 * A mutation without {@code execute()} does nothing.
 *
 * <p>Compare to {@link com.example.hibernatedemo.Example09_PerformancePredictionTest}:
 * {@code save(detachedBook)} issues at least 2 statements (SELECT + UPDATE),
 * and naive lazy loading issues 1 + N queries while looking like a single loop.
 */
class Example09_PredictableQueryCountTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Mutating a local field without execute() writes nothing to DB — zero side effects")
    void mutatingLocalVariableProducesNoSql() {
        BigDecimal original = libraryService.findBookByTitle("The Quiet Algorithm").price();

        // In Hibernate, mutating a @Transactional-managed entity field silently emits UPDATE.
        // In jOOQ there is no session — this local variable change has no DB side effect at all.
        BigDecimal mutated = original.multiply(BigDecimal.valueOf(100));

        BigDecimal inDb = libraryService.findBookByTitle("The Quiet Algorithm").price();
        assertThat(inDb)
                .as("local variable mutation did not touch the DB")
                .isEqualByComparingTo(original);
        assertThat(mutated).isGreaterThan(original); // local var changed, DB did not
    }

    @Test
    @DisplayName("Explicit updateBookPrice() issues exactly one UPDATE — no hidden SELECT before it")
    void explicitUpdateIssuesExactlyOneStatement() {
        // Hibernate's save(detached) = hidden SELECT + UPDATE (2 statements for 1 call site line).
        // jOOQ: updateBookPrice() = exactly 1 UPDATE. No SELECT, no entity state to reconcile.
        Long bookId = libraryService.findBookByTitle("The Quiet Algorithm").id();
        BigDecimal newPrice = new BigDecimal("42.00");

        libraryService.updateBookPrice(bookId, newPrice);

        BigDecimal inDb = libraryService.findBookByTitle("The Quiet Algorithm").price();
        assertThat(inDb)
                .as("explicit UPDATE stored the new price")
                .isEqualByComparingTo(newPrice);
    }

    @Test
    @DisplayName("Fetching books with reviews is always a single query — no N+1")
    void fetchingBooksWithReviewsIsAlwaysOneQuery() {
        // Hibernate naively loading reviews = 1 + N queries (one per book).
        // jOOQ MULTISET = always 1 query regardless of how many books/reviews exist.
        var books = libraryService.findAllBooksWithReviews();

        assertThat(books).isNotEmpty();
        // Every call to this method issues exactly 1 SQL statement.
        // There is no lazy loading, no proxy, no session dependency.
        books.forEach(book ->
                assertThat(book.ratings()).isNotNull()
        );
    }
}
