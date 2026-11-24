package academy.backend.market_pulse.dto.portfolio;

/**
 * Событие оценки с названием stage, именем потока и признаком virtual.
 */
public record PortfolioEvent(String portfolioId, String stage, String thread, boolean virtual) {}
