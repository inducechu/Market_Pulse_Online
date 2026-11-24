package academy.backend.market_pulse.dto.portfolio;

import academy.backend.market_pulse.model.Currency;

import java.math.BigDecimal;
import java.util.List;

/**
 * Неизменяемый отчёт с оценкой каждой позиции и общей стоимостью.
 */
public record PortfolioReport(String portfolioId, Currency currency, List<PortfolioReportLine> positions, BigDecimal total) {
    public PortfolioReport {
        positions = List.copyOf(positions);
    }
}
