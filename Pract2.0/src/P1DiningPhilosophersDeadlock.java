import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Симуляция Deadlock в задаче "Обедающие философы".
 *
 * В отличие от правильного решения (где вилки захватываются в порядке возрастания id),
 * здесь каждый философ бере сначала СВОЮ ЛЕВУЮ вилку, а затем ПРАВУЮ вилку.
 * Это приводит к классическому deadlock (циклическому ожиданию): каждый философ
 * держит левую вилку и ждет правую, которая занята соседним философом.
 */
public class P1DiningPhilosophersDeadlock {

    private static final class Fork {
        private final int id;
        private final Lock lock = new ReentrantLock();

        private Fork(int id) {
            this.id = id;
        }
    }

    private static final class Table {
        private final Fork[] forks;
        private final AtomicIntegerArray forkUsers;
        private final AtomicBoolean conflictDetected = new AtomicBoolean();

        private Table(int philosopherCount) {
            forks = new Fork[philosopherCount];
            for (int id = 0; id < philosopherCount; id++) {
                forks[id] = new Fork(id);
            }
            forkUsers = new AtomicIntegerArray(philosopherCount);
        }

        void eat(int philosopherId) throws InterruptedException {
            Fork left = forks[philosopherId];
            Fork right = forks[(philosopherId + 1) % forks.length];

            // ОШИБКА ДИЗАЙНА (приводящая к Deadlock):
            // Всегда берем сначала левую вилку, потом правую, без упорядочивания по id.
            left.lock.lock();
            try {
                // Искусственная задержка для синхронизации захвата левых вилок всеми философами
                Thread.sleep(50);

                right.lock.lock();
                try {
                    eatWithBothForks(left, right);
                } finally {
                    right.lock.unlock();
                }
            } finally {
                left.lock.unlock();
            }
        }

        private void eatWithBothForks(Fork left, Fork right) {
            int leftUsers = forkUsers.incrementAndGet(left.id);
            int rightUsers = forkUsers.incrementAndGet(right.id);
            if (leftUsers != 1 || rightUsers != 1) {
                conflictDetected.set(true);
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                forkUsers.decrementAndGet(right.id);
                forkUsers.decrementAndGet(left.id);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int philosopherCount = 5;
        Table table = new Table(philosopherCount);
        ExecutorService pool = Executors.newFixedThreadPool(philosopherCount);
        List<Future<?>> results = new ArrayList<>();

        System.out.println("Запуск симуляции Обедающих философов с deadlock...");
        System.out.println("Каждый философ пытается взять левую вилку, а затем правую.");

        try {
            for (int philosopher = 0; philosopher < philosopherCount; philosopher++) {
                int philosopherId = philosopher;
                results.add(pool.submit(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            table.eat(philosopherId);
                            Thread.yield();
                        }
                    } catch (InterruptedException e) {
                        // Поток прерван при завершении симуляции
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            // Ждем завершения с таймаутом (ожидаем deadlock)
            for (int philosopher = 0; philosopher < philosopherCount; philosopher++) {
                // Ждем максимум 2 секунды — философы гарантированно заблокируются навсегда
                results.get(philosopher).get(2, TimeUnit.SECONDS);
            }

            System.out.println("Удивительно: deadlock не возник!");
        } catch (TimeoutException e) {
            System.out.println("\n[DEADLOCK ОБНАРУЖЕН!]");
            System.out.println("Философы заблокировали друг друга: каждый держит левую вилку и ждет правую.");
            System.out.println("Программа не может продолжить выполнение (таймаут истек).");
        } finally {
            pool.shutdownNow();
            // Даем потокам завершиться
            pool.awaitTermination(1, TimeUnit.SECONDS);
            System.out.println("Симуляция завершена.");
        }
    }
}
