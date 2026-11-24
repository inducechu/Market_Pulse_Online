package academy.backend.market_pulse.dto.portfolio;

/**
 * Механизм оценки: FUTURE - явное ожидание Future, CF - цепочки CompletableFuture,
 * VT - отдельный виртуальный поток на портфель.
 */
public enum PortfolioEngine { FUTURE, CF, VT }
