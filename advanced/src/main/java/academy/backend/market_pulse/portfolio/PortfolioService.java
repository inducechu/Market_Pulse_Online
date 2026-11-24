package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.model.Currency;

import java.util.List;

/**
 * Оценивает пакет портфелей, сохраняя порядок входных идентификаторов и отдельные ошибки.
 */
public interface PortfolioService extends AutoCloseable {

    default List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency) throws InterruptedException {
        return evaluatePortfolios(ids, currency, QuoteFailurePolicy.REPORT_ERROR);
    }

    List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency, QuoteFailurePolicy policy)
            throws InterruptedException;

    @Override
    void close();
}
