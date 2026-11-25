package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.exception.MarketDataException;
import academy.backend.market_pulse.model.Currency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** CompletableFuture: позиции открывают две независимые ветви, thenCombineAsync передаёт обе в расчёт. */
final class PortfolioServiceV2 extends PortfolioService {
    private final PortfolioExecutors executors = PortfolioExecutors.platform();
    private final ExecutorService calc = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("portfolio-calc-", 1).factory());

    PortfolioServiceV2(PortfolioMarket market, int quoteLimit, Consumer<PortfolioEvent> trace) {
        super(market, quoteLimit, trace);
    }

    @Override
    CompletableFuture<PortfolioReport> evaluateOne(PortfolioBatch batch, String id, Currency currency) {
        var network = batch.on(executors.network());
        return CompletableFuture.supplyAsync(() -> positions(id, currency), network)
                .thenCompose(request -> {
                    var prices = CompletableFuture.supplyAsync(() -> quotes(request), batch.on(executors.quotes()));
                    var fx = CompletableFuture.supplyAsync(() -> rates(request), network);
                    return prices.thenCombineAsync(fx, (quotes, rates) -> calculate(request, quotes, rates), batch.on(calc))
                            .exceptionally(error -> {
                                // thenCombine сообщает об одной ошибке. После завершения обеих ветвей доступны обе причины.
                                if (prices.state() == Future.State.FAILED && fx.state() == Future.State.FAILED) {
                                    throw new MarketDataException(id, causeOf(prices.exceptionNow()), causeOf(fx.exceptionNow()));
                                }
                                throw new CompletionException(causeOf(error));
                            });
                });
    }

    @Override
    void closeExecutors() {
        executors.close();
        calc.close();
    }
}
