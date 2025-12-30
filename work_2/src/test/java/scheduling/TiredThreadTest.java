package scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for TiredThread.
 * Verifies the functionality of individual worker threads, including task execution,
 * fatigue calculation, and shutdown.
 */
class TiredThreadTest {

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
     * Tests initialization of the TiredThread.
     * Verifies that ID and fatigue factor are set correctly.
     */
    @Test
    void testInitialization() {
        int id = 1;
        double fatigueFactor = 1.5;
        TiredThread thread = new TiredThread(id, fatigueFactor);

        assertEquals(id, thread.getWorkerId());
        assertEquals(0, thread.getTimeUsed());
        // Initial fatigue should be 0 as timeUsed is 0
        assertEquals(0.0, thread.getFatigue(), 0.001);
    }

    /**
     * Tests task execution.
     * Verifies that a submitted task is executed by the thread.
     */
    @Test
    void testTaskExecution() throws InterruptedException {
        TiredThread thread = new TiredThread(1, 1.0);
        thread.start();

        CountDownLatch latch = new CountDownLatch(1);
        thread.newTask(latch::countDown);

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Task should be executed within 1 second");
        
        thread.shutdown();
        thread.join();
    }

    /**
     * Tests fatigue calculation.
     * Verifies that fatigue increases after performing work.
     */
    @Test
    void testFatigueCalculation() throws InterruptedException {
        double inputFactor = 2.0;
        double effectiveFactor = 1.5; // Normalized to max 1.5
        TiredThread thread = new TiredThread(1, inputFactor);
        thread.start();

        CountDownLatch latch = new CountDownLatch(1);
        long sleepTime = 50; // ms

        thread.newTask(() -> {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        
        // Wait a bit for the thread to update its stats after task completion
        Thread.sleep(20);

        long timeUsed = thread.getTimeUsed();
        assertTrue(timeUsed > 0, "Time used should be greater than 0");
        
        // Fatigue = timeUsed * effectiveFactor
        System.out.println("Time Used: " + timeUsed);
        System.out.println("Input Factor: " + inputFactor);
        System.out.println("Effective Factor: " + effectiveFactor);
        System.out.println("Fatigue: " + thread.getFatigue());
        assertEquals(timeUsed * effectiveFactor, thread.getFatigue(), 0.001);

        thread.shutdown();
        thread.join();
    }

    @Test
    void testNormalization() {
        TiredThread t1 = new TiredThread(1, 0.1);
        assertEquals("FF=0.50", t1.getName());

        TiredThread t2 = new TiredThread(2, 2.0);
        assertEquals("FF=1.50", t2.getName());

        TiredThread t3 = new TiredThread(3, 1.0);
        assertEquals("FF=1.00", t3.getName());
    }

    /**
     * Tests comparison between two threads.
     * Verifies that threads are compared based on their fatigue.
     */
    @Test
    void testCompareTo() throws InterruptedException {
        TiredThread t1 = new TiredThread(1, 1.0);
        TiredThread t2 = new TiredThread(2, 1.0);
        
        t1.start();
        t2.start();

        // Make t1 work
        CountDownLatch latch1 = new CountDownLatch(1);
        t1.newTask(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch1.countDown();
            }
        });
        
        latch1.await();
        Thread.sleep(20); // Allow stats update

        // t1 has worked, t2 has not. t1 should have higher fatigue.
        // compareTo returns:
        // < 0 if this < o
        // > 0 if this > o
        // t1.fatigue > t2.fatigue (which is 0)
        
        assertTrue(t1.getFatigue() > t2.getFatigue());
        assertTrue(t1.compareTo(t2) > 0, "t1 should be 'greater' (more fatigued) than t2");
        assertTrue(t2.compareTo(t1) < 0, "t2 should be 'less' (less fatigued) than t1");

        t1.shutdown();
        t2.shutdown();
        t1.join();
        t2.join();
    }

    /**
     * Tests that the thread shuts down properly.
     */
    @Test
    void testShutdown() throws InterruptedException {
        TiredThread thread = new TiredThread(1, 1.0);
        thread.start();
        
        assertTrue(thread.isAlive());
        
        thread.shutdown();
        thread.join(1000);
        
        assertFalse(thread.isAlive(), "Thread should be dead after shutdown");
    }
}
