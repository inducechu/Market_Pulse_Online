package academy.backend.market_pulse.dto.portfolio;

import academy.backend.market_pulse.model.Currency;

import java.util.List;
import java.util.Objects;

/**
 * Проверенные параметры пакетной оценки, полученные из CLI.
 */
public record PortfolioEvaluationOptions(
        List<String> portfolioIds, Currency currency, PortfolioEngine engine,
        int quoteLimit, boolean trace) {
    public PortfolioEvaluationOptions {
        portfolioIds = List.copyOf(portfolioIds);
        if (portfolioIds.isEmpty() || portfolioIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Укажите непустые идентификаторы портфелей");
        }
        if (quoteLimit < 1) {
            throw new IllegalArgumentException("--quote-limit должен быть положительным");
        }
        Objects.requireNonNull(currency);
        Objects.requireNonNull(engine);
    }
}
