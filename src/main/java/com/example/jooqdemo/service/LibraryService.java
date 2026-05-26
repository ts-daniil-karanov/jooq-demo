package com.example.jooqdemo.service;

import com.example.jooqdemo.generated.tables.records.AuthorRecord;
import com.example.jooqdemo.repository.AuthorRepository;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.Table;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.example.jooqdemo.generated.Tables.AUTHOR;
import static com.example.jooqdemo.generated.Tables.BOOK;
import static com.example.jooqdemo.generated.Tables.REVIEW;
import static com.example.jooqdemo.generated.Tables.USER_BALANCE;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;

@Service
@Transactional
@RequiredArgsConstructor
public class LibraryService {

    private final DSLContext dsl;
    private final AuthorRepository authorRepository;

    // ── Example 01 ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<String> findAuthorNamesByCountry(String country) {
        return authorRepository.findNamesByCountry(country);
    }

    // ── Example 02 ────────────────────────────────────────────────────────────

    public record BookView(String title, BigDecimal price, String authorName) {}

    @Transactional(readOnly = true)
    public List<BookView> findBooksByCountry(String country) {
        return dsl.select(BOOK.TITLE, BOOK.PRICE, AUTHOR.NAME)
                .from(BOOK)
                .join(AUTHOR).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
                .where(AUTHOR.COUNTRY.eq(country))
                .orderBy(BOOK.PRICE.desc())
                .fetch(r -> new BookView(r.get(BOOK.TITLE), r.get(BOOK.PRICE), r.get(AUTHOR.NAME)));
    }

    // ── Example 03 ────────────────────────────────────────────────────────────

    public record RankedBook(String title, BigDecimal price, String country) {}

    /**
     * Returns the {@code topN} most expensive books for each country, ranked by price descending.
     *
     * <p>Window functions are first-class in jOOQ — {@code rowNumber().over().partitionBy()} is
     * fully type-safe and compiler-checked. No native SQL string, no {@code Object[]} mapping.
     *
     * <p>Compare to Hibernate where JPQL has no window function support at all and the service
     * must fall back to a raw native SQL string with hand-written {@code Object[]} mapping.
     */
    @Transactional(readOnly = true)
    public List<RankedBook> findTopNMostExpensiveBooksPerCountry(int topN) {
        var rn = rowNumber()
                .over()
                .partitionBy(AUTHOR.COUNTRY)
                .orderBy(BOOK.PRICE.desc())
                .as("rn");

        Table<Record4<String, BigDecimal, String, Integer>> ranked = dsl
                .select(BOOK.TITLE, BOOK.PRICE, AUTHOR.COUNTRY, rn)
                .from(BOOK)
                .join(AUTHOR).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
                .asTable("ranked");

        return dsl.select(
                        ranked.field("title", String.class),
                        ranked.field("price", BigDecimal.class),
                        ranked.field("country", String.class))
                .from(ranked)
                .where(ranked.field("rn", Integer.class).le(topN))
                .orderBy(ranked.field("country").asc(), ranked.field("price").desc())
                .fetch(r -> new RankedBook(
                        r.get(ranked.field("title", String.class)),
                        r.get(ranked.field("price", BigDecimal.class)),
                        r.get(ranked.field("country", String.class))));
    }

    // ── Example 04 ────────────────────────────────────────────────────────────

    public record BookWithReviews(String title, List<Integer> ratings) {}

    /**
     * MULTISET returns books with their reviews in a single SQL statement.
     * No lazy loading, no N+1, no proxies — plain Java objects, fully materialized.
     */
    @Transactional(readOnly = true)
    public List<BookWithReviews> findAllBooksWithReviews() {
        return dsl.select(
                        BOOK.TITLE,
                        multiset(
                                select(REVIEW.RATING)
                                        .from(REVIEW)
                                        .where(REVIEW.BOOK_ID.eq(BOOK.ID))
                        ).as("ratings"))
                .from(BOOK)
                .orderBy(BOOK.TITLE)
                .fetch(r -> new BookWithReviews(
                        r.value1(),
                        r.value2().map(rev -> rev.value1())));
    }

    // ── Example 05 ────────────────────────────────────────────────────────────

    public record BookDetail(String title, BigDecimal price, String authorName, Long id) {}

    /**
     * jOOQ always returns fully materialized records — no sessions, no proxies.
     * There is no jOOQ equivalent of LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public BookDetail findBookByTitle(String title) {
        return dsl.select(BOOK.TITLE, BOOK.PRICE, AUTHOR.NAME, BOOK.ID)
                .from(BOOK)
                .join(AUTHOR).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))
                .where(BOOK.TITLE.eq(title))
                .fetchOne(r -> new BookDetail(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    // ── Example 06 — explicit UPDATE (no dirty checking) ─────────────────────

    /**
     * jOOQ requires an explicit UPDATE statement — no mutation magic.
     * If you don't call execute(), nothing is written.
     */
    @Transactional
    public void updateBookPrice(Long bookId, BigDecimal newPrice) {
        dsl.update(BOOK)
                .set(BOOK.PRICE, newPrice)
                .where(BOOK.ID.eq(bookId))
                .execute();
    }

    @Transactional
    public void updateBookPrice(String title, BigDecimal newPrice) {
        dsl.update(BOOK)
                .set(BOOK.PRICE, newPrice)
                .where(BOOK.TITLE.eq(title))
                .execute();
    }

    // ── Example 08: no entity cache — every select hits the DB ───────────────

    public record RepeatReadResult(BigDecimal beforeUpdate, BigDecimal afterUpdate) {}

    /**
     * jOOQ has no first-level entity cache. Every {@code selectFrom()} call issues a
     * real SQL query regardless of what was read before in the same transaction.
     *
     * <p>After an UPDATE (even via a raw JDBC statement), the next {@code select()}
     * always returns the current DB value — no stale data, no cache eviction needed.
     *
     * <p>Compare to {@link com.example.hibernatedemo.service.LibraryService#demonstrateL1CacheStaleRead}:
     * Hibernate returns stale cached data after a native SQL update until the L1 cache
     * is explicitly cleared via {@code em.clear()}.
     */
    @Transactional
    public RepeatReadResult demonstrateNoEntityCache(Long bookId, BigDecimal newPrice) {
        BigDecimal before = dsl.select(BOOK.PRICE).from(BOOK)
                .where(BOOK.ID.eq(bookId))
                .fetchOne(BOOK.PRICE);

        // Update via jOOQ — same session, same transaction
        dsl.update(BOOK)
                .set(BOOK.PRICE, newPrice)
                .where(BOOK.ID.eq(bookId))
                .execute();

        // Re-select: always goes to DB, always returns fresh value
        BigDecimal after = dsl.select(BOOK.PRICE).from(BOOK)
                .where(BOOK.ID.eq(bookId))
                .fetchOne(BOOK.PRICE);

        return new RepeatReadResult(before, after);
    }

    // ── Example 09: predictable query count ────────────────────────────────────

    /**
     * Returns the price of a book by ID using a single explicit SELECT.
     * With jOOQ, the number of SQL statements equals the number of DSL calls you write.
     */
    @Transactional(readOnly = true)
    public BigDecimal fetchPrice(Long bookId) {
        return dsl.select(BOOK.PRICE).from(BOOK)
                .where(BOOK.ID.eq(bookId))
                .fetchOne(BOOK.PRICE);
    }

    // ── Example 07 — concurrent insert (WORKS) ────────────────────────────────

    /**
     * Returns the author with the given name, creating it if it does not yet exist.
     *
     * <p><b>Naive catch-and-retry — works on MariaDB without any savepoint magic.</b>
     *
     * <p>When two threads race to INSERT the same author:
     * <ol>
     *   <li>The losing thread's INSERT fails with a duplicate-key constraint violation.</li>
     *   <li>jOOQ uses JDBC directly — there is no "session" object to poison.</li>
     *   <li>MariaDB does NOT abort the transaction on a constraint violation
     *       (unlike PostgreSQL, which puts the whole transaction into an "aborted" state).
     *       The JDBC connection remains valid.</li>
     *   <li>The {@code catch} block re-reads the row the winning thread created — cleanly.</li>
     * </ol>
     *
     * <p>{@code READ_COMMITTED} isolation is required so that the re-read in the {@code catch}
     * block sees the row committed by the winning thread. With the default {@code REPEATABLE_READ}
     * isolation, the transaction snapshot was taken before the winning thread committed, so the
     * re-read would return empty.
     *
     * <p>Compare to the Hibernate version ({@code Example07_ConcurrentInsertTest}):
     * the identical {@code catch + findByName} pattern <b>fails</b> there even on MariaDB,
     * because Hibernate marks its Session as {@code rollback-only} at the Java level —
     * regardless of what the database actually did.
     * The subsequent {@code findByName} throws {@code AssertionFailure} or
     * {@code UnexpectedRollbackException}.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthorRecord findOrCreate(String name, String country) {
        return authorRepository.findByName(name).orElseGet(() -> {
            try {
                return dsl.insertInto(AUTHOR, AUTHOR.NAME, AUTHOR.COUNTRY)
                        .values(name, country)
                        .returning()
                        .fetchOne();
            } catch (DataAccessException e) {
                // Another thread won the INSERT race.
                // The outer transaction is still alive — safe to re-read.
                return authorRepository.findByName(name).orElseThrow();
            }
        });
    }

    // ── Example 07 (exact replica of point-interest-api-internal) ─────────────

    /**
     * Returns the user balance for the given userId, creating it if it does not exist.
     *
     * <p>This is a 1:1 replica of the scenario in {@code point-interest-api-internal}:
     * same table structure ({@code user_id} as manually-assigned primary key, {@code version}
     * column), same catch-and-retry logic, same {@code READ_COMMITTED} isolation.
     *
     * <p><b>This implementation WORKS with jOOQ.</b>
     *
     * <p>When two threads race to INSERT the same {@code userId}:
     * <ol>
     *   <li>The losing thread's INSERT fails with a primary-key violation.</li>
     *   <li>jOOQ uses plain JDBC — there is no Session to poison. The JDBC connection
     *       remains valid after the exception.</li>
     *   <li>The re-read in the {@code catch} block returns the row committed by the
     *       winning thread cleanly (thanks to {@code READ_COMMITTED} isolation).</li>
     * </ol>
     *
     * <p>Compare to {@code Example07_ConcurrentInsertTest} in {@code hibernate-demo}:
     * the identical service code fails there because Hibernate marks the Session as
     * {@code rollback-only} after the failed INSERT — the re-read throws
     * {@code UnexpectedRollbackException: Transaction silently rolled back because it
     * has been marked as rollback-only}.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public com.example.jooqdemo.generated.tables.records.UserBalanceRecord findOrCreateUserBalance(long userId) {
        var existing = dsl.selectFrom(USER_BALANCE)
                .where(USER_BALANCE.USER_ID.eq(userId))
                .fetchOne();
        if (existing != null) {
            return existing;
        }
        try {
            return dsl.insertInto(USER_BALANCE, USER_BALANCE.USER_ID, USER_BALANCE.POINTS)
                    .values(userId, 0L)
                    .returning()
                    .fetchOne();
        } catch (Exception e) {
            // Another thread won the INSERT race.
            // JDBC connection is still alive — no session to poison.
            return dsl.selectFrom(USER_BALANCE)
                    .where(USER_BALANCE.USER_ID.eq(userId))
                    .fetchOne();
        }
    }
}
