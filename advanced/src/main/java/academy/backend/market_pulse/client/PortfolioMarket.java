package academy.backend.market_pulse.client;

import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.model.Currency;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Блокирующие, прерываемые запросы к портфельному API. Допускает одновременные вызовы. */
public interface PortfolioMarket extends AutoCloseable {
    List<PortfolioPosition> loadPositions(String portfolioId) throws IOException, InterruptedException;

    Map<String, BigDecimal> loadQuotes(Set<String> tickers) throws IOException, InterruptedException;

    Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to)
            throws IOException, InterruptedException;

    @Override
    default void close() {}
}
