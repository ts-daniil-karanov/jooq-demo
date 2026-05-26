package com.example.jooqdemo;

import com.example.jooqdemo.generated.tables.records.AuthorRecord;
import com.example.jooqdemo.generated.tables.records.BookRecord;
import com.example.jooqdemo.service.LibraryService.RankedBook;
import org.jooq.impl.DefaultDSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.example.jooqdemo.generated.tables.Author.AUTHOR;
import static com.example.jooqdemo.generated.tables.Book.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 3 — window functions through the service layer.
 *
 * <p>{@code LibraryService.findTopNMostExpensiveBooksPerCountry()} uses jOOQ's type-safe window function
 * API ({@code rowNumber().over().partitionBy(AUTHOR.COUNTRY).orderBy(BOOK.PRICE.desc())}).
 * The whole expression is compiler-checked — no raw SQL string anywhere.
 *
 * <p>Compare to the Hibernate version where JPQL has no window function support at all
 * and the service must fall back to a native SQL string with {@code Object[]} mapping.
 */
class Example03_WindowFunctionTest extends AbstractIntegrationTest {
    @Autowired
    private DefaultDSLContext dslContext;

    @Test
    @DisplayName("Top 2 most expensive books per country (type-safe window functions)")
    void topPricedBooksPerCountry() {
        List<BookRecord> bookRecords = dslContext.selectFrom(BOOK).fetchInto(BookRecord.class);
        System.out.println(bookRecords);

        List<AuthorRecord> records =  dslContext.selectFrom(AUTHOR).fetchInto(AuthorRecord.class);
        System.out.println(records);

        List<RankedBook> rows = libraryService.findTopNMostExpensiveBooksPerCountry(2);
        System.out.println(rows);

        assertThat(rows).hasSize(6);
        assertThat(rows.stream().map(RankedBook::title).toList())
                .contains("Streams in Production", "Distributed by Default",
                          "Le Cache Intransigent", "Kaizen for Code",
                          "Quiet Refactors", "Tundra Engineering");
    }
}
