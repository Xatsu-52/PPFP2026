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

    static class MyBarrier {
        private static class Phase {
            final AtomicInteger count = new AtomicInteger(0);
            final ConcurrentLinkedQueue<Thread> waiters = new ConcurrentLinkedQueue<>();
            volatile boolean opened = false;
        }

        private final int parties;
        private volatile Phase currentPhase = new Phase();

        public MyBarrier(int parties) {
            if (parties <= 0) {
                throw new IllegalArgumentException("Число потоков должно быть больше 0");
            }
            this.parties = parties;
        }

        public void await() {
            Thread current = Thread.currentThread();
            boolean interrupted = false;

            while (true) {
                Phase phase = currentPhase;
                int arrived = phase.count.incrementAndGet();

                if (arrived > parties) {
                    Thread.onSpinWait();
                    continue;
                }

                // Последний (n-й) поток открывает барьер и будит все предыдущие n - 1 потоков
                if (arrived == parties) {
                    currentPhase = new Phase();
                    phase.opened = true;
                    Thread waiter;
                    while ((waiter = phase.waiters.poll()) != null) {
                        LockSupport.unpark(waiter);
                    }
                    if (interrupted) {
                        current.interrupt();
                    }
                    return;
                }

                // Первые n - 1 потоков ждут открытия барьера
                phase.waiters.add(current);
                while (!phase.opened) {
                    LockSupport.park(this);
                    if (Thread.interrupted()) {
                        interrupted = true;
                    }
                }

                if (interrupted) {
                    current.interrupt();
                }
                return;
            }
        }
    }

    public static void runSemaphoreExample() throws InterruptedException {
        System.out.println("=== Пример семафора (ДЗ 7) ===");
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

    public static void runBarrierExample() throws InterruptedException {
        System.out.println("\n=== Пример барьера (ДЗ 8) ===");
        int n = 4;
        MyBarrier barrier = new MyBarrier(n);
        Thread[] threads = new Thread[n];

        for (int i = 0; i < n; i++) {
            int threadNumber = i + 1;
            threads[i] = new Thread(() -> {
                try {
                    Thread.sleep(threadNumber * 200L);
                    System.out.println("Поток " + threadNumber + " дошёл до барьера");
                    barrier.await();
                    System.out.println("Поток " + threadNumber + " прошёл барьер!");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < n; i++) {
            threads[i].join();
        }

        System.out.println("Все " + n + " потоков прошли барьер.");
    }

    public static void main(String[] args) throws InterruptedException {
        runSemaphoreExample();
        runBarrierExample();
    }
}
