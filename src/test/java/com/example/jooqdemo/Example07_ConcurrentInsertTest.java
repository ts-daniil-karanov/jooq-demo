package com.example.jooqdemo;

import com.example.jooqdemo.generated.tables.records.UserBalanceRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Problem 4 counterpart — jOOQ: catch-and-retry is SAFE because jOOQ has no stateful Session.
 *
 * <p>This test runs the exact same scenario as
 * {@link com.example.hibernatedemo.Example07_ConcurrentInsertTest} in {@code hibernate-demo}:
 * multiple threads race to INSERT a {@code UserBalance} row for the same {@code userId}.
 * The service uses the same catch-and-retry pattern.
 *
 * <p><b>This test PASSES</b> because jOOQ uses plain JDBC — there is no session object
 * to "poison". When the losing thread's INSERT violates the primary-key constraint, the JDBC
 * connection remains valid. The catch block re-reads the row committed by the winning thread
 * (visible thanks to {@code READ_COMMITTED} isolation) and returns it cleanly.
 *
 * <p>Compare to {@link com.example.hibernatedemo.Example07_ConcurrentInsertTest}:
 * the identical service code FAILS there because Hibernate marks its Session as
 * {@code rollback-only} after the failed INSERT. Any subsequent SQL in the same
 * session throws {@code UnexpectedRollbackException: Transaction silently rolled back
 * because it has been marked as rollback-only} — the exact error reported by QA.
 */
class Example07_ConcurrentInsertTest extends AbstractIntegrationTest {

    private static final int  THREADS        = 10;
    private static final long SHARED_USER_ID = 99999L;

    @Test
    @DisplayName("jOOQ + manual @Id: all threads return UserBalance — no UnexpectedRollbackException")
    void allThreadsReturnUserBalanceWithoutThrowing() throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go    = new CountDownLatch(1);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<UserBalanceRecord>> futures = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return libraryService.findOrCreateUserBalance(SHARED_USER_ID);
            }));
        }

        ready.await();
        go.countDown();
        pool.shutdown();

        List<UserBalanceRecord> successes = new ArrayList<>();
        List<Throwable>         failures  = new ArrayList<>();

        for (Future<UserBalanceRecord> f : futures) {
            try {
                successes.add(f.get());
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }

        // ── All threads must succeed ──────────────────────────────────────────
        assertThat(failures)
                .as("jOOQ has no stateful Session — catch-and-retry works cleanly")
                .isEmpty();

        assertThat(successes).hasSize(THREADS);
        assertThat(successes).allSatisfy(ub ->
                assertThat(ub.getUserId()).isEqualTo(SHARED_USER_ID));
    }
}
