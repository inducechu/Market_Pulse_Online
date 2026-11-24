package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static academy.backend.market_pulse.model.Currency.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Управляемый источник портфелей для контрактных проверок.
 */
public class SamplePortfolioMarket implements PortfolioMarket {
    @Override
    public List<PortfolioPosition> loadPositions(String id) {
        return switch (id) {
            case "empty" -> List.of();
            case "bad-price" -> List.of(new PortfolioPosition("UNKNOWN", BigDecimal.ONE, RUB));
            case "bad-fx" -> List.of(new PortfolioPosition("AAPL", BigDecimal.ONE, EUR));
            default -> List.of(new PortfolioPosition("T", new BigDecimal("3"), RUB),
                    new PortfolioPosition("AAPL", new BigDecimal("2"), USD));
        };
    }

    @Override
    public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
        return Map.of("AAPL", BigDecimal.TEN, "T", new BigDecimal("100"));
    }

    @Override
    public Map<String, BigDecimal> loadBackupQuotes(Set<String> tickers) {
        return Map.of("AAPL", new BigDecimal("12"), "T", new BigDecimal("100"));
    }

    @Override
    public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to) {
        return Map.of(USD, new BigDecimal("90"));
    }
}
