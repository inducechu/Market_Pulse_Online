package academy.backend.market_pulse.portfolio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Создаёт общие для сервиса executor для сети, расчётов и виртуальных потоков.
 */
final class PortfolioExecutors {
    private PortfolioExecutors() {}

    static ExecutorService ioExecutor() {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform().name("portfolio-io-", 1).factory());
    }

    static ExecutorService calcExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("portfolio-calc-", 1).factory());
    }

    static ExecutorService virtualExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("portfolio-vt-", 1).factory());
    }
}
