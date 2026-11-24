package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.portfolio.PortfolioValuation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Содержит заготовки пяти доработок; TODO реализуются на практической части семинара.
 */
public final class TaskStarters implements TaskCards {
    // TODO: З1. Запустить загрузку позиций на io, связать стейджи и назначить расчёту calc.
    @Override
    public CompletableFuture<PortfolioReport> evaluate(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc) {
        var request = valuation.loadPositions(id, currency);
        var prices = CompletableFuture.supplyAsync(() -> valuation.loadQuotes(request), io);
        var rates = CompletableFuture.supplyAsync(() -> valuation.loadExchangeRates(request), io);
        return prices.thenCombine(rates, (p, r) -> valuation.calculateValue(request, p, r));
    }

    // TODO: З2. Запросить резервные котировки при HTTP 503 основного источника.
    @Override
    public CompletableFuture<PortfolioReport> evaluateWithBackup(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc) {
        throw new UnsupportedOperationException("З2");
    }

    // TODO: З3. Передать все задачи виртуальным потокам до сбора результатов.
    @Override
    public List<PortfolioResult> evaluateBatch(PortfolioValuation valuation, List<String> ids, Currency currency, int limit)
            throws InterruptedException {
        if (limit < 1) {
            throw new IllegalArgumentException("Квота котировок должна быть положительной");
        }
        List<PortfolioResult> results = new ArrayList<>();
        for (String id : ids) {
            var task = new FutureTask<>(() -> evaluateOne(valuation, id, currency));
            task.run();
            results.add(awaitResult(id, task));
        }
        return List.copyOf(results);
    }

    private PortfolioReport evaluateOne(PortfolioValuation valuation, String id, Currency currency) {
        var request = valuation.loadPositions(id, currency);
        // TODO: З3. Удерживать разрешение общей квоты на время запроса котировок.
        var prices = valuation.loadQuotes(request);
        var rates = valuation.loadExchangeRates(request);
        return valuation.calculateValue(request, prices, rates);
    }

    private PortfolioResult awaitResult(String id, Future<PortfolioReport> task) throws InterruptedException {
        try {
            return PortfolioResult.success(task.get());
        } catch (ExecutionException error) {
            return PortfolioResult.failure(id, error.getCause());
        }
    }

    // TODO: З4. Опубликовать снимок после завершения расчёта.
    @Override
    public void publishSnapshot(long version, Supplier<PortfolioReport> load) {
        throw new UnsupportedOperationException("З4");
    }

    @Override
    public PortfolioSnapshot currentSnapshot() {
        // TODO: З4. Прочитать последний опубликованный снимок, если он есть.
        return null;
    }

    // TODO: З5. Передавать результаты и ошибки в accept по мере готовности.
    @Override
    public void forEachReady(List<String> ids, Function<String, PortfolioReport> evaluate, Consumer<PortfolioResult> accept)
            throws InterruptedException {
        throw new UnsupportedOperationException("З5");
    }
}
