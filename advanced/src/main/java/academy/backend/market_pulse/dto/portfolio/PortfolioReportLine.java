package academy.backend.market_pulse.dto.portfolio;

import java.math.BigDecimal;

/**
 * Одна позиция и её стоимость в валюте отчёта.
 */
public record PortfolioReportLine(PortfolioPosition position, BigDecimal value) {}
