package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.client.impl.PortfolioHttpClient;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.service.portfolio.PortfolioServiceFactory;

import jdk.management.VirtualThreadSchedulerMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static academy.backend.market_pulse.model.Currency.RUB;

/**
 * Клиентская JVM теста, ограниченная одним carrier thread.
 */
public final class OneCarrierClient {
    public static void main(String[] args) throws Exception {
        var startP2 = new CompletableFuture<Void>();
        Consumer<PortfolioEvent> trace = event -> {
            if (event.portfolioId().equals("P2") && event.stage().equals("positions:start")) {
                startP2.join();
            }
            System.out.println(event);
        };
        try (var market = new PortfolioHttpClient(URI.create(args[0]));
             var service = PortfolioServiceFactory.create(PortfolioEngine.VT, market, 2, trace)) {
            var evaluation = new FutureTask<>(() -> service.evaluatePortfolios(List.of("P1", "P2"), RUB));
            Thread.ofPlatform().start(evaluation);
            try (var stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                if (!"start P2".equals(stdin.readLine())) {
                    throw new IllegalStateException("Сервер не разрешил старт P2");
                }
            } finally {
                startP2.complete(null);
            }
            for (var result : evaluation.get(12, TimeUnit.SECONDS)) {
                if (result.error() != null) {
                    throw new IllegalStateException(result.portfolioId(), result.error());
                }
                System.out.println(result.portfolioId() + "=" + result.report().total());
            }
            var scheduler = ManagementFactory.getPlatformMXBean(VirtualThreadSchedulerMXBean.class);
            System.out.println("parallelism=" + scheduler.getParallelism() + " poolSize=" + scheduler.getPoolSize());
        }
    }
}
