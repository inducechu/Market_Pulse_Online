package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Один виртуальный поток оценивает один портфель обычными последовательными вызовами.
 */
final class PortfolioServiceV3 implements PortfolioService {
    /**
     * Общие операции загрузки и расчёта.
     */
    private final PortfolioValuation valuation;
    /**
     * Общая для всех портфелей сервиса квота запросов котировок.
     */
    private final Semaphore permits;
    /**
     * Создаёт отдельный виртуальный поток для каждой поданной оценки.
     */
    private final ExecutorService executor = PortfolioExecutors.virtualExecutor();

    PortfolioServiceV3(PortfolioValuation valuation, int quoteLimit) {
        if (quoteLimit < 1) {
            throw new IllegalArgumentException("Квота котировок должна быть положительной");
        }
        this.valuation = valuation;
        this.permits = new Semaphore(quoteLimit);
    }

    /**
     * Запускает по виртуальному потоку на портфель; Semaphore ограничивает только запрос котировок.
     */
    @Override
    public List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException {
        List<Future<PortfolioReport>> pending = new ArrayList<>();
        for (String id : ids) {
            pending.add(executor.submit(() -> evaluateOne(id, currency, policy)));
        }
        return AsyncUtils.collectInOrder(ids, pending);
    }

    /**
     * Выполняет последовательные стадии в одном виртуальном потоке, удерживая permit только на котировки.
     */
    private PortfolioReport evaluateOne(String id, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException {
        var request = valuation.loadPositions(id, currency);
        Map<String, BigDecimal> prices;
        // NOTICE: acquire вне try: при прерывании ожидания permit не получен и release не нужен.
        permits.acquire();
        try {
            prices = valuation.loadQuotes(request, policy);
        } finally {
            // NOTICE: permit возвращается и после ошибки HTTP, иначе общая квота будет потеряна.
            permits.release();
        }
        var rates = valuation.loadExchangeRates(request);
        return valuation.calculateValue(request, prices, rates);
    }

    @Override
    public void close() {
        executor.close();
    }
}
