package academy.backend.market_pulse.service.portfolio;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioRequest;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.model.Currency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/** Оценивает независимые портфели. Возвращает отчёт или причину ошибки для каждого входного идентификатора. */
public abstract class PortfolioService implements AutoCloseable {
    private final PortfolioValuation valuation;
    private final Semaphore quotePermits;
    private final Consumer<PortfolioEvent> trace;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
    private boolean closed;

    PortfolioService(PortfolioMarket market, int quoteLimit, Consumer<PortfolioEvent> trace) {
        this.valuation = new PortfolioValuation(market);
        this.quotePermits = new Semaphore(quoteLimit);
        this.trace = trace;
    }

    public final List<PortfolioResult> evaluatePortfolios(List<String> ids, Currency currency)
            throws InterruptedException {
        // Несколько оценок могут работать одновременно. Закрытие ждёт все стадии начатых пакетов.
        var read = lifecycle.readLock();
        read.lockInterruptibly();
        try {
            if (closed) {
                throw new IllegalStateException("Сервис оценки закрыт");
            }
            return evaluateBatch(ids, currency);
        } finally {
            read.unlock();
        }
    }

    private List<PortfolioResult> evaluateBatch(List<String> ids, Currency currency) throws InterruptedException {
        var portfolioIds = List.copyOf(ids);
        var batch = new PortfolioBatch();
        try {
            var reports = portfolioIds.stream().map(id -> evaluateOne(batch, id, currency)).toList();
            List<PortfolioResult> results = new ArrayList<>(reports.size());
            for (int i = 0; i < reports.size(); i++) {
                try {
                    results.add(PortfolioResult.success(reports.get(i).get()));
                } catch (ExecutionException error) {
                    // NOTICE: здесь при сетевой ошибке можно было бы обратиться к резервному источнику.
                    // Возвращаем причину сбоя этого портфеля и продолжаем собирать остальные отчёты.
                    results.add(PortfolioResult.failure(portfolioIds.get(i), causeOf(error)));
                }
            }
            return List.copyOf(results);
        } catch (InterruptedException | RuntimeException | Error error) {
            batch.cancel();
            throw error;
        }
    }

    abstract Future<PortfolioReport> evaluateOne(PortfolioBatch batch, String id, Currency currency);

    final PortfolioRequest positions(String id, Currency currency) {
        return stage(id, "positions", () -> valuation.loadPositions(id, currency));
    }

    final Map<String, BigDecimal> quotes(PortfolioRequest request) {
        if (request.tickers().isEmpty()) {
            return Map.of();
        }
        return stage(request.portfolioId(), "quotes", () -> {
            quotePermits.acquire();
            try {
                return valuation.loadQuotes(request);
            } finally {
                // Квота общая для всех пакетов сервиса. При ошибке и отмене разрешение тоже возвращается.
                quotePermits.release();
            }
        });
    }

    final Map<Currency, BigDecimal> rates(PortfolioRequest request) {
        return request.foreignCurrencies().isEmpty() ? Map.of()
                : stage(request.portfolioId(), "fx", () -> valuation.loadExchangeRates(request));
    }

    final PortfolioReport calculate(PortfolioRequest request, Map<String, BigDecimal> prices, Map<Currency, BigDecimal> fx) {
        return stage(request.portfolioId(), "calculate", () -> valuation.calculate(request, prices, fx));
    }

    private <T> T stage(String id, String name, Stage<T> action) {
        Thread thread = Thread.currentThread();
        trace.accept(new PortfolioEvent(id, name + ":start", thread.getName(), thread.isVirtual()));
        String outcome = ":error";
        try {
            T result = action.run();
            outcome = ":ok";
            return result;
        } catch (IOException error) {
            String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            throw new UncheckedIOException("Не удалось загрузить " + name + " для " + id + ": " + reason, error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
        } finally {
            trace.accept(new PortfolioEvent(id, name + outcome, thread.getName(), thread.isVirtual()));
        }
    }

    static Throwable causeOf(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    @FunctionalInterface
    private interface Stage<T> {
        T run() throws IOException, InterruptedException;
    }

    @Override
    public final void close() {
        var write = lifecycle.writeLock();
        write.lock();
        try {
            if (!closed) {
                closed = true;
                closeExecutors();
            }
        } finally {
            write.unlock();
        }
    }

    abstract void closeExecutors();
}
