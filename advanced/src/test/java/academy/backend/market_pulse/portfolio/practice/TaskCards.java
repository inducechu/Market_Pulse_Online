package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.portfolio.PortfolioValuation;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Определяет контракты пяти доработок оценки портфеля для заготовок и готовых решений.
 */
public interface TaskCards {

    CompletableFuture<PortfolioReport> evaluate(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc);

    CompletableFuture<PortfolioReport> evaluateWithBackup(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc);

    List<PortfolioResult> evaluateBatch(PortfolioValuation valuation, List<String> ids, Currency currency, int limit)
            throws InterruptedException;

    void publishSnapshot(long version, Supplier<PortfolioReport> load);

    PortfolioSnapshot currentSnapshot();

    void forEachReady(List<String> ids, Function<String, PortfolioReport> evaluate, Consumer<PortfolioResult> accept)
            throws InterruptedException;
}
