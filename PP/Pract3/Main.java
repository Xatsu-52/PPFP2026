import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class Main {

    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;
    public static final int MAX_ITER = 1000;

    public static final double X_MIN = -2.0;
    public static final double X_MAX = 1.0;
    public static final double Y_MIN = -1.2;
    public static final double Y_MAX = 1.2;

    public static final int THREADS = Runtime.getRuntime().availableProcessors();

    public static int calculatePixel(int x, int y, int width, int height) {
        double cx = X_MIN + (double) x / width * (X_MAX - X_MIN);
        double cy = Y_MIN + (double) y / height * (Y_MAX - Y_MIN);

        double zx = 0.0;
        double zy = 0.0;
        int iter = 0;

        while (zx * zx + zy * zy <= 4.0 && iter < MAX_ITER) {
            double nextZx = zx * zx - zy * zy + cx;
            double nextZy = 2.0 * zx * zy + cy;
            zx = nextZx;
            zy = nextZy;
            iter++;
        }

        return iter;
    }

    public static int[][] calculateSequential() {
        int[][] result = new int[HEIGHT][WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                result[y][x] = calculatePixel(x, y, WIDTH, HEIGHT);
            }
        }
        return result;
    }

    public static int[][] calculateParallel() throws InterruptedException {
        int[][] result = new int[HEIGHT][WIDTH];
        Thread[] threads = new Thread[THREADS];

        int rowsPerThread = HEIGHT / THREADS;

        for (int i = 0; i < THREADS; i++) {
            final int threadId = i;
            final int startRow = threadId * rowsPerThread;
            final int endRow = (threadId == THREADS - 1) ? HEIGHT : startRow + rowsPerThread;

            threads[i] = new Thread(() -> {
                for (int y = startRow; y < endRow; y++) {
                    for (int x = 0; x < WIDTH; x++) {
                        result[y][x] = calculatePixel(x, y, WIDTH, HEIGHT);
                    }
                }
            });
        }

        for (int i = 0; i < THREADS; i++) {
            threads[i].start();
        }

        for (int i = 0; i < THREADS; i++) {
            threads[i].join();
        }

        return result;
    }

    public static void saveImage(int[][] grid, String filename) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int iter = grid[y][x];
                int color;
                if (iter == MAX_ITER) {
                    color = Color.BLACK.getRGB();
                } else {
                    float hue = 0.7f + (float) iter / 100.0f;
                    float saturation = 0.8f;
                    float brightness = iter > 0 ? 1.0f : 0.0f;
                    color = Color.HSBtoRGB(hue, saturation, brightness);
                }
                image.setRGB(x, y, color);
            }
        }

        try {
            File outputFile = new File(filename);
            ImageIO.write(image, "png", outputFile);
            System.out.println("\u0418зображение сохранено в файл: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении изображения: " + e.getMessage());
        }
    }

    public static int countPointsInSet(int[][] grid) {
        int count = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (grid[y][x] == MAX_ITER) {
                    count++;
                }
            }
        }
        return count;
    }

    public static void main(String[] args) throws InterruptedException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        System.out.println("Расчёт множества Мандельброта");
        System.out.printf("Размер сетки: %dx%d (всего пикселей: %d)%n", WIDTH, HEIGHT, WIDTH * HEIGHT);
        System.out.printf("Макс. итераций на пиксель: %d%n", MAX_ITER);
        System.out.printf("\u0418спользуется потоков: %d%n", THREADS);

        long startSeq = System.nanoTime();
        int[][] seqResult = calculateSequential();
        long finishSeq = System.nanoTime();
        double seqTimeMs = (finishSeq - startSeq) / 1_000_000.0;

        long startPar = System.nanoTime();
        int[][] parResult = calculateParallel();
        long finishPar = System.nanoTime();
        double parTimeMs = (finishPar - startPar) / 1_000_000.0;

        int inSetCount = countPointsInSet(parResult);

        System.out.println("\n Результаты вычислений ");
        System.out.println("Точек входит в множество: " + inSetCount + " из " + (WIDTH * HEIGHT));
        System.out.printf("Время последовательного расчёта: %.2f ms%n", seqTimeMs);
        System.out.printf("Время параллельного расчёта:     %.2f ms%n", parTimeMs);

        saveImage(parResult, "mandelbrot.png");
    }
}