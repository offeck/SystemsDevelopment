package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final Object lock = new Object();
    private Throwable firstError = null;

    private void recordFirstError(Throwable t) {
        if (t == null) {
            return;
        }
        synchronized (lock) {
            if (firstError == null) {
                firstError = t;
            }
        }
    }

    private Throwable consumeFirstError() {
        synchronized (lock) {
            Throwable error = firstError;
            firstError = null;
            return error;
        }
    }

    public TiredExecutor(int numThreads) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        // Nir:
        workers = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            workers[i] = new TiredThread(i, 0.5 + Math.random()); // a random value in the range 0.5–1.5
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
                    recordFirstError(t);
                } finally {
                    if (inFlight.decrementAndGet() == 0) {
                        synchronized (lock) {
                            lock.notifyAll(); // Notify submitAll waiter
                        }
                    }
                    idleMinHeap.add(worker);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Executor interrupted while waiting for worker", e);
        }
    }

    public void submitAll(Iterable<Runnable> tasks) {
        // TODO: submit tasks one by one and wait until all finish
        // Nir: ask for advice in office hours.
        for (Runnable task : tasks) {
            submit(task);
        }
        synchronized (lock) {
            while (inFlight.get() != 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Executor interrupted while waiting for tasks", e);
                }
            }
        }
        Throwable error = consumeFirstError();
        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            } else if (error instanceof Error) {
                throw (Error) error;
            } else {
                throw new RuntimeException(error);
            }
        }
    }

    public void shutdown() {
        // TODO
        try {
            for (TiredThread worker : workers) {
                worker.shutdown();
            }
            for (TiredThread worker : workers) {
                worker.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Executor interrupted while waiting for tasks", e);
        }
    }

    public synchronized String getWorkerReport() {
        // TODO: return readable statistics for each worker
        StringBuilder report = new StringBuilder();
        for (TiredThread worker : workers) {
            report.append(String.format("Worker %d: Time Used = %d ns, Time Idle = %d ns\n",
                    worker.getWorkerId(), worker.getTimeUsed(), worker.getTimeIdle()));
        }
        return report.toString();
    }
}
