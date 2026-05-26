package com.example.jooqdemo;

import com.example.jooqdemo.generated.tables.records.AuthorRecord;
import com.example.jooqdemo.generated.tables.records.BookRecord;
import org.jooq.impl.DefaultDSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.example.jooqdemo.generated.tables.Author.AUTHOR;
import static com.example.jooqdemo.generated.tables.Book.BOOK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario 1 — simple read via the service layer.
 *
 * <p>The query lives in {@code LibraryService.findAuthorNamesByCountry()}, which delegates
 * to {@code AuthorRepository}. The repository uses a jOOQ {@code TableField} reference
 * ({@code AUTHOR.NAME}, {@code AUTHOR.COUNTRY}) — a typo in a column name fails at
 * <em>compile time</em>, not at runtime. Compare to the Hibernate version where the JPQL
 * string {@code "WHERE a.country = :c"} is only validated when the test runs.
 */
class Example01_SimpleReadTest extends AbstractIntegrationTest {

    @Autowired
    private DefaultDSLContext dslContext;

    @Test
    @DisplayName("Fetch author names from a given country, ordered by name")
    void fetchAuthorsFromUs() {
        List<BookRecord> bookRecords = dslContext.selectFrom(BOOK).fetchInto(BookRecord.class);
        System.out.println(bookRecords);

        List<AuthorRecord> records =  dslContext.selectFrom(AUTHOR).fetchInto(AuthorRecord.class);
        System.out.println(records);
        
        List<String> names = libraryService.findAuthorNamesByCountry("US");
        System.out.println(names);

        assertThat(names).containsExactly("Jane Smith", "John Williams");
    }
}
