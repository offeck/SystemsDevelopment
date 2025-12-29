package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TiredThread extends Thread implements Comparable<TiredThread> {

    private static final Runnable POISON_PILL = () -> {}; // Special task to signal shutdown

    private final int id; // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation

    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a task

    private final AtomicLong timeUsed = new AtomicLong(0); // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0); // Total time spent waiting for tasks
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker last became idle

    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());
        setName(String.format("FF=%.2f", fatigueFactor));
    }

    public int getWorkerId() {
        return id;
    }

    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getTimeUsed() {
        return timeUsed.get();
    }

    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is non-blocking: if the worker is not ready to accept a task,
     * it throws IllegalStateException.
     */
    public void newTask(Runnable task) {
        if (!handoff.offer(task)) {
            throw new IllegalStateException("Worker is not ready to accept a new task");
        }
    }

    /**
     * Request this worker to stop after finishing current task.
     * Inserts a poison pill so the worker wakes up and exits.
     */
    public void shutdown() throws InterruptedException {
        alive.set(false);
        handoff.put(POISON_PILL);
    }

    @Override
    public void run() {
        while (alive.get()) {
            try {
                // 1. Wait for a task (blocks until available)
                Runnable task = handoff.take();
                
                // 2. Update idle time (we just stopped being idle)
                long currentTime = System.nanoTime();
                timeIdle.addAndGet(currentTime - idleStartTime.get());

                // 3. Check if it's the poison pill (shutdown signal)
                if (task == POISON_PILL) {
                    break;  // Exit the loop
                }

                // 4. Mark as busy
                busy.set(true);
                
                // 5. Execute the task and track time
                long startTime = System.nanoTime();
                task.run();
                long endTime = System.nanoTime();
                
                // 6. Update time used
                timeUsed.addAndGet(endTime - startTime);
                
                // 7. Mark as idle again
                busy.set(false);
                idleStartTime.set(System.nanoTime());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public int compareTo(TiredThread o) {
        return Double.compare(this.getFatigue(), o.getFatigue());
    }
}