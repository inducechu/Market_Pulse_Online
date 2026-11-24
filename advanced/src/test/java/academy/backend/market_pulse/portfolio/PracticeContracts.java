package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.portfolio.practice.PortfolioSnapshot;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.portfolio.SamplePortfolioMarket;
import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.dto.portfolio.PortfolioReportLine;
import academy.backend.market_pulse.portfolio.practice.DefectCards;
import academy.backend.market_pulse.portfolio.practice.VersionedQuote;
import academy.backend.market_pulse.portfolio.practice.TaskCards;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static academy.backend.market_pulse.model.Currency.RUB;

import static academy.backend.market_pulse.model.Currency.USD;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static java.math.BigDecimal.ONE;

import static java.math.BigDecimal.TEN;

import static java.util.concurrent.TimeUnit.SECONDS;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Проверяет контракты карточек практики на управляемых конкурентных сценариях.
 */
abstract class PracticeContracts {
    abstract DefectCards defects(Executor io);

    abstract TaskCards tasks();

    @Test
    void d1() throws Exception {
        var quota = new Semaphore(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        var firstIn = new CountDownLatch(1);
        var letFirstFail = new CountDownLatch(1);
        var secondAttempt = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var cards = defects(workers);
            var first = workers.submit(() -> cards.loadWithQuota(quota, () -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                firstIn.countDown();
                try {
                    await(letFirstFail);
                    throw new PortfolioHttpStatusException(503, "/v1/quotes");
                } finally {
                    active.decrementAndGet();
                }
            }));
            try {
                await(firstIn);
                assertThat(quota.availablePermits()).as("Активный запрос занимает квоту").isZero();
                var second = workers.submit(() -> {
                    secondAttempt.countDown();
                    return cards.loadWithQuota(quota, () -> {
                        maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                        active.decrementAndGet();
                        return TEN;
                    });
                });
                try {
                    await(secondAttempt);
                    letFirstFail.countDown();
                    assertThat(assertThrows(ExecutionException.class, () -> first.get(2, SECONDS)).getCause())
                            .hasMessageContaining("HTTP 503");
                    assertThat(second.get(1, SECONDS)).as("После 503 квота снова доступна").isEqualTo(TEN);
                    assertThat(maximum).hasValue(1);
                    assertThat(quota.availablePermits()).isEqualTo(1);
                } finally {
                    second.cancel(true);
                }
            } finally {
                letFirstFail.countDown();
            }
        }
    }

    @Test
    void d2() throws Exception {
        var firstIn = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        try (var io = Executors.newFixedThreadPool(2)) {
            var cards = defects(io);
            var first = cards.loadCachedQuote("T", () -> {
                requests.incrementAndGet();
                firstIn.countDown();
                await(release);
                return TEN;
            });
            try {
                await(firstIn);
                var second = cards.loadCachedQuote("T", () -> {
                    requests.incrementAndGet();
                    return TEN;
                });
                release.countDown();
                assertThat(first.get(2, SECONDS)).isEqualTo(TEN);
                assertThat(second.get(2, SECONDS)).isEqualTo(TEN);
                assertThat(requests).as("Обращения к поставщику за котировкой T").hasValue(1);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void d3() throws Exception {
        var worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("single-worker").factory());
        try {
            var pending = defects(worker).evaluateOnOneWorker(worker, () -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("single-worker");
                return TEN;
            });
            assertThat(pending.get(1, SECONDS)).isEqualTo(TEN);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(2, SECONDS)).isTrue();
        }
    }

    @Test
    void d4() throws Exception {
        var primary = new CompletableFuture<PortfolioReport>();
        var heldBackup = new CompletableFuture<PortfolioReport>();
        var backupCalls = new AtomicInteger();
        var pending = defects(Runnable::run).recoverReportWithBackup(primary, () -> {
            backupCalls.incrementAndGet();
            return heldBackup;
        });
        primary.completeExceptionally(new PortfolioHttpStatusException(503, "/v1/quotes"));
        assertThat(backupCalls).hasValue(1);
        assertThat(pending.isDone()).as("Резервный ответ ещё удерживается").isFalse();
        var report = report("P1", "2460");
        heldBackup.complete(report);
        assertThat(pending.get(2, SECONDS)).isEqualTo(report);

        var failed = defects(Runnable::run).recoverReportWithBackup(primary,
                () -> CompletableFuture.failedFuture(new PortfolioHttpStatusException(502, "/v1/backup/quotes")));
        assertThat(assertThrows(ExecutionException.class, () -> failed.get(2, SECONDS)).getCause())
                .hasMessageContaining("HTTP 502");
    }

    @Test
    void d5() {
        var cards = defects(Runnable::run);
        var fresh = new VersionedQuote(2, TEN);
        cards.publishQuote("T", new VersionedQuote(3, ONE));
        cards.publishQuote("AAPL", fresh);
        cards.publishQuote("AAPL", new VersionedQuote(1, ONE));
        assertThat(cards.publishedQuote("AAPL")).isEqualTo(fresh);
        assertThat(cards.publishedQuote("T")).isEqualTo(new VersionedQuote(3, ONE));
    }

    @Test
    void z1() throws Exception {
        for (String first : List.of("quotes", "fx")) {
            var started = new CountDownLatch(2);
            var quotes = new CountDownLatch(1);
            var fx = new CountDownLatch(1);
            var firstDone = new CountDownLatch(1);
            var events = new ConcurrentLinkedQueue<PortfolioEvent>();
            var market = new SamplePortfolioMarket() {
                @Override
                public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
                    assertThat(tickers).containsExactlyInAnyOrder("AAPL", "T");
                    started.countDown();
                    await(quotes);
                    return super.loadQuotes(tickers);
                }

                @Override
                public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to) {
                    assertThat(from).containsExactly(USD);
                    assertThat(to).isEqualTo(RUB);
                    started.countDown();
                    await(fx);
                    return super.loadExchangeRates(from, to);
                }
            };
            var valuation = new PortfolioValuation(market, event -> {
                events.add(event);
                if (event.stage().equals(first + ":ok")) {
                    firstDone.countDown();
                }
            });
            try (var io = PortfolioExecutors.ioExecutor(); var calc = PortfolioExecutors.calcExecutor();
                 var caller = Executors.newSingleThreadExecutor()) {
                try {
                    var evaluation = caller.submit(() -> tasks().evaluate(valuation, "P1", RUB, io, calc))
                            .get(2, SECONDS);
                    await(started);
                    assertThat(events).filteredOn(event -> event.stage().equals("positions:ok"))
                            .extracting(PortfolioEvent::thread)
                            .as("Позиции загружаются на I/O исполнителе")
                            .singleElement().asString().startsWith("portfolio-io-");
                    assertThat(evaluation.isDone()).as("Оба ответа ещё удерживаются").isFalse();
                    (first.equals("quotes") ? quotes : fx).countDown();
                    await(firstDone);
                    assertThat(evaluation.isDone()).as("Второй ответ ещё удерживается").isFalse();
                    (first.equals("quotes") ? fx : quotes).countDown();

                    assertThat(evaluation.get(2, SECONDS).total()).isEqualByComparingTo("2100");
                    assertThat(events).extracting(PortfolioEvent::stage).containsSubsequence(
                            "positions:ok", first + ":ok", (first.equals("quotes") ? "fx" : "quotes") + ":ok",
                            "calculate:ok");
                    assertThat(events).allMatch(e -> e.stage().startsWith("calculate")
                            ? e.thread().startsWith("portfolio-calc-") : e.thread().startsWith("portfolio-io-"));
                } finally {
                    quotes.countDown();
                    fx.countDown();
                }
            }
        }
    }

    @Test
    void z2() throws Exception {
        try (var io = PortfolioExecutors.ioExecutor(); var calc = PortfolioExecutors.calcExecutor()) {
            for (int primary : List.of(200, 404, 503)) {
                for (boolean backupFails : primary == 503 ? List.of(false, true) : List.of(false)) {
                    var backupCalls = new AtomicInteger();
                    var market = new SamplePortfolioMarket() {
                        @Override
                        public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
                            if (primary != 200) {
                                throw new PortfolioHttpStatusException(primary, "/v1/quotes");
                            }
                            return super.loadQuotes(tickers);
                        }

                        @Override
                        public Map<String, BigDecimal> loadBackupQuotes(Set<String> tickers) {
                            backupCalls.incrementAndGet();
                            if (backupFails) {
                                throw new PortfolioHttpStatusException(502, "/v1/backup/quotes");
                            }
                            return super.loadBackupQuotes(tickers);
                        }
                    };
                    var valuation = new PortfolioValuation(market, __ -> {});
                    var result = tasks().evaluateWithBackup(valuation, "P1", RUB, io, calc);
                    if (primary == 404 || backupFails) {
                        var error = assertThrows(ExecutionException.class, () -> result.get(2, SECONDS)).getCause();
                        assertThat(error).hasMessageContaining(backupFails ? "HTTP 502" : "HTTP 404");
                    } else {
                        assertThat(result.get(2, SECONDS).total()).isEqualByComparingTo(primary == 200 ? "2100" : "2460");
                    }
                    assertThat(backupCalls).hasValue(primary == 503 ? 1 : 0);
                }
            }
        }
    }

    @Test
    void z3() throws Exception {
        for (int limit : List.of(1, 2)) {
            var ids = List.of("P3", "P1", "P2");
            var firstPositionOnVirtualThread = new CompletableFuture<Boolean>();
            var positioned = new CountDownLatch(ids.size());
            var withinQuota = new CountDownLatch(limit);
            var overQuota = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var active = new AtomicInteger();
            var maximum = new AtomicInteger();
            var market = new SamplePortfolioMarket() {
                @Override
                public List<PortfolioPosition> loadPositions(String id) {
                    boolean virtual = Thread.currentThread().isVirtual();
                    firstPositionOnVirtualThread.complete(virtual);
                    assertThat(virtual).as("Позиции %s загружаются на виртуальном потоке", id).isTrue();
                    positioned.countDown();
                    return super.loadPositions(id);
                }

                @Override
                public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
                    int current = active.incrementAndGet();
                    maximum.accumulateAndGet(current, Math::max);
                    if (current > limit) {
                        overQuota.countDown();
                    }
                    withinQuota.countDown();
                    try {
                        await(release);
                        return super.loadQuotes(tickers);
                    } finally {
                        active.decrementAndGet();
                    }
                }
            };
            try (var caller = Executors.newSingleThreadExecutor()) {
                var valuation = new PortfolioValuation(market, __ -> {});
                var pending = caller.submit(() -> tasks().evaluateBatch(valuation, ids, RUB, limit));
                try {
                    assertThat(firstPositionOnVirtualThread.get(2, SECONDS))
                            .as("Загрузка позиций выполняется на виртуальном потоке").isTrue();
                    await(positioned);
                    await(withinQuota);
                    assertThat(overQuota.await(200, TimeUnit.MILLISECONDS)).isFalse();
                    release.countDown();
                    var results = pending.get(2, SECONDS);
                    assertThat(results).extracting(PortfolioResult::portfolioId).containsExactlyElementsOf(ids);
                    assertThat(results).allSatisfy(outcome ->
                            assertThat(outcome.report().total()).isEqualByComparingTo("2100"));
                    assertThat(maximum).hasValue(limit);
                } finally {
                    release.countDown();
                }
            }
        }
        verifyZ3OrderWhenSecondFinishesFirst();
    }

    private void verifyZ3OrderWhenSecondFinishesFirst() throws Exception {
        var release = new CountDownLatch(1);
        var firstIn = new CountDownLatch(1);
        var secondDone = new CountDownLatch(1);
        var market = new SamplePortfolioMarket() {
            @Override
            public List<PortfolioPosition> loadPositions(String id) {
                if (id.equals("P1")) {
                    firstIn.countDown();
                    await(release);
                }
                return super.loadPositions(id);
            }
        };
        var valuation = new PortfolioValuation(market, event -> {
            if (event.portfolioId().equals("P2") && event.stage().equals("calculate:ok")) {
                secondDone.countDown();
            }
        });
        try (var caller = Executors.newSingleThreadExecutor()) {
            var pending = caller.submit(() ->
                    tasks().evaluateBatch(valuation, List.of("P1", "bad-price", "P2"), RUB, 1));
            try {
                await(firstIn);
                await(secondDone);
                assertThat(pending.isDone()).isFalse();
                release.countDown();
                var results = pending.get(2, SECONDS);
                assertThat(results).extracting(PortfolioResult::portfolioId).containsExactly("P1", "bad-price", "P2");
                assertThat(results.get(1).error()).hasMessageContaining("UNKNOWN");
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void z4() throws Exception {
        var cards = tasks();
        assertThat(cards.currentSnapshot()).isNull();
        var oldReport = report("P1", "2100");
        cards.publishSnapshot(1, () -> oldReport);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var next = report("P1", "2460");
        var mutablePositions = new ArrayList<>(next.positions());
        try (var worker = Executors.newSingleThreadExecutor()) {
            var update = worker.submit(() -> cards.publishSnapshot(2, () -> {
                entered.countDown();
                await(release);
                return new PortfolioReport("P1", RUB, mutablePositions, next.total());
            }));
            try {
                await(entered);
                var observed = cards.currentSnapshot();
                assertThat(observed).isEqualTo(new PortfolioSnapshot(1, oldReport));
                release.countDown();
                update.get(2, SECONDS);
                mutablePositions.clear();
                assertThat(cards.currentSnapshot()).isEqualTo(new PortfolioSnapshot(2, next));
                assertThat(observed).isEqualTo(new PortfolioSnapshot(1, oldReport));
                assertThrows(UnsupportedOperationException.class, cards.currentSnapshot().report().positions()::clear);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void z5() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        verifyReadyOrder(id -> {
            if (id.equals("P1")) {
                entered.countDown();
                await(release);
            } else {
                await(entered);
            }
            if (id.equals("missing")) {
                throw new PortfolioHttpStatusException(404, "/v1/portfolios/missing/positions");
            }
            return report(id, "2100");
        }, release::countDown);
    }

    @Test
    void z5Http() throws Exception {
        var gate = new ResponseGate("P1", true);
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().extensions(gate));
        server.start();
        try {
            PortfolioMock.standard(server);
            PortfolioMock.stubPositions(server, "P1", okJson(PortfolioMock.POSITIONS).withTransformers(gate.getName()));
            var valuation = new PortfolioValuation(new PortfolioHttpClient(URI.create(server.baseUrl())), __ -> {});
            verifyReadyOrder(id -> {
                if (!id.equals("P1")) {
                    try {
                        assertThat(gate.awaitRequest()).isTrue();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                }
                var request = valuation.loadPositions(id, RUB);
                return valuation.calculateValue(request, valuation.loadQuotes(request), valuation.loadExchangeRates(request));
            }, gate::open);
        } finally {
            server.stop();
        }
    }

    private void verifyReadyOrder(Function<String, PortfolioReport> value, Runnable release) throws Exception {
        BlockingQueue<PortfolioResult> outputs = new LinkedBlockingQueue<>();
        try (var reader = Executors.newSingleThreadExecutor()) {
            var cards = tasks();
            var running = reader.submit(() -> {
                cards.forEachReady(List.of("P1", "P2", "missing"), value, outputs::add);
                return null;
            });
            try {
                var firstTwo = new ArrayList<PortfolioResult>();
                firstTwo.add(outputs.poll(2, SECONDS));
                firstTwo.add(outputs.poll(2, SECONDS));
                assertThat(firstTwo).doesNotContainNull().extracting(PortfolioResult::portfolioId)
                        .containsExactlyInAnyOrder("P2", "missing");
                var p2 = firstTwo.stream().filter(outcome -> outcome.portfolioId().equals("P2")).findFirst().orElseThrow();
                assertThat(p2.report().total()).isEqualByComparingTo("2100");
                var missing = firstTwo.stream().filter(outcome -> outcome.portfolioId().equals("missing"))
                        .findFirst().orElseThrow();
                assertThat(missing.error()).hasMessageContaining("HTTP 404");
                release.run();
                running.get(2, SECONDS);
                assertThat(outputs).extracting(PortfolioResult::portfolioId).containsExactly("P1");
            } finally {
                release.run();
            }
        }
    }

    private static PortfolioReport report(String id, String total) {
        var position = new PortfolioPosition("T", ONE, RUB);
        var amount = new BigDecimal(total);
        return new PortfolioReport(id, RUB, List.of(new PortfolioReportLine(position, amount)), amount);
    }

    private static void await(CountDownLatch checkpoint) {
        try {
            assertThat(checkpoint.await(4, SECONDS)).as("Контрольная точка достигнута").isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
