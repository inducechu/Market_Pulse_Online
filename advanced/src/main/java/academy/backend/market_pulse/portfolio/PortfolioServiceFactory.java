package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

/**
 * Выбирает реализацию одной задачи с Future, CompletableFuture или виртуальными потоками.
 */
public final class PortfolioServiceFactory {
    private PortfolioServiceFactory() {}

    public static PortfolioService create(PortfolioEngine engine, PortfolioValuation valuation, int quoteLimit) {
        return switch (engine) {
            case FUTURE -> new PortfolioServiceV1(valuation);
            case CF -> new PortfolioServiceV2(valuation);
            case VT -> new PortfolioServiceV3(valuation, quoteLimit);
        };
    }
}
