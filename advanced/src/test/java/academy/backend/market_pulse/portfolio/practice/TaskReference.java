package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.portfolio.PortfolioServiceFactory;
import academy.backend.market_pulse.portfolio.PortfolioServiceV2;
import academy.backend.market_pulse.portfolio.QuoteFailurePolicy;
import academy.backend.market_pulse.portfolio.PortfolioValuation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Содержит готовые решения пяти доработок оценки портфеля.
 */
public final class TaskReference implements TaskCards {
    private final AtomicReference<PortfolioSnapshot> current = new AtomicReference<>();

    // З1: позиции → две I/O-ветки → расчёт. См. PortfolioServiceV2.evaluateAsync.
    @Override
    public CompletableFuture<PortfolioReport> evaluate(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc) {
        return PortfolioServiceV2.evaluateAsync(valuation, id, currency, io, calc, QuoteFailurePolicy.REPORT_ERROR);
    }

    // З2: обработка статуса относится только к ветке котировок. См. PortfolioServiceV2.loadQuotesAsync.
    @Override
    public CompletableFuture<PortfolioReport> evaluateWithBackup(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc) {
        return PortfolioServiceV2.evaluateAsync(valuation, id, currency, io, calc, QuoteFailurePolicy.BACKUP_ON_503);
    }

    // З3: тот же контракт, что у portfolio-value с --engine VT. См. PortfolioServiceV3.evaluatePortfolios.
    @Override
    public List<PortfolioResult> evaluateBatch(PortfolioValuation valuation, List<String> ids, Currency currency, int limit)
            throws InterruptedException {
        try (var service = PortfolioServiceFactory.create(PortfolioEngine.VT, valuation, limit)) {
            return service.evaluatePortfolios(ids, currency);
        }
    }

    /**
     * Сначала собирает новый снимок, затем публикует одной атомарной заменой ссылки.
     */
    @Override
    public void publishSnapshot(long version, Supplier<PortfolioReport> load) {
        var next = new PortfolioSnapshot(version, load.get());
        current.set(next);
    }

    @Override
    public PortfolioSnapshot currentSnapshot() {
        return current.get();
    }

    /**
     * Передаёт отчёты и ошибки в порядке завершения задач через completion queue.
     */
    @Override
    public void forEachReady(List<String> ids, Function<String, PortfolioReport> evaluate, Consumer<PortfolioResult> accept)
            throws InterruptedException {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var completed = new ExecutorCompletionService<PortfolioResult>(workers);
            for (String id : ids) {
                completed.submit(() -> {
                    try {
                        return PortfolioResult.success(evaluate.apply(id));
                    } catch (RuntimeException error) {
                        return PortfolioResult.failure(id, error);
                    }
                });
            }

            try {
                for (int i = 0; i < ids.size(); i++) {
                    accept.accept(completed.take().get());
                }
            } catch (ExecutionException error) {
                throw new IllegalStateException(error.getCause());
            } finally {
                workers.shutdownNow();
            }
        }
    }
}
