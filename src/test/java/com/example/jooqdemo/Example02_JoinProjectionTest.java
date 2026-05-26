package com.example.jooqdemo;

import com.example.jooqdemo.service.LibraryService.BookView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 2 — JOIN with projection through the service layer.
 *
 * <p>{@code LibraryService.findBooksByCountry()} joins books with their authors using
 * type-safe jOOQ column references. Any column rename in the migration triggers a codegen
 * rerun and then a compile error in the service — the bug surfaces before tests run.
 * Compare to Hibernate's JPQL constructor expression where the field name is a raw string.
 */
class Example02_JoinProjectionTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("Books with their author name for US, ordered by price descending")
    void booksWithAuthorByCountry() {
        List<BookView> rows = libraryService.findBooksByCountry("US");

        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).title()).isEqualTo("Streams in Production");
        assertThat(rows.get(0).price()).isEqualByComparingTo("41.75");
        assertThat(rows.get(0).authorName()).isEqualTo("John Williams");
    }
}
