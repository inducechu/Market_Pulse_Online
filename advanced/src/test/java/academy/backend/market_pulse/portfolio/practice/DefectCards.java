package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.dto.portfolio.PortfolioReport;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Определяет контракты пяти сценариев с конкурентными ошибками.
 */
public interface DefectCards {

    BigDecimal loadWithQuota(Semaphore quota, Supplier<BigDecimal> load) throws InterruptedException;

    CompletableFuture<BigDecimal> loadCachedQuote(String ticker, Supplier<BigDecimal> load);

    Future<BigDecimal> evaluateOnOneWorker(ExecutorService worker, Supplier<BigDecimal> load);

    CompletableFuture<?> recoverReportWithBackup(
            CompletableFuture<PortfolioReport> primary, Supplier<CompletableFuture<PortfolioReport>> backup);

    void publishQuote(String ticker, VersionedQuote quote);

    VersionedQuote publishedQuote(String ticker);
}
