package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        // Nir:
        workers = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            workers[i] = new TiredThread(i, 1.0); // Assuming a default fatigue factor of 1.0
            workers[i].start();
            idleMinHeap.add(workers[i]);
        }
    }

    public void submit(Runnable task) {
        
    try {
        TiredThread worker = idleMinHeap.take();
        inFlight.incrementAndGet();
        
        worker.newTask(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                // CRITICAL: Catch user errors so the worker thread doesn't die!
                System.err.println("Task failed: " + t.getMessage());
            } finally {
                inFlight.decrementAndGet();
                idleMinHeap.add(worker); 
            }
        });
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Executor interrupted", e);
    }
}

    }

    public void submitAll(Iterable<Runnable> tasks) {
        // TODO: submit tasks one by one and wait until all finish
        // Nir: ask for advice in office hours.
        for (Runnable task : tasks) {
            submit(task);
        }
    }

    public void shutdown() throws InterruptedException {
        // TODO
        for (TiredThread worker : workers) {
            worker.shutdown();
        }
        for (TiredThread worker : workers) {
            worker.join();
        }
    }

    public synchronized String getWorkerReport() {
        // TODO: return readable statistics for each worker
        String report = "";
        for (TiredThread worker : workers) {
            report += String.format("Worker %d: Time Used = %d ns, Time Idle = %d ns\n",
                    worker.getWorkerId(), worker.getTimeUsed(), worker.getTimeIdle());
        }
        return report;
    }
}
