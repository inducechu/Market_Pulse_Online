package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;

import java.util.function.Consumer;

/** Тот же алгоритм на Future. У каждого портфеля и каждой дочерней задачи котировок свой виртуальный поток. */
final class PortfolioServiceV3 extends PortfolioServiceV1 {
    PortfolioServiceV3(PortfolioMarket market, int quoteLimit, Consumer<PortfolioEvent> trace) {
        super(market, quoteLimit, trace, PortfolioExecutors.virtual());
    }
}
