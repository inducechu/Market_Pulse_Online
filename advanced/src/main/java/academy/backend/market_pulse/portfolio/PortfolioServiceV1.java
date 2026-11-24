package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Координация на Future: вызывающий поток запускает запросы, собирает данные и разбирает отказы.
 */
final class PortfolioServiceV1 implements PortfolioService {
    /**
     * Общие операции загрузки и расчёта.
     */
    private final PortfolioValuation valuation;
    // NOTICE: все оценки этого сервиса используют один пул. Его закрывает владелец сервиса.
    private final ExecutorService io = PortfolioExecutors.ioExecutor();

    PortfolioServiceV1(PortfolioValuation valuation) {
        this.valuation = valuation;
    }

    /**
     * Оценивает портфели по очереди; внутри одного портфеля запросы цен и курсов идут параллельно.
     */
    @Override
    public List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException {
        List<PortfolioResult> results = new ArrayList<>();
        for (String id : ids) {
            try {
                var report = evaluateOne(id, currency, policy);
                results.add(PortfolioResult.success(report));
            } catch (ExecutionException | RuntimeException error) {
                results.add(PortfolioResult.failure(id, AsyncUtils.unwrapAsyncException(error)));
            }
        }
        return List.copyOf(results);
    }

    /**
     * После позиций запускает две независимые загрузки и сохраняет обе причины при двойном отказе.
     */
    private PortfolioReport evaluateOne(String id, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException, ExecutionException {
        var request = io.submit(() -> valuation.loadPositions(id, currency)).get();
        Future<Map<String, BigDecimal>> quotes = io.submit(() -> valuation.loadQuotes(request, policy));
        Future<Map<Currency, BigDecimal>> rates = io.submit(() -> valuation.loadExchangeRates(request));

        Map<String, BigDecimal> prices;
        try {
            prices = quotes.get();
        } catch (ExecutionException quoteError) {
            // NOTICE: ошибка первого Future ничего не говорит о состоянии второго запроса.
            try {
                rates.get();
            } catch (ExecutionException rateError) {
                throw new MarketDataException(id, quoteError.getCause(), rateError.getCause());
            }
            throw quoteError;
        }
        return valuation.calculateValue(request, prices, rates.get());
    }

    @Override
    public void close() {
        io.close();
    }
}
