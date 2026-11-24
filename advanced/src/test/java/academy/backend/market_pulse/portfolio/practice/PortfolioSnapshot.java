package academy.backend.market_pulse.portfolio.practice;

import academy.backend.market_pulse.dto.portfolio.PortfolioReport;

/**
 * Неизменяемые данные для проверки публикации и порядка обновлений.
 */
public record PortfolioSnapshot(long version, PortfolioReport report) {}
