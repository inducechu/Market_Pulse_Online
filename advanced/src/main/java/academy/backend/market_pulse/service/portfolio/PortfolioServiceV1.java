package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.exception.MarketDataException;
import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Future: исполнитель обрабатывает портфель, запускает котировки и сам параллельно запрашивает курсы. */
class PortfolioServiceV1 extends PortfolioService {
    private final PortfolioExecutors executors;

    PortfolioServiceV1(PortfolioMarket market, int quoteLimit, Consumer<PortfolioEvent> trace, PortfolioExecutors executors) {
        super(market, quoteLimit, trace);
        this.executors = executors;
    }

    @Override
    Future<PortfolioReport> evaluateOne(PortfolioBatch batch, String id, Currency currency) {
        return batch.submit(executors.network(), () -> {
            var request = positions(id, currency);
            var prices = batch.submit(executors.quotes(), () -> quotes(request));
            Map<Currency, BigDecimal> fx;
            try {
                fx = rates(request);
            } catch (RuntimeException fxError) {
                // Даже если курсы недоступны, у независимого запроса котировок может быть своя ошибка.
                try {
                    prices.get();
                } catch (ExecutionException quoteError) {
                    throw new MarketDataException(id, causeOf(quoteError), causeOf(fxError));
                }
                throw fxError;
            }
            return calculate(request, prices.get(), fx);
        });
    }

    @Override
    void closeExecutors() {
        executors.close();
    }
}
