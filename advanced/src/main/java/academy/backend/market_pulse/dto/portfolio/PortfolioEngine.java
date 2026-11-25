package academy.backend.market_pulse.dto.portfolio;

/**
 * FUTURE собирает Future на пулах платформенных потоков, CF связывает стадии CompletableFuture,
 * VT выполняет тот же алгоритм на Future с отдельным виртуальным потоком для каждой задачи.
 */
public enum PortfolioEngine { FUTURE, CF, VT }
