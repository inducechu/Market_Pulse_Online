package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioReportLine;
import academy.backend.market_pulse.dto.portfolio.PortfolioRequest;
import academy.backend.market_pulse.model.Currency;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Преобразует состав портфеля в параметры запросов, а полученные цены и курсы в отчёт. */
final class PortfolioValuation {
    private final PortfolioMarket market;

    PortfolioValuation(PortfolioMarket market) {
        this.market = market;
    }

    PortfolioRequest loadPositions(String id, Currency currency) throws IOException, InterruptedException {
        var positions = market.loadPositions(id);
        var tickers = positions.stream().map(PortfolioPosition::ticker).collect(Collectors.toSet());
        var foreignCurrencies = positions.stream().map(PortfolioPosition::currency)
                .filter(from -> from != currency).collect(Collectors.toSet());
        return new PortfolioRequest(id, currency, positions, tickers, foreignCurrencies);
    }

    Map<String, BigDecimal> loadQuotes(PortfolioRequest request) throws IOException, InterruptedException {
        return market.loadQuotes(request.tickers());
    }

    Map<Currency, BigDecimal> loadExchangeRates(PortfolioRequest request) throws IOException, InterruptedException {
        return market.loadExchangeRates(request.foreignCurrencies(), request.currency());
    }

    PortfolioReport calculate(PortfolioRequest request, Map<String, BigDecimal> quotes, Map<Currency, BigDecimal> rates) {
        List<PortfolioReportLine> lines = request.positions().stream().map(position -> {
            BigDecimal price = required(quotes, position.ticker(), "цена");
            BigDecimal rate = position.currency() == request.currency() ? BigDecimal.ONE
                    : required(rates, position.currency(), "курс к " + request.currency());
            return new PortfolioReportLine(position, position.quantity().multiply(price).multiply(rate));
        }).toList();
        BigDecimal total = lines.stream().map(PortfolioReportLine::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioReport(request.portfolioId(), request.currency(), lines, total);
    }

    private static <K> BigDecimal required(Map<K, BigDecimal> values, K key, String label) {
        BigDecimal value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Отсутствует " + label + " для " + key);
        }
        return value;
    }
}
