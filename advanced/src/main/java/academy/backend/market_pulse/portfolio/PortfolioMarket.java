package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Задаёт блокирующие операции получения позиций, котировок и валютных курсов.
 */
public interface PortfolioMarket {

    List<PortfolioPosition> loadPositions(String portfolioId);

    Map<String, BigDecimal> loadQuotes(Set<String> tickers);

    Map<String, BigDecimal> loadBackupQuotes(Set<String> tickers);

    Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to);
}
