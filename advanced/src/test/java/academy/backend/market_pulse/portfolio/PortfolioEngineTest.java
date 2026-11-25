package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.dto.portfolio.PortfolioReportLine;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.service.portfolio.PortfolioServiceFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static academy.backend.market_pulse.model.Currency.RUB;
import static academy.backend.market_pulse.model.Currency.USD;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Общий контракт трёх способов оценки, включая конкурентные вызовы одного сервиса. */
class PortfolioEngineTest {
    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void closeLetsAnAcceptedPortfolioFinishItsRemainingStages(PortfolioEngine engine) throws Exception {
        var positionsStarted = new CountDownLatch(1);
        var releasePositions = new CountDownLatch(1);
        var closingStarted = new CountDownLatch(1);
        var market = new SamplePortfolioMarket() {
            @Override
            public List<PortfolioPosition> loadPositions(String id) throws IOException, InterruptedException {
                positionsStarted.countDown();
                releasePositions.await();
                return super.loadPositions(id);
            }
        };
        try (var service = PortfolioServiceFactory.create(engine, market, 1, event -> {});
             var callers = Executors.newFixedThreadPool(2)) {
            var pending = callers.submit(() -> service.evaluatePortfolios(List.of("P1"), RUB));
            try {
                assertThat(positionsStarted.await(3, SECONDS)).isTrue();
                var close = callers.submit(() -> {
                    closingStarted.countDown();
                    service.close();
                });
                assertThat(closingStarted.await(3, SECONDS)).isTrue();
                assertThatThrownBy(() -> close.get(100, MILLISECONDS)).isInstanceOf(TimeoutException.class);
                releasePositions.countDown();
                var accepted = pending.get(3, SECONDS).getFirst();
                assertThat(accepted.error()).isNull();
                assertThat(accepted.report().total()).isEqualByComparingTo("2100");
                close.get(3, SECONDS);
                assertThatThrownBy(() -> service.evaluatePortfolios(List.of("P2"), RUB))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("закрыт");
            } finally {
                releasePositions.countDown();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void sameTotalsOrderAndExplicitErrors(PortfolioEngine engine) throws InterruptedException {
        var events = new ConcurrentLinkedQueue<PortfolioEvent>();
        var ids = List.of("P2", "bad-price", "empty", "bad-fx", "P1", "P2");
        List<PortfolioResult> results;
        try (var service = PortfolioServiceFactory.create(engine, new SamplePortfolioMarket(), 1, events::add)) {
            results = service.evaluatePortfolios(ids, RUB);
        }

        assertThat(results).extracting(PortfolioResult::portfolioId).containsExactlyElementsOf(ids);
        for (int index : List.of(0, 4, 5)) {
            assertThat(results.get(index).report().total()).isEqualByComparingTo("2100");
            assertThat(results.get(index).report().positions()).extracting(PortfolioReportLine::value)
                    .containsExactly(new BigDecimal("300"), new BigDecimal("1800"));
        }
        assertThat(results.get(1).error()).hasMessageContaining("цена").hasMessageContaining("UNKNOWN");
        assertThat(results.get(2).report().total()).isEqualByComparingTo("0");
        assertThat(results.get(3).error()).hasMessageContaining("курс").hasMessageContaining("EUR");
        assertThat(events).filteredOn(event -> event.portfolioId().equals("empty")).extracting(PortfolioEvent::stage)
                .containsExactly("positions:start", "positions:ok", "calculate:start", "calculate:ok");

        var p1 = events.stream().filter(event -> event.portfolioId().equals("P1")).toList();
        assertThat(p1).allMatch(event -> event.virtual() == (engine == PortfolioEngine.VT));
        assertThat(p1).extracting(PortfolioEvent::stage)
                .containsSubsequence("positions:ok", "quotes:start", "quotes:ok", "calculate:start", "calculate:ok")
                .containsSubsequence("positions:ok", "fx:start", "fx:ok", "calculate:start", "calculate:ok");
        if (engine == PortfolioEngine.CF) {
            assertThat(p1.getLast().thread()).startsWith("portfolio-calc-");
        }
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void sharesQuoteLimitAcrossBatchesWhileBothCanReadExchangeRates(PortfolioEngine engine) throws Exception {
        var atQuoteStage = new CountDownLatch(2);
        var ratesReady = new CountDownLatch(2);
        var firstQuote = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var market = new SamplePortfolioMarket() {
            @Override
            public Map<String, BigDecimal> loadQuotes(Set<String> tickers) throws IOException, InterruptedException {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                firstQuote.countDown();
                try {
                    release.await();
                    return super.loadQuotes(tickers);
                } finally {
                    active.decrementAndGet();
                }
            }
        };

        try (var service = PortfolioServiceFactory.create(engine, market, 1, event -> {
            if (event.stage().equals("quotes:start")) {
                atQuoteStage.countDown();
            } else if (event.stage().equals("fx:ok")) {
                ratesReady.countDown();
            }
        }); var callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> service.evaluatePortfolios(List.of("P1"), RUB));
            var second = callers.submit(() -> service.evaluatePortfolios(List.of("P2"), RUB));
            try {
                assertThat(firstQuote.await(3, SECONDS)).isTrue();
                assertThat(atQuoteStage.await(3, SECONDS)).isTrue();
                assertThat(ratesReady.await(3, SECONDS)).as("Квота котировок оставляет доступ к FX").isTrue();
            } finally {
                release.countDown();
            }
            assertThat(first.get(3, SECONDS).getFirst().report().total()).isEqualByComparingTo("2100");
            assertThat(second.get(3, SECONDS).getFirst().report().total()).isEqualByComparingTo("2100");
            assertThat(maximum).hasValue(1);
        }
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void interruptCancelsBothRequestsAndLetsAnotherBatchUseThePermit(PortfolioEngine engine) throws Exception {
        var arrived = new CountDownLatch(2);
        var stopped = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var nextQuote = new CountDownLatch(1);
        var market = new SamplePortfolioMarket() {
            @Override
            public List<PortfolioPosition> loadPositions(String id) {
                return List.of(new PortfolioPosition(id, BigDecimal.ONE, id.equals("slow") ? USD : RUB));
            }

            @Override
            public Map<String, BigDecimal> loadQuotes(Set<String> tickers) throws InterruptedException {
                if (tickers.contains("slow")) {
                    holdRequest();
                }
                return Map.of("healthy", BigDecimal.TEN);
            }

            @Override
            public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to) throws InterruptedException {
                holdRequest();
                return Map.of();
            }

            private void holdRequest() throws InterruptedException {
                arrived.countDown();
                try {
                    release.await();
                } finally {
                    stopped.countDown();
                }
            }
        };
        try (var service = PortfolioServiceFactory.create(engine, market, 1, event -> {
            if (event.portfolioId().equals("healthy") && event.stage().equals("quotes:start")) {
                nextQuote.countDown();
            }
        }); var otherCaller = Executors.newSingleThreadExecutor()) {
            var interrupted = new CompletableFuture<Boolean>();
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    service.evaluatePortfolios(List.of("slow"), RUB);
                    interrupted.complete(false);
                } catch (InterruptedException expected) {
                    interrupted.complete(true);
                }
            });
            try {
                assertThat(arrived.await(3, SECONDS)).isTrue();
                var healthy = otherCaller.submit(() -> service.evaluatePortfolios(List.of("healthy"), RUB));
                assertThat(nextQuote.await(3, SECONDS)).isTrue();
                caller.interrupt();
                assertThat(interrupted.get(3, SECONDS)).isTrue();
                assertThat(stopped.await(3, SECONDS)).as("Обе сетевые операции прерваны").isTrue();
                assertThat(healthy.get(3, SECONDS).getFirst().report().total()).isEqualByComparingTo("10");
            } finally {
                release.countDown();
                caller.interrupt();
                caller.join();
            }
        }
    }
}
