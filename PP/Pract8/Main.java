import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class Main {

    static class MySemaphore {
        private final AtomicInteger permits;
        private final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();

        public MySemaphore(int permits) {
            if (permits < 0) {
                throw new IllegalArgumentException("Количество разрешений не может быть меньше 0");
            }
            this.permits = new AtomicInteger(permits);
        }

        public void acquire() {
            Thread current = Thread.currentThread();
            waiters.add(current);
            boolean interrupted = false;

            while (true) {
                int currentPermits = permits.get();
                if (currentPermits > 0
                        && waiters.peek() == current
                        && permits.compareAndSet(currentPermits, currentPermits - 1)) {
                    waiters.poll();

                    if (permits.get() > 0) {
                        Thread next = waiters.peek();
                        if (next != null) {
                            LockSupport.unpark(next);
                        }
                    }

                    if (interrupted) {
                        current.interrupt();
                    }
                    return;
                }

                LockSupport.park(this);
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
        }

        public void release() {
            permits.incrementAndGet();
            Thread next = waiters.peek();
            if (next != null) {
                LockSupport.unpark(next);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MySemaphore semaphore = new MySemaphore(2);
        Thread[] threads = new Thread[5];

        for (int i = 0; i < threads.length; i++) {
            int threadNumber = i + 1;
            threads[i] = new Thread(() -> {
                System.out.println("Поток " + threadNumber + " пытается занять место...");
                semaphore.acquire();
                try {
                    System.out.println("Поток " + threadNumber + " вошёл в критическую секцию");
                    Thread.sleep(500);
                    System.out.println("Поток " + threadNumber + " освобождает место");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    semaphore.release();
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }

        System.out.println("Все потоки завершили работу.");
    }
}
