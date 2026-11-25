package academy.backend.market_pulse.demo.concurrency;

import com.sun.net.httpserver.HttpServer;
import jdk.management.VirtualThreadSchedulerMXBean;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Удерживает два HTTP-ответа, показывая конкурентное ожидание сети при одном carrier thread.
 */
public final class OneCarrierNetworkDemo {
    private OneCarrierNetworkDemo() {}

    public static void main(String[] args) throws Exception {
        var scheduler = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
        if (scheduler.getParallelism() != 1
                || !"1".equals(System.getProperty("jdk.virtualThreadScheduler.maxPoolSize"))) {
            throw new IllegalStateException("Запустите с -Djdk.virtualThreadScheduler.parallelism=1 "
                    + "и -Djdk.virtualThreadScheduler.maxPoolSize=1");
        }

        var releaseResponses = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var handlers = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("demo-http-", 1).factory())) {
            server.setExecutor(handlers);
            server.start();
            try {
                URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
                System.out.printf("PID=%d, server=%s%n", ProcessHandle.current().pid(), base);
                var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

                waitForEnter(console, "Подключите профайлер. Нажмите Enter, чтобы отправить запрос P1");
                var p1 = startRequest(server, base, "P1", releaseResponses);

                waitForEnter(console, "Ответ P1 удерживается. Нажмите Enter, чтобы отправить запрос P2");
                var p2 = startRequest(server, base, "P2", releaseResponses);

                // NOTICE: теперь в консоли уже есть «server received P1» и «server received P2».
                // На следующем приглашении пока не нажимайте Enter. Во втором терминале снимите
                // jcmd <PID> Thread.dump_to_file <файл> и найдите стеки demo-vt-P1 и demo-vt-P2.
                System.out.printf("parallelism=%d poolSize=%d%n", scheduler.getParallelism(), scheduler.getPoolSize());
                waitForEnter(console, "Оба ответа удерживаются. Нажмите Enter, чтобы разрешить ответы");
                releaseResponses.countDown();
                System.out.printf("P1 HTTP %d, P2 HTTP %d%n", p1.get(), p2.get());
                waitForEnter(console, "Сохраните запись профайлера. Нажмите Enter для выхода");
            } finally {
                releaseResponses.countDown();
                server.stop(0);
            }
        }
    }

    private static FutureTask<Integer> startRequest(HttpServer server, URI base, String id, CountDownLatch release)
            throws InterruptedException {
        var arrived = new CountDownLatch(1);
        String path = "/" + id;
        server.createContext(path, exchange -> {
            try (exchange) {
                System.out.println("server received " + id);
                arrived.countDown();
                release.await();
                System.out.println("server releases " + id);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        var request = new FutureTask<>(() -> readResponse(base.resolve(path), id));
        Thread.ofVirtual().name("demo-vt-" + id).start(request);
        if (!arrived.await(5, TimeUnit.MINUTES)) {
            throw new IllegalStateException("Сервер не получил запрос " + id);
        }
        return request;
    }

    private static int readResponse(URI uri, String id) throws IOException {
        var connection = (HttpURLConnection) uri.toURL().openConnection(Proxy.NO_PROXY);
        connection.setConnectTimeout(2_000);
        try {
            System.out.println(id + " before read " + Thread.currentThread());
            // NOTICE: поставьте брейкпоинт на getResponseCode с режимом Suspend: Thread.
            // В Variables посмотрите id, в Evaluate Expression - Thread.currentThread().threadId()
            // и isVirtual() у этого потока. Нажмите Resume, чтобы запрос дошёл до сервера.
            int status = connection.getResponseCode();
            // NOTICE: вторая точка - строка after read. После разрешения ответов сравните threadId()
            // с первым остановом для того же id. В Variables уже есть результат HTTP.
            System.out.println(id + " after read  " + Thread.currentThread());
            return status;
        } finally {
            connection.disconnect();
        }
    }

    private static void waitForEnter(BufferedReader console, String prompt) throws IOException {
        System.out.println(prompt);
        if (console.readLine() == null) {
            throw new EOFException("Запустите демо в консоли с доступным stdin");
        }
    }
}
