package academy.backend.market_pulse.demo.concurrency;

import com.sun.net.httpserver.HttpServer;
import jdk.management.VirtualThreadSchedulerMXBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.IntToLongFunction;

/**
 * Сравнивает один пайплайн CompletableFuture на двух исполнителях. Для показа в IDE
 * укажите VM option {@code -XX:ActiveProcessorCount=4} и сравните задачи в секунду внутри каждого сценария.
 */
public final class VirtualThreadThroughputDemo {
    private static final int TASKS = 48;
    private static final int RESPONSE_DELAY_MS = 60;
    private static final int CPU_RANGE_SIZE = 80_000;

    private VirtualThreadThroughputDemo() {}

    public static void main(String[] args) throws IOException {
        int parallelism = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class).getParallelism();
        System.out.printf("По %d задач. Потоков в фиксированном пуле и целевой параллелизм VT: %d.%n",
                TASKS, parallelism);

        try (var platform = Executors.newFixedThreadPool(parallelism);
             var virtual = Executors.newVirtualThreadPerTaskExecutor();
             var handlers = Executors.newFixedThreadPool(TASKS, Thread.ofPlatform().name("demo-server-", 1).factory())) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), TASKS);
            server.setExecutor(handlers);
            server.createContext("/quote", exchange -> {
                try (exchange) {
                    Thread.sleep(RESPONSE_DELAY_MS);
                    // Возвращаем цену, зависящую от номера запроса. Каждый исполнитель получит те же данные.
                    int id = Integer.parseInt(exchange.getRequestURI().getQuery());
                    byte[] result = Integer.toString(100 + id).getBytes(StandardCharsets.US_ASCII);
                    exchange.sendResponseHeaders(200, result.length);
                    exchange.getResponseBody().write(result);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            server.start();
            try {
                URI quotes = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/quote");
                compare("CPU: расчёт", platform, virtual, VirtualThreadThroughputDemo::calculate);
                compare("Сеть: HTTP с задержкой " + RESPONSE_DELAY_MS + " мс", platform, virtual,
                        id -> readQuote(quotes, id));
            } finally {
                server.stop(0);
            }
        }
    }

    private static void compare(String workload, Executor platform, Executor virtual, IntToLongFunction task) {
        System.out.println("\n" + workload);
        runBatch(platform, task, 8);
        runBatch(virtual, task, 8);
        System.out.println("  Раунд 1");
        long expected = measure("Фиксированный пул", platform, task);
        long vtFirst = measure("Виртуальные потоки", virtual, task);
        System.out.println("  Раунд 2, обратный порядок");
        long vtSecond = measure("Виртуальные потоки", virtual, task);
        long fixedSecond = measure("Фиксированный пул", platform, task);
        if (expected != vtFirst || expected != vtSecond || expected != fixedSecond) {
            throw new IllegalStateException("Суммы для одинаковых задач разошлись");
        }
    }

    private static long measure(String label, Executor executor, IntToLongFunction task) {
        long start = System.nanoTime();
        long total = runBatch(executor, task, TASKS);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        System.out.printf("  %-21s  %6.0f мс  %7.1f задач/с  сумма=%d%n", label, seconds * 1000, TASKS / seconds, total);
        return total;
    }

    private static long runBatch(Executor executor, IntToLongFunction task, int size) {
        var reports = new ArrayList<CompletableFuture<Long>>(size);
        for (int id = 0; id < size; id++) {
            int requestId = id;
            // NOTICE: меняется только executor. Сначала отправляем все задачи, затем собираем результаты.
            reports.add(CompletableFuture.supplyAsync(() -> task.applyAsLong(requestId), executor)
                    .thenApply(value -> value * 2));
        }
        return reports.stream().mapToLong(CompletableFuture::join).sum();
    }

    /** Считаем простые числа в своём диапазоне. Здесь нет ни сети, ни ожидания других задач. */
    private static long calculate(int id) {
        int begin = 1_000_000 + id * CPU_RANGE_SIZE;
        long primes = 0;
        for (int n = begin + 1; n < begin + CPU_RANGE_SIZE; n += 2) {
            int divisor = 3;
            while (divisor * divisor <= n && n % divisor != 0) {
                divisor += 2;
            }
            if (divisor * divisor > n) {
                primes++;
            }
        }
        return primes;
    }

    private static long readQuote(URI base, int id) {
        try {
            var connection = (HttpURLConnection) URI.create(base + "?" + id).toURL().openConnection(Proxy.NO_PROXY);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            try (var body = connection.getInputStream()) {
                return Long.parseLong(new String(body.readAllBytes(), StandardCharsets.US_ASCII));
            } finally {
                connection.disconnect();
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
