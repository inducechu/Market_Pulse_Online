package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.model.Currency;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static academy.backend.market_pulse.model.Currency.*;

/**
 * Управляемый источник портфелей для контрактных проверок.
 */
class SamplePortfolioMarket implements PortfolioMarket {
    @Override
    public List<PortfolioPosition> loadPositions(String id) throws IOException, InterruptedException {
        return switch (id) {
            case "empty" -> List.of();
            case "bad-price" -> List.of(new PortfolioPosition("UNKNOWN", BigDecimal.ONE, RUB));
            case "bad-fx" -> List.of(new PortfolioPosition("AAPL", BigDecimal.ONE, EUR));
            default -> List.of(new PortfolioPosition("T", new BigDecimal("3"), RUB),
                    new PortfolioPosition("AAPL", new BigDecimal("2"), USD));
        };
    }

    @Override
    public Map<String, BigDecimal> loadQuotes(Set<String> tickers) throws IOException, InterruptedException {
        return Map.of("AAPL", BigDecimal.TEN, "T", new BigDecimal("100"));
    }

    @Override
    public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to)
            throws IOException, InterruptedException {
        return Map.of(USD, new BigDecimal("90"));
    }
}
