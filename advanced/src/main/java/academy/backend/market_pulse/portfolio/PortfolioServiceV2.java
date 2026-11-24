package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Пайплайн CompletableFuture: после позиций параллельно загружаются цены и курсы, затем строится отчёт.
 */
public final class PortfolioServiceV2 implements PortfolioService {
    /**
     * Общие операции загрузки и расчёта.
     */
    private final PortfolioValuation valuation;
    // NOTICE: новые портфели используют те же исполнители для сети и расчёта.
    private final ExecutorService io = PortfolioExecutors.ioExecutor();
    /**
     * Executor для расчёта, отдельный от сетевых запросов.
     */
    private final ExecutorService calc = PortfolioExecutors.calcExecutor();

    PortfolioServiceV2(PortfolioValuation valuation) {
        this.valuation = valuation;
    }

    @Override
    public List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException {
        var pending = ids.stream().map(id -> evaluateAsync(valuation, id, currency, io, calc, policy)).toList();
        return AsyncUtils.collectInOrder(ids, pending);
    }

    /**
     * Строит граф зависимостей: позиции, затем цены и курсы параллельно, затем расчёт на calc.
     */
    public static CompletableFuture<PortfolioReport> evaluateAsync(
            PortfolioValuation valuation, String id, Currency currency, Executor io, Executor calc,
            QuoteFailurePolicy policy) {
        return CompletableFuture.supplyAsync(() -> valuation.loadPositions(id, currency), io)
                .thenCompose(request -> {
                    var quotes = loadQuotesAsync(valuation, request, io, policy);
                    var rates = CompletableFuture.supplyAsync(() -> valuation.loadExchangeRates(request), io);
                    var report = quotes.thenCombineAsync(rates,
                            (prices, fx) -> valuation.calculateValue(request, prices, fx), calc);
                    return report.exceptionally(error -> {
                        throw describeFailure(id, quotes, rates, error);
                    });
                });
    }

    // NOTICE: thenCombine ждёт две ветви. Обе причины сохраняет наша политика диагностики.
    private static RuntimeException describeFailure(String id, Future<?> quotes, Future<?> rates, Throwable error) {
        if (quotes.state() == Future.State.FAILED && rates.state() == Future.State.FAILED) {
            return new MarketDataException(id, AsyncUtils.unwrapAsyncException(quotes.exceptionNow()),
                    AsyncUtils.unwrapAsyncException(rates.exceptionNow()));
        }
        return new CompletionException(AsyncUtils.unwrapAsyncException(error));
    }

    /**
     * При разрешённом HTTP 503 переключает всю цепочку на асинхронный запрос к резерву.
     */
    private static CompletableFuture<Map<String, BigDecimal>> loadQuotesAsync(
            PortfolioValuation valuation, PortfolioRequest request, Executor io, QuoteFailurePolicy policy) {
        var primary = CompletableFuture.supplyAsync(() -> valuation.loadQuotes(request), io);
        return primary.exceptionallyCompose(error -> {
            var cause = AsyncUtils.unwrapAsyncException(error);
            if (policy.canUseBackup(cause)) {
                return CompletableFuture.supplyAsync(() -> valuation.loadBackupAfterFailure(request, cause), io);
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    @Override
    public void close() {
        // Сначала завершаются запросы. Их результаты ещё могут запустить расчёт.
        io.close();
        calc.close();
    }
}
