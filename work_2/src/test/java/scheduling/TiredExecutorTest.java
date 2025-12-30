package scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TiredExecutor.
 * Verifies the functionality of task submission, execution, and worker reporting.
 */
class TiredExecutorTest {

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println(context.getDisplayName() + " passed.");
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println(context.getDisplayName() + " failed.");
        }
    };

    @BeforeEach
    void setUp(TestInfo testInfo) {
        System.out.println("Starting " + testInfo.getDisplayName() + "...");
    }

    /**
     * Tests that the executor can be initialized with a valid number of threads
     * and shut down properly.
     */
    @Test
    void testInitialization() throws InterruptedException {
        TiredExecutor executor = new TiredExecutor(2);
        assertNotNull(executor);
        executor.shutdown();
    }

    /**
     * Tests that the executor throws an exception when initialized with
     * an invalid number of threads (0 or negative).
     */
    @Test
    void testInvalidInitialization() {
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(0));
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(-1));
    }

    /**
     * Tests submitting a single task to the executor.
     * Verifies that the task is executed.
     */
    @Test
    void testSubmit() throws InterruptedException {
        TiredExecutor executor = new TiredExecutor(1);
        CountDownLatch latch = new CountDownLatch(1);
        
        executor.submit(() -> {
            latch.countDown();
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Task should complete within 1 second");
        executor.shutdown();
    }

    /**
     * Tests submitting a collection of tasks.
     * Verifies that all tasks in the list are executed.
     */
    @Test
    void testSubmitAll() throws InterruptedException {
        int numTasks = 10;
        TiredExecutor executor = new TiredExecutor(4);
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);

        for (int i = 0; i < numTasks; i++) {
            tasks.add(counter::incrementAndGet);
        }

        executor.submitAll(tasks);

        assertEquals(numTasks, counter.get(), "All tasks should be executed");
        executor.shutdown();
    }

    /**
     * Tests the generation of the worker report.
     * Verifies that the report is not null and contains expected content.
     */
    @Test
    void testGetWorkerReport() throws InterruptedException {
        TiredExecutor executor = new TiredExecutor(2);
        executor.submit(() -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Wait a bit for the task to run and stats to update
        Thread.sleep(100);
        
        String report = executor.getWorkerReport();
        assertNotNull(report);
        System.out.println("This is the report: " + report);
        assertTrue(report.contains("Worker"), "Report should contain worker info");
        
        executor.shutdown();
    }
}