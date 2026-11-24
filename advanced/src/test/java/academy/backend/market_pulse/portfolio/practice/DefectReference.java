package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.dto.portfolio.PortfolioReport;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Показывает исправления пяти конкурентных дефектов с теми же контрактами.
 */
public final class DefectReference implements DefectCards {
    private final Executor ioExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<BigDecimal>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VersionedQuote> published = new ConcurrentHashMap<>();

    public DefectReference(Executor ioExecutor) {
        this.ioExecutor = ioExecutor;
    }

    /**
     * Освобождает permit при любом исходе загрузки после успешного acquire.
     */
    @Override
    public BigDecimal loadWithQuota(Semaphore quota, Supplier<BigDecimal> load) throws InterruptedException {
        quota.acquire();
        try {
            return load.get();
        } finally {
            quota.release();
        }
    }

    /**
     * Атомарно публикует один Future загрузки на тикер; ошибочную загрузку удаляет для следующей попытки.
     */
    @Override
    public CompletableFuture<BigDecimal> loadCachedQuote(String ticker, Supplier<BigDecimal> load) {
        var pending = cache.computeIfAbsent(ticker, key -> CompletableFuture.supplyAsync(load, ioExecutor));
        return pending.whenComplete((price, error) -> {
            if (error != null) {
                cache.remove(ticker, pending);
            }
        });
    }

    /**
     * Передаёт работу единственному worker без вложенного ожидания на том же executor.
     */
    @Override
    public Future<BigDecimal> evaluateOnOneWorker(ExecutorService worker, Supplier<BigDecimal> load) {
        return worker.submit(load::get);
    }

    /**
     * Связывает результат с резервной цепочкой, чтобы дождаться её завершения.
     */
    @Override
    public CompletableFuture<PortfolioReport> recoverReportWithBackup(
            CompletableFuture<PortfolioReport> primary, Supplier<CompletableFuture<PortfolioReport>> backup) {
        return primary.exceptionallyCompose(error -> backup.get());
    }

    /**
     * Атомарно сравнивает версии и сохраняет только более свежую котировку.
     */
    @Override
    public void publishQuote(String ticker, VersionedQuote quote) {
        published.merge(ticker, quote, (current, incoming) ->
                incoming.version() > current.version() ? incoming : current);
    }

    @Override
    public VersionedQuote publishedQuote(String ticker) {
        return published.get(ticker);
    }
}
