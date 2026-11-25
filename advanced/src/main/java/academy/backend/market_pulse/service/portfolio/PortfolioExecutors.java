package academy.backend.market_pulse.service.portfolio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Исполнители для независимых блокирующих запросов. */
record PortfolioExecutors(ExecutorService network, ExecutorService quotes) implements AutoCloseable {
    static PortfolioExecutors platform() {
        // Работники, ожидающие квоту цен, оставляют свободными потоки для позиций и курсов.
        return new PortfolioExecutors(fixedPool("portfolio-io-"), fixedPool("portfolio-quotes-"));
    }

    static PortfolioExecutors virtual() {
        var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("portfolio-vt-", 1).factory());
        return new PortfolioExecutors(executor, executor);
    }

    private static ExecutorService fixedPool(String prefix) {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform().name(prefix, 1).factory());
    }

    @Override
    public void close() {
        network.close();
        if (quotes != network) {
            quotes.close();
        }
    }
}
