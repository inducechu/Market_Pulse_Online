package academy.backend.market_pulse.dto.portfolio;

import academy.backend.market_pulse.model.Currency;

import java.util.List;
import java.util.Set;

/**
 * Неизменяемые позиции и параметры запросов, полученные после загрузки состава портфеля.
 */
public record PortfolioRequest(
        String portfolioId, Currency currency, List<PortfolioPosition> positions, Set<String> tickers,
        Set<Currency> foreignCurrencies) {
    public PortfolioRequest {
        positions = List.copyOf(positions);
        tickers = Set.copyOf(tickers);
        foreignCurrencies = Set.copyOf(foreignCurrencies);
    }
}
