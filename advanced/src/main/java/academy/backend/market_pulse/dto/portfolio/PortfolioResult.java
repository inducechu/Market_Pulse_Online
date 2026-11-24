package academy.backend.market_pulse.dto.portfolio;

/**
 * Отчёт либо ошибка одного портфеля с сохранением его идентификатора.
 */
public record PortfolioResult(String portfolioId, PortfolioReport report, Throwable error) {
    public static PortfolioResult success(PortfolioReport report) {
        return new PortfolioResult(report.portfolioId(), report, null);
    }

    public static PortfolioResult failure(String portfolioId, Throwable error) {
        return new PortfolioResult(portfolioId, null, error);
    }
}
