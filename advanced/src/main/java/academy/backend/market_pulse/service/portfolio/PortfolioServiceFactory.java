package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;

import java.util.function.Consumer;

/** Выбирает способ исполнения портфельного запроса. Клиентом market владеет вызывающий код. */
public final class PortfolioServiceFactory {
    private PortfolioServiceFactory() {}

    public static PortfolioService create(PortfolioEngine engine, PortfolioMarket market, int quoteLimit,
                                          Consumer<PortfolioEvent> trace) {
        if (quoteLimit < 1) {
            throw new IllegalArgumentException("Квота котировок должна быть положительной");
        }
        return switch (engine) {
            case FUTURE -> new PortfolioServiceV1(market, quoteLimit, trace, PortfolioExecutors.platform());
            case CF -> new PortfolioServiceV2(market, quoteLimit, trace);
            case VT -> new PortfolioServiceV3(market, quoteLimit, trace);
        };
    }
}
