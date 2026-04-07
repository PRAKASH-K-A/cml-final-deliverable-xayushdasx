import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates latency and throughput metrics with minimal contention.
 * Writes periodic snapshots to CSV so the data can be graphed later.
 */
public final class PerformanceMonitor {

    private static final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private static final AtomicLong totalResponses = new AtomicLong(0);
    private static final AtomicLong maxLatencyNanos = new AtomicLong(0);
    private static final AtomicLong windowResponses = new AtomicLong(0);

    private static final long startTimeNanos = System.nanoTime();
    private static volatile long lastWindowTimeNanos = startTimeNanos;
    private static final Object fileLock = new Object();
    private static final String OUTPUT_FILE = "performance_metrics.csv";
    private static volatile boolean initialized = false;

    private PerformanceMonitor() {}

    public static void recordLatency(long nanos, String category) {
        totalLatencyNanos.addAndGet(nanos);
        updateMaxLatency(nanos);

        long currentCount = totalResponses.incrementAndGet();
        long currentWindowCount = windowResponses.incrementAndGet();

        if (!initialized) {
            initializeCsv();
        }

        if (currentCount % 1000 == 0) {
            long now = System.nanoTime();
            long totalElapsed = Math.max(1, now - startTimeNanos);
            long windowElapsed = Math.max(1, now - lastWindowTimeNanos);

            double avgMicros = (totalLatencyNanos.get() / (double) currentCount) / 1_000.0;
            double peakMicros = maxLatencyNanos.get() / 1_000.0;
            double lifetimeThroughput = currentCount * 1_000_000_000.0 / totalElapsed;
            double windowThroughput = currentWindowCount * 1_000_000_000.0 / windowElapsed;

            String line = String.format(
                    "%s,%d,%s,%.3f,%.3f,%.2f,%.2f%n",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    currentCount,
                    category,
                    avgMicros,
                    peakMicros,
                    lifetimeThroughput,
                    windowThroughput
            );

            synchronized (fileLock) {
                appendLine(line);
            }

            System.out.printf(
                    "[Performance] responses=%d avg=%.3f us max=%.3f us lifetime=%.2f resp/s window=%.2f resp/s%n",
                    currentCount, avgMicros, peakMicros, lifetimeThroughput, windowThroughput
            );

            windowResponses.set(0);
            lastWindowTimeNanos = now;
        }
    }

    public static void printFinalSummary() {
        long responses = totalResponses.get();
        if (responses == 0) {
            System.out.println("[Performance] No responses recorded.");
            return;
        }

        long elapsed = Math.max(1, System.nanoTime() - startTimeNanos);
        double avgMicros = (totalLatencyNanos.get() / (double) responses) / 1_000.0;
        double peakMicros = maxLatencyNanos.get() / 1_000.0;
        double throughput = responses * 1_000_000_000.0 / elapsed;

        System.out.printf(
                "[Performance] FINAL responses=%d avg=%.3f us max=%.3f us throughput=%.2f resp/s csv=%s%n",
                responses, avgMicros, peakMicros, throughput, OUTPUT_FILE
        );
    }

    private static void updateMaxLatency(long nanos) {
        long current;
        do {
            current = maxLatencyNanos.get();
            if (nanos <= current) {
                return;
            }
        } while (!maxLatencyNanos.compareAndSet(current, nanos));
    }

    private static void initializeCsv() {
        synchronized (fileLock) {
            if (initialized) {
                return;
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE, false))) {
                writer.write("timestamp,responses,category,avg_latency_us,max_latency_us,lifetime_throughput_rps,window_throughput_rps");
                writer.newLine();
                initialized = true;
            } catch (IOException e) {
                System.err.println("[Performance] Failed to initialize CSV output: " + e.getMessage());
            }
        }
    }

    private static void appendLine(String line) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE, true))) {
            writer.write(line);
        } catch (IOException e) {
            System.err.println("[Performance] Failed to append CSV row: " + e.getMessage());
        }
    }
}
