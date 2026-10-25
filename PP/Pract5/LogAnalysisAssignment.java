import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

/*
 * ПРАКТИЧЕСКОЕ ЗАДАНИЕ: МНОГОПОТОЧНЫЙ АНАЛИЗ ЛОГОВ ВЕБ-СЕРВЕРА
 *
 * Крупные части задания:
 * 1. Сгенерировать набор настоящих файлов с логами.
 * 2. Реализовать разбор строк, последовательный отчёт и параллельный анализ.
 * 3. Распараллелить обработку нескольких файлов через ExecutorService.
 * 4. Сделать общую статистику потокобезопасной.
 * 5. Ограничить число одновременно читаемых файлов через Semaphore.
 * 6. Добавить Atomic-счётчики, CAS и корректное ожидание завершения.
 * 7. Реализовать досрочную остановку анализа через volatile.
 * 8. Сформулировать и доказать инварианты корректности и отсутствия дедлоков.
 * 9. Реализовать обработку повреждённых файлов и частичный отчёт.
 *
 * Повреждённые строки пропускаются, а ошибка чтения одного файла сохраняет уже
 * накопленные данные этого файла и не отменяет обработку остальных. В отчёте
 * указаны повреждённые строки, ошибки чтения и файлы, обработанные полностью.
 *
 * ФАЙЛ МОЖНО ЗАПУСТИТЬ САМОСТОЯТЕЛЬНО:
 *   java LogAnalysisAssignment generate logs 20 1000
 *   java LogAnalysisAssignment analyze logs 4 2 per-path
 *   java LogAnalysisAssignment check
 *
 * Первая команда создаёт каталог logs и файлы access-000.log, access-001.log и т.д.
 * Аргументы: каталог, количество файлов, количество строк в каждом файле.
 * Вторая команда выполняет встроенную проверку генератора и итератора строк.
 *
 * ФОРМАТ СТРОКИ:
 *   METHOD PATH STATUS RESPONSE_BYTES DURATION_MILLIS
 * Например:
 *   GET /api/users 200 820 4
 *
 * Поля разделены одним или несколькими пробелами.
 * Успешный запрос имеет статус от 200 до 399 включительно.
 * Ошибочный запрос имеет статус от 400 и выше.
 *
 * ГОТОВЫЕ СТРУКТУРЫ:
 *   LogEntry       — одна разобранная запись.
 *   LogFile        — путь к одному файлу.
 *   PathStatistics — неизменяемая статистика одного URL.
 *   Report         — итоговый отчёт.
 *   LogFileLines   — итератор строк файла.
 *   GenerateLogs   — генератор входных файлов.
 *
 * ВАЖНО ПРО LogFileLines:
 * Итератор потоконебезопасен. Один экземпляр Iterator<String> нельзя читать
 * из нескольких потоков одновременно. Каждый поток должен получить собственный
 * итератор для своего файла. Нельзя разделить один итератор между задачами.
 * Сам файл можно читать параллельно разными потоками только при условии, что
 * каждый поток создаёт отдельный итератор.
 *
 * ЧАСТИ ЗАДАНИЯ:
 *
 * 1. РАЗБОР СТРОКИ
 * Реализуйте преобразование одной строки в LogEntry. Проверьте количество
 * полей, преобразуйте числовые значения и выбрасывайте понятную ошибку для
 * повреждённой строки.
 *
 * 2. ПОСЛЕДОВАТЕЛЬНЫЙ ОТЧЁТ
 * Напишите обработку всех файлов одним потоком. Для каждого URL собирайте
 * количество запросов, количество успешных запросов и ошибок, объём ответов,
 * суммарное и максимальное время обработки. Этот вариант станет эталоном для
 * проверки многопоточной версии.
 *
 * 3. ПАРАЛЛЕЛЬНАЯ ОБРАБОТКА ФАЙЛОВ
 * Создайте фиксированный ExecutorService. Каждый файл обрабатывается отдельной
 * задачей. Рабочий поток создаёт собственный LogFileLines.Iterator, читает свой
 * файл и передаёт записи в общий аккумулятор. Главный поток должен дождаться
 * завершения всех файлов и только потом построить Report.
 *
 * 4. ПОТОКОБЕЗОПАСНАЯ СТАТИСТИКА
 * Реализуйте аккумулятор статистики. Сначала допустимо использовать один общий
 * synchronized-метод. Затем реализуйте вариант с отдельным synchronized-аккумулятором
 * для каждого URL и ConcurrentHashMap. Сравните эти варианты на данных, где почти
 * все запросы относятся к одному URL, и на данных с большим числом URL.
 *
 * 5. ОГРАНИЧЕНИЕ ЧТЕНИЯ ФАЙЛОВ
 * Добавьте Semaphore, ограничивающий число файлов, которые могут обрабатываться
 * одновременно. Рабочих потоков может быть больше разрешений семафора. Разрешение
 * нужно получить перед чтением файла и гарантированно вернуть после завершения.
 * Добавьте наблюдение за текущим и максимальным числом активных файлов.
 *
 * 6. ATOMIC-СЧЁТЧИКИ И CAS
 * Добавьте счётчики обработанных файлов, прочитанных строк, корректных записей,
 * повреждённых записей, ошибок чтения и активных файлов. Для максимального числа
 * активных файлов используйте AtomicInteger и compareAndSet. Обычные изменяемые
 * int/long для этих глобальных счётчиков не используйте.
 *
 * 7. ОЖИДАНИЕ ЗАВЕРШЕНИЯ
 * Организуйте ожидание всех файлов через CountDownLatch либо через набор Future.
 * Завершение задачи должно учитываться даже при исключении. ExecutorService нужно
 * корректно закрыть и дождаться его завершения.
 *
 * CountDownLatch — это одноразовый счётчик-сигнал. Его создают с начальным
 * числом, например равным количеству файлов. Главный поток вызывает await()
 * и блокируется, пока счётчик не станет равен нулю. Каждая завершившаяся задача
 * один раз вызывает countDown(), уменьшая счётчик. Когда последний счётчик
 * уменьшен до нуля, все ожидающие потоки продолжают работу. Увеличить latch
 * обратно или использовать его повторно нельзя, поэтому для нового запуска
 * создаётся новый объект. Вызов countDown() нужно помещать в finally, чтобы
 * исключение в задаче не оставило главный поток ждать бесконечно.
 *
 * 8. ДОСРОЧНАЯ ОСТАНОВКА
 * Добавьте volatile-флаг остановки. После остановки новые строки не начинают
 * обрабатываться, а уже начатая операция может завершиться. Опишите, какие данные
 * входят в частичный отчёт. Не оставляйте рабочие потоки и разрешения семафора
 * в подвешенном состоянии.
 *
 * 9. ПОВРЕЖДЁННЫЕ ФАЙЛЫ И ЧАСТИЧНЫЙ ОТЧЁТ
 * Сделайте так, чтобы ошибка одного файла не отменяла обработку остальных файлов.
 * В итоговом результате отдельно храните список или число файлов, которые не удалось
 * обработать. Решите, что происходит при повреждённой строке: прекращается обработка
 * всего файла или пропускается только эта строка. Выберите один вариант и явно
 * зафиксируйте его в документации. Для полного запуска корректные файлы должны
 * давать полный отчёт, а ошибка должна быть видна пользователю.
 *
 * ИНВАРИАНТЫ, КОТОРЫЕ НУЖНО ДОКАЗАТЬ:
 * - каждая строка полного запуска обработана ровно один раз;
 * - для каждого URL requests = successful + errors;
 * - maxDuration <= totalDuration при наличии запросов;
 * - 0 <= activeFiles <= semaphoreLimit;
 * - после завершения анализа activeFiles равно нулю;
 * - один повреждённый файл не мешает обработке остальных;
 * - в программе нет дедлока.
 *
 * В доказательстве отсутствия дедлока укажите порядок захвата мониторов и
 * семафоров. Отдельно объясните, может ли поток ждать acquire(), удерживая
 * synchronized-монитор, и почему разрешение семафора возвращается при исключении.
 * Объясните, почему volatile нужен для флага остановки.
 *
 * НЕБОЛЬШИЕ ЭКСПЕРИМЕНТЫ ПОСЛЕ РЕАЛИЗАЦИИ:
 * - сравнить 1 и 4 рабочих потока;
 * - сравнить лимит семафора 1 и 4;
 * - сравнить глобальную блокировку и блокировки по URL;
 * - добавить одну повреждённую строку или один повреждённый файл и проверить,
 *   что остальные данные всё равно попадают в отчёт.
 * Эксперименты не требуют дополнительной архитектуры или новых классов.
 */
