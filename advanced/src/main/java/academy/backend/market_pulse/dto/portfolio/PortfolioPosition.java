package academy.backend.market_pulse.dto.portfolio;

import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;

/**
 * Позиция портфеля с количеством бумаг и валютой цены.
 */
public record PortfolioPosition(String ticker, BigDecimal quantity, Currency currency) {}
