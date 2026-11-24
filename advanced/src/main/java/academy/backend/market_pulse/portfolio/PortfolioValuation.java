package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioRequest;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.dto.portfolio.PortfolioReportLine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Содержит стадии оценки портфеля, общие для всех способов оркестрации.
 */
public final class PortfolioValuation {

    /**
     * Источник рыночных данных, общий для стадий оценки.
     */
    private final PortfolioMarket market;
    /**
     * Получатель диагностических событий; может вызываться конкурентно из разных задач.
     */
    private final Consumer<PortfolioEvent> trace;

    public PortfolioValuation(PortfolioMarket market, Consumer<PortfolioEvent> trace) {
        this.market = market;
        this.trace = trace;
    }

    /**
     * Загружает позиции и определяет наборы тикеров и иностранных валют для следующих стадий.
     */
    public PortfolioRequest loadPositions(String id, Currency currency) {
        List<PortfolioPosition> positions = stage(id, "positions", () -> market.loadPositions(id));
        Set<String> tickers = positions.stream().map(PortfolioPosition::ticker).collect(Collectors.toSet());
        Set<Currency> foreignCurrencies = positions.stream().map(PortfolioPosition::currency).filter(from -> from != currency)
                .collect(Collectors.toSet());
        return new PortfolioRequest(id, currency, positions, tickers, foreignCurrencies);
    }

    public Map<String, BigDecimal> loadQuotes(PortfolioRequest request) {
        return request.tickers().isEmpty() ? Map.of()
                : stage(request.portfolioId(), "quotes", () -> market.loadQuotes(request.tickers()));
    }

    public Map<String, BigDecimal> loadQuotes(PortfolioRequest request, QuoteFailurePolicy policy) {
        try {
            return loadQuotes(request);
        } catch (RuntimeException error) {
            if (policy.canUseBackup(error)) {
                return loadBackupAfterFailure(request, error);
            }
            throw error;
        }
    }

    Map<String, BigDecimal> loadBackupAfterFailure(PortfolioRequest request, Throwable primaryError) {
        try {
            return loadBackupQuotes(request);
        } catch (RuntimeException backupError) {
            if (backupError != primaryError) {
                backupError.addSuppressed(primaryError);
            }
            throw backupError;
        }
    }

    public Map<String, BigDecimal> loadBackupQuotes(PortfolioRequest request) {
        return request.tickers().isEmpty() ? Map.of()
                : stage(request.portfolioId(), "backup-quotes", () -> market.loadBackupQuotes(request.tickers()));
    }

    public Map<Currency, BigDecimal> loadExchangeRates(PortfolioRequest request) {
        return request.foreignCurrencies().isEmpty() ? Map.of()
                : stage(request.portfolioId(), "fx",
                        () -> market.loadExchangeRates(request.foreignCurrencies(), request.currency()));
    }

    /**
     * Считает quantity × price × rate для каждой позиции и суммирует стоимость в валюте отчёта.
     */
    public PortfolioReport calculateValue(
            PortfolioRequest request, Map<String, BigDecimal> quotes, Map<Currency, BigDecimal> rates) {
        return stage(request.portfolioId(), "calculate", () -> {
            List<PortfolioReportLine> lines = request.positions().stream().map(position -> {
                BigDecimal price = required(quotes, position.ticker(), "цена");
                BigDecimal rate = position.currency() == request.currency() ? BigDecimal.ONE
                        : required(rates, position.currency(), "курс к " + request.currency());
                return new PortfolioReportLine(position, position.quantity().multiply(price).multiply(rate));
            }).toList();
            BigDecimal total = lines.stream().map(PortfolioReportLine::value).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PortfolioReport(request.portfolioId(), request.currency(), lines, total);
        });
    }

    /**
     * Помечает начало и исход стадии именем текущего потока для разбора исполнения цепочки.
     */
    private <T> T stage(String id, String name, Supplier<T> action) {
        Thread thread = Thread.currentThread();
        trace.accept(new PortfolioEvent(id, name + ":start", thread.getName(), thread.isVirtual()));
        try {
            T result = action.get();
            trace.accept(new PortfolioEvent(id, name + ":ok", thread.getName(), thread.isVirtual()));
            return result;
        } catch (RuntimeException error) {
            trace.accept(new PortfolioEvent(id, name + ":error", thread.getName(), thread.isVirtual()));
            throw error;
        }
    }

    private static <K> BigDecimal required(Map<K, BigDecimal> values, K key, String label) {
        BigDecimal value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Отсутствует " + label + " для " + key);
        }
        return value;
    }
}