public class LogAnalysisAssignment {

    public record LogEntry(
            String method,
            String path,
            int status,
            long responseBytes,
            long durationMillis
    ) {}

    public record LogFile(Path path) {}

    public record PathStatistics(
            long requests,
            long successful,
            long errors,
            long responseBytes,
            long totalDurationMillis,
            long maxDurationMillis
    ) {}

    public record Report(
            long totalRequests,
            long successfulRequests,
            long errorRequests,
            long responseBytes,
            long maxDurationMillis,
            Map<String, PathStatistics> byPath,
            List<Path> failedFiles,
            long processedFiles,
            long linesRead,
            long validRecords,
            long malformedRecords,
            long readErrors,
            int maxActiveFiles,
            boolean stopped
    ) {
        public Report {
            byPath = Map.copyOf(byPath);
            failedFiles = List.copyOf(failedFiles);
        }
    }

    public enum LockingMode {
        GLOBAL,
        PER_PATH
    }

    /** Преобразует строку лога в запись или сообщает, что именно повреждено. */
    public static LogEntry parseLine(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Log line must not be null");
        }
        String trimmed = line.trim();
        String[] fields = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                    "Expected 5 fields, found " + fields.length + ": " + line
            );
        }

        try {
            int status = Integer.parseInt(fields[2]);
            long responseBytes = Long.parseLong(fields[3]);
            long durationMillis = Long.parseLong(fields[4]);
            if (status < 200 || status > 599) {
                throw new IllegalArgumentException(
                        "Status must be between 200 and 599: " + fields[2]
                );
            }
            if (responseBytes < 0 || durationMillis < 0) {
                throw new IllegalArgumentException(
                        "Response bytes and duration must be non-negative"
                );
            }
            return new LogEntry(
                    fields[0], fields[1], status, responseBytes, durationMillis
            );
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid numeric field in log line: " + line,
                    exception
            );
        }
    }

    private static final class MutablePathStatistics {
        private long requests;
        private long successful;
        private long errors;
        private long responseBytes;
        private long totalDurationMillis;
        private long maxDurationMillis;

        private synchronized void add(LogEntry entry) {
            requests++;
            if (entry.status() < 400) {
                successful++;
            } else {
                errors++;
            }
            responseBytes = Math.addExact(responseBytes, entry.responseBytes());
            totalDurationMillis = Math.addExact(
                    totalDurationMillis, entry.durationMillis()
            );
            maxDurationMillis = Math.max(maxDurationMillis, entry.durationMillis());
        }

        private synchronized PathStatistics snapshot() {
            return new PathStatistics(
                    requests,
                    successful,
                    errors,
                    responseBytes,
                    totalDurationMillis,
                    maxDurationMillis
            );
        }
    }

    private static final class StatisticsAccumulator {
        private final LockingMode lockingMode;
        private final Map<String, MutablePathStatistics> paths =
                new ConcurrentHashMap<>();
        private final Object globalLock = new Object();

        private StatisticsAccumulator(LockingMode lockingMode) {
            this.lockingMode = lockingMode;
        }

        private void add(LogEntry entry) {
            if (lockingMode == LockingMode.GLOBAL) {
                synchronized (globalLock) {
                    paths.computeIfAbsent(
                            entry.path(), ignored -> new MutablePathStatistics()
                    ).add(entry);
                }
            } else {
                paths.computeIfAbsent(
                        entry.path(), ignored -> new MutablePathStatistics()
                ).add(entry);
            }
        }

        private Map<String, PathStatistics> snapshot() {
            Map<String, PathStatistics> result = new java.util.TreeMap<>();
            paths.forEach(
                    (path, statistics) -> result.put(path, statistics.snapshot())
            );
            return result;
        }
    }

    private static final class Metrics {
        private final AtomicLong processedFiles = new AtomicLong();
        private final AtomicLong linesRead = new AtomicLong();
        private final AtomicLong validRecords = new AtomicLong();
        private final AtomicLong malformedRecords = new AtomicLong();
        private final AtomicLong readErrors = new AtomicLong();
        private final AtomicInteger activeFiles = new AtomicInteger();
        private final AtomicInteger maxActiveFiles = new AtomicInteger();

        private void enterFile() {
            int active = activeFiles.incrementAndGet();
            int maximum;
            while (active > (maximum = maxActiveFiles.get())
                    && !maxActiveFiles.compareAndSet(maximum, active)) {
                // Retry with the latest observed maximum.
            }
        }

        private void leaveFile() {
            activeFiles.decrementAndGet();
        }
    }

    /**
     * Анализатор можно остановить из другого потока вызовом requestStop().
     * Повреждённые строки пропускаются; ошибка чтения помечает только файл,
     * сохраняя записи, которые успели попасть в статистику.
     *
     * Блокировки берутся в порядке «разрешение семафора, затем монитор статистики»;
     * acquire() никогда не выполняется внутри synchronized-блока. В режиме GLOBAL
     * вложенный порядок всегда «общий монитор, монитор URL»; в режиме PER_PATH
     * захватывается только монитор URL. Поэтому циклического ожидания нет. Каждый
     * permit возвращается в finally. volatile обеспечивает видимость запроса
     * остановки между управляющим и рабочими потоками.
     */
    public static final class Analyzer {
        public volatile boolean stopRequested;

        public void requestStop() {
            stopRequested = true;
        }

        public boolean isStopRequested() {
            return stopRequested;
        }

        public Report analyzeSequential(List<LogFile> files) {
            return analyzeInternal(files, 1, 1, LockingMode.PER_PATH, false);
        }

        public Report analyze(
                List<LogFile> files,
                int workerCount,
                int semaphoreLimit,
                LockingMode lockingMode
        ) {
            return analyzeInternal(
                    files, workerCount, semaphoreLimit, lockingMode, true
            );
        }

        private Report analyzeInternal(
                List<LogFile> files,
                int workerCount,
                int semaphoreLimit,
                LockingMode lockingMode,
                boolean parallel
        ) {
            Objects.requireNonNull(files, "files");
            Objects.requireNonNull(lockingMode, "lockingMode");
            for (LogFile file : files) {
                Objects.requireNonNull(file, "file");
                Objects.requireNonNull(file.path(), "file path");
            }
            files = List.copyOf(files);
            if (workerCount < 1 || semaphoreLimit < 1) {
                throw new IllegalArgumentException(
                        "Worker count and semaphore limit must be positive"
                );
            }

            StatisticsAccumulator statistics = new StatisticsAccumulator(lockingMode);
            Metrics metrics = new Metrics();
            ConcurrentHashMap<Path, String> failures = new ConcurrentHashMap<>();
            Semaphore permits = new Semaphore(semaphoreLimit);

            if (!parallel) {
                for (LogFile file : files) {
                    if (stopRequested) {
                        break;
                    }
                    processFile(file, statistics, metrics, failures, permits);
                }
            } else {
                runParallel(files, workerCount, statistics, metrics, failures, permits);
            }

            Map<String, PathStatistics> byPath = statistics.snapshot();
            long requests = 0;
            long successful = 0;
            long errors = 0;
            long bytes = 0;
            long maxDuration = 0;
            for (PathStatistics pathStatistics : byPath.values()) {
                requests = Math.addExact(requests, pathStatistics.requests());
                successful = Math.addExact(successful, pathStatistics.successful());
                errors = Math.addExact(errors, pathStatistics.errors());
                bytes = Math.addExact(bytes, pathStatistics.responseBytes());
                maxDuration = Math.max(maxDuration, pathStatistics.maxDurationMillis());
            }

            List<Path> failedFiles = new ArrayList<>(failures.keySet());
            failedFiles.sort(Comparator.comparing(Path::toString));
            for (Map.Entry<Path, String> failure : failures.entrySet()) {
                System.err.println(
                        "Failed to analyze " + failure.getKey() + ": "
                                + failure.getValue()
                );
            }
            return new Report(
                    requests,
                    successful,
                    errors,
                    bytes,
                    maxDuration,
                    byPath,
                    failedFiles,
                    metrics.processedFiles.get(),
                    metrics.linesRead.get(),
                    metrics.validRecords.get(),
                    metrics.malformedRecords.get(),
                    metrics.readErrors.get(),
                    metrics.maxActiveFiles.get(),
                    stopRequested
            );
        }

        private void runParallel(
                List<LogFile> files,
                int workerCount,
                StatisticsAccumulator statistics,
                Metrics metrics,
                ConcurrentHashMap<Path, String> failures,
                Semaphore permits
        ) {
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            CountDownLatch completed = new CountDownLatch(files.size());
            try {
                for (LogFile file : files) {
                    executor.execute(() -> {
                        try {
                            if (!stopRequested) {
                                processFile(file, statistics, metrics, failures, permits);
                            }
                        } finally {
                            completed.countDown();
                        }
                    });
                }
                executor.shutdown();
                completed.await();
                if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                    throw new IllegalStateException(
                            "Executor did not terminate within one minute"
                    );
                }
            } catch (InterruptedException exception) {
                stopRequested = true;
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for log analysis", exception
                );
            } catch (RuntimeException exception) {
                executor.shutdownNow();
                throw exception;
            } finally {
                if (!executor.isTerminated()) {
                    executor.shutdownNow();
                }
            }
        }

        private void processFile(
                LogFile file,
                StatisticsAccumulator statistics,
                Metrics metrics,
                ConcurrentHashMap<Path, String> failures,
                Semaphore permits
        ) {
            boolean permitAcquired = false;
            boolean fileReadSuccessfully = false;
            try {
                permits.acquire();
                permitAcquired = true;
                metrics.enterFile();
                try (LogFileLines.LineIterator lines =
                             LogFileLines.iterator(file.path())) {
                    while (!stopRequested && lines.hasNext()) {
                        String line = lines.next();
                        metrics.linesRead.incrementAndGet();
                        try {
                            statistics.add(parseLine(line));
                            metrics.validRecords.incrementAndGet();
                        } catch (IllegalArgumentException exception) {
                            metrics.malformedRecords.incrementAndGet();
                        }
                    }
                    fileReadSuccessfully = !stopRequested;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures.put(file.path(), "interrupted before acquiring a file permit");
                metrics.readErrors.incrementAndGet();
            } catch (RuntimeException exception) {
                failures.put(file.path(), exception.toString());
                metrics.readErrors.incrementAndGet();
            } finally {
                if (permitAcquired) {
                    metrics.leaveFile();
                    permits.release();
                }
                if (fileReadSuccessfully) {
                    metrics.processedFiles.incrementAndGet();
                }
            }
        }
    }

    /**
     * Возвращает отдельный итератор для чтения строк одного файла.
     *
     * ВАЖНО: Iterator<String> потоконебезопасен. Один экземпляр нельзя
     * одновременно читать из нескольких потоков. Для каждого потока нужен
     * отдельный вызов LogFileLines.iterator(path).
     */
    public static final class LogFileLines {
        private LogFileLines() {}

        public static LineIterator iterator(Path path) {
            try {
                BufferedReader reader = Files.newBufferedReader(
                        path,
                        StandardCharsets.UTF_8
                );

                return new LineIterator(path, reader);
            } catch (IOException exception) {
                throw new RuntimeException(
                        "Cannot open " + path,
                        exception
                );
            }
        }

        public static final class LineIterator implements Iterator<String>, AutoCloseable {
            private final Path path;
            private final BufferedReader reader;
            private String nextLine;
            private boolean closed;

            private LineIterator(Path path, BufferedReader reader) {
                this.path = path;
                this.reader = reader;
                nextLine = readNext();
            }

            private String readNext() {
                try {
                    String line = reader.readLine();
                    if (line == null) {
                        close();
                    }
                    return line;
                } catch (IOException exception) {
                    try {
                        close();
                    } catch (RuntimeException closeException) {
                        exception.addSuppressed(closeException);
                    }
                    throw new RuntimeException("Cannot read " + path, exception);
                }
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                try {
                    reader.close();
                } catch (IOException exception) {
                    throw new RuntimeException("Cannot close " + path, exception);
                }
            }

            @Override
            public boolean hasNext() {
                return nextLine != null;
            }

            @Override
            public String next() {
                if (nextLine == null) {
                    throw new NoSuchElementException();
                }
                String result = nextLine;
                nextLine = readNext();
                return result;
            }
        }
    }

    /** Генерирует реальные текстовые файлы с детерминированными данными. */
    public static final class GenerateLogs {
        private static final String[] METHODS = {
                "GET", "POST", "PUT", "DELETE"
        };
        private static final String[] PATHS = {
                "/", "/index.html", "/login", "/api/users",
                "/api/orders", "/api/products", "/search", "/health"
        };
        private static final int[] STATUSES = {
                200, 200, 200, 201, 204,
                301, 400, 401, 404, 500
        };

        private GenerateLogs() {}

        public static void generate(
                Path outputDirectory,
                int fileCount,
                int linesPerFile
        ) throws IOException {
            if (fileCount < 0 || linesPerFile < 0) {
                throw new IllegalArgumentException(
                        "Counts must be non-negative"
                );
            }

            Files.createDirectories(outputDirectory);
            Random random = new Random(42);

            for (int fileId = 0; fileId < fileCount; fileId++) {
                Path file = outputDirectory.resolve(
                        "access-%03d.log".formatted(fileId)
                );

                try (BufferedWriter writer = Files.newBufferedWriter(
                        file,
                        StandardCharsets.UTF_8
                )) {
                    for (int line = 0; line < linesPerFile; line++) {
                        String method =
                                METHODS[random.nextInt(METHODS.length)];
                        String path =
                                PATHS[random.nextInt(PATHS.length)];
                        int status =
                                STATUSES[random.nextInt(STATUSES.length)];
                        long responseBytes =
                                100L + random.nextInt(100_000);
                        long durationMillis =
                                1L + random.nextInt(500);

                        writer.write(
                                "%s %s %d %d %d%n".formatted(
                                        method,
                                        path,
                                        status,
                                        responseBytes,
                                        durationMillis
                                )
                        );
                    }
                }
            }
        }
    }

    /** Проверяет генератор и чтение строк без участия студенческого анализатора. */
    private static void selfCheck() throws IOException {
        Path directory = Files.createTempDirectory("log-assignment-");
        GenerateLogs.generate(directory, 3, 7);

        int files = 0;
        int lines = 0;
        List<LogFile> logFiles = new ArrayList<>();

        try (var paths = Files.list(directory)) {
            for (Path path : paths.toList()) {
                files++;
                logFiles.add(new LogFile(path));
                Iterator<String> iterator = LogFileLines.iterator(path);

                while (iterator.hasNext()) {
                    String line = iterator.next();
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length != 5) {
                        throw new AssertionError(
                                "Unexpected line: " + line
                        );
                    }
                    Integer.parseInt(fields[2]);
                    Long.parseLong(fields[3]);
                    Long.parseLong(fields[4]);
                    lines++;
                }
            }
        }

        if (files != 3 || lines != 21) {
            throw new AssertionError(
                    "Expected 3 files and 21 lines, got "
                            + files + " files and " + lines + " lines"
            );
        }

        Report sequential = new Analyzer().analyzeSequential(logFiles);
        Report parallel = new Analyzer().analyze(
                logFiles, 4, 2, LockingMode.PER_PATH
        );
        Report globalLock = new Analyzer().analyze(
                logFiles, 4, 2, LockingMode.GLOBAL
        );
        if (!sequential.byPath().equals(parallel.byPath())
                || !sequential.byPath().equals(globalLock.byPath())
                || parallel.totalRequests() != lines
                || parallel.maxActiveFiles() > 2
                || !parallel.failedFiles().isEmpty()) {
            throw new AssertionError("Sequential and parallel reports differ");
        }

        System.out.println(
                "OK: generated " + files
                                + " files, read " + lines
                                + " lines, and matched sequential/parallel reports"
        );
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("generate")) {
            Path directory = Path.of(
                    args.length > 1 ? args[1] : "logs"
            );
            int fileCount = args.length > 2
                    ? Integer.parseInt(args[2])
                    : 20;
            int linesPerFile = args.length > 3
                    ? Integer.parseInt(args[3])
                    : 1_000;

            GenerateLogs.generate(
                    directory,
                    fileCount,
                    linesPerFile
            );

            System.out.println(
                    "Generated " + fileCount
                            + " files in " + directory
            );
            return;
        }

        if (args.length > 0 && args[0].equals("analyze")) {
            Path directory = Path.of(args.length > 1 ? args[1] : "logs");
            int workers = args.length > 2 ? Integer.parseInt(args[2]) : 4;
            int semaphoreLimit = args.length > 3
                    ? Integer.parseInt(args[3])
                    : workers;
            LockingMode lockingMode = args.length > 4
                    && args[4].equalsIgnoreCase("global")
                    ? LockingMode.GLOBAL
                    : LockingMode.PER_PATH;
            List<LogFile> files;
            try (var paths = Files.list(directory)) {
                files = paths
                        .filter(path -> path.getFileName().toString().endsWith(".log"))
                        .sorted()
                        .map(LogFile::new)
                        .toList();
            }

            Report report = new Analyzer().analyze(
                    files, workers, semaphoreLimit, lockingMode
            );
            System.out.println("Files discovered: " + files.size());
            System.out.println("Files processed: " + report.processedFiles());
            System.out.println("Failed files: " + report.failedFiles().size());
            System.out.println("Lines read: " + report.linesRead());
            System.out.println("Valid records: " + report.validRecords());
            System.out.println("Malformed records skipped: " + report.malformedRecords());
            System.out.println("Requests: " + report.totalRequests());
            System.out.println("Successful: " + report.successfulRequests());
            System.out.println("Errors: " + report.errorRequests());
            System.out.println("Response bytes: " + report.responseBytes());
            System.out.println("Maximum active files: " + report.maxActiveFiles());
            System.out.println("Stopped early: " + report.stopped());
            report.byPath().forEach((path, stats) ->
                    System.out.println(path + ": " + stats)
            );
            return;
        }

        if (args.length > 0 && args[0].equals("check")) {
            selfCheck();
            return;
        }

        System.out.println(
                "Usage: java LogAnalysisAssignment "
                        + "generate <directory> <files> <lines>"
        );
        System.out.println(
                "       java LogAnalysisAssignment check"
        );
        System.out.println(
                "       java LogAnalysisAssignment analyze <directory> "
                        + "[workers] [semaphore-limit] [global|per-path]"
        );
    }
}
