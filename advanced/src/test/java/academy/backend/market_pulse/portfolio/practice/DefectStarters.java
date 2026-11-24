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
 * Пять независимых заготовок. Методы каждой карточки проверяются отдельно.
 */
public final class DefectStarters implements DefectCards {
    private final Executor ioExecutor;
    private final ConcurrentHashMap<String, BigDecimal> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, VersionedQuote> published = new ConcurrentHashMap<>();

    public DefectStarters(Executor ioExecutor) {
        this.ioExecutor = ioExecutor;
    }

    // TODO: Д1. Проверить, что будет с квотой при ошибке загрузки.
    @Override
    public BigDecimal loadWithQuota(Semaphore quota, Supplier<BigDecimal> load) throws InterruptedException {
        quota.acquire();
        BigDecimal price = load.get();
        quota.release();
        return price;
    }

    // TODO: Д2. Проверить, сколько раз стартует загрузка одного тикера.
    @Override
    public CompletableFuture<BigDecimal> loadCachedQuote(String ticker, Supplier<BigDecimal> load) {
        BigDecimal current = cache.get(ticker);
        if (current != null) {
            return CompletableFuture.completedFuture(current);
        }
        return CompletableFuture.supplyAsync(() -> {
            BigDecimal price = load.get();
            cache.put(ticker, price);
            return price;
        }, ioExecutor);
    }

    // TODO: Д3. Найти, кто выполнит задачу во время ожидания get().
    @Override
    public Future<BigDecimal> evaluateOnOneWorker(ExecutorService worker, Supplier<BigDecimal> load) {
        return worker.submit(() -> worker.submit(load::get).get());
    }

    // TODO: Д4. Проверить, какой тип результата получается после handle.
    @Override
    public CompletableFuture<?> recoverReportWithBackup(
            CompletableFuture<PortfolioReport> primary, Supplier<CompletableFuture<PortfolioReport>> backup) {
        return primary.handle((report, error) ->
                error == null ? CompletableFuture.completedFuture(report) : backup.get());
    }

    // TODO: Д5. Проверить порядок публикации котировок с разными версиями.
    @Override
    public void publishQuote(String ticker, VersionedQuote quote) {
        published.put(ticker, quote);
    }

    @Override
    public VersionedQuote publishedQuote(String ticker) {
        return published.get(ticker);
    }
}
