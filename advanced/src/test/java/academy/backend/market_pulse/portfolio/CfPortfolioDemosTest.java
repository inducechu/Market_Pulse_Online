package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static academy.backend.market_pulse.model.Currency.RUB;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Показывает композицию стадий CompletableFuture и выбор executor на оценке портфеля.
 */
class CfPortfolioDemosTest {
    @ParameterizedTest(name = "{0}: первым возвращается {1}")
    @CsvSource({"FUTURE,quotes", "FUTURE,fx", "CF,quotes", "CF,fx"})
    void demoA(PortfolioEngine engine, String first) throws Exception {
        var quotes = new ResponseGate("quotes", true);
        var fx = new ResponseGate("fx", true);
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().extensions(quotes, fx));
        server.start();
        try (var caller = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("portfolio-caller").factory())) {
            PortfolioMock.stubPositions(server, "P1", okJson(PortfolioMock.POSITIONS));
            PortfolioMock.stubQuotes(server, okJson(PortfolioMock.QUOTES).withTransformers(quotes.getName()));
            PortfolioMock.stubRates(server, okJson(PortfolioMock.RATES).withTransformers(fx.getName()));
            var events = new ConcurrentLinkedQueue<PortfolioEvent>();
            var firstDone = new CountDownLatch(1);
            var valuation = new PortfolioValuation(new PortfolioHttpClient(URI.create(server.baseUrl())), event -> {
                events.add(event);
                System.out.println(event);
                if (event.stage().equals(first + ":ok")) {
                    firstDone.countDown();
                }
            });
            try (var service = PortfolioServiceFactory.create(engine, valuation, 2)) {
                var pending = caller.submit(() -> service.evaluatePortfolios(List.of("P1"), RUB));
                try {
                    assertThat(quotes.awaitRequest()).as("Запрос котировок уже на сервере").isTrue();
                    assertThat(fx.awaitRequest()).as("FX стартует до ответа котировок").isTrue();
                    (first.equals("quotes") ? quotes : fx).open();
                    assertThat(firstDone.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(pending.isDone()).isFalse();
                    (first.equals("quotes") ? fx : quotes).open();

                    var result = pending.get(5, TimeUnit.SECONDS).getFirst();
                    assertThat(result.error()).isNull();
                    assertThat(result.report().total()).isEqualByComparingTo("2100");
                    String second = first.equals("quotes") ? "fx" : "quotes";
                    assertThat(events).extracting(PortfolioEvent::stage)
                            .containsSubsequence("positions:ok", first + ":ok", second + ":ok", "calculate:ok");
                    assertThat(events).filteredOn(event -> event.stage().equals("calculate:ok"))
                            .allMatch(event -> event.thread().startsWith(
                                    engine == PortfolioEngine.CF ? "portfolio-calc-" : "portfolio-caller"));
                    server.verify(1, getRequestedFor(urlPathEqualTo("/v1/quotes")));
                    server.verify(1, getRequestedFor(urlPathEqualTo("/v1/fx")));
                } finally {
                    quotes.open();
                    fx.open();
                }
            }
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource({
            "200,, Основной HTTP 200 - резерв не вызывается",
            "503,200, Основной HTTP 503 - резерв HTTP 200",
            "404,, Основной HTTP 404 - резерв не вызывается",
            "503,502, Основной HTTP 503 - резерв HTTP 502"
    })
    void demoB(int primaryStatus, Integer backupStatus, String scenario) throws Exception {
        boolean backupFails = backupStatus != null && backupStatus != 200;
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        try (var io = PortfolioExecutors.ioExecutor(); var calc = PortfolioExecutors.calcExecutor()) {
            PortfolioMock.stubPositions(server, "P1", okJson(PortfolioMock.POSITIONS));
            PortfolioMock.stubQuotes(server, primaryStatus == 200
                    ? okJson(PortfolioMock.QUOTES) : aResponse().withStatus(primaryStatus));
            PortfolioMock.stubRates(server, okJson(PortfolioMock.RATES));
            if (backupStatus != null) {
                PortfolioMock.stubBackupQuotes(server, backupFails
                        ? aResponse().withStatus(backupStatus) : okJson(PortfolioMock.BACKUP));
            }
            var events = new ConcurrentLinkedQueue<PortfolioEvent>();
            var valuation = new PortfolioValuation(new PortfolioHttpClient(URI.create(server.baseUrl())), event -> {
                events.add(event);
                System.out.println(event);
            });
            var report = PortfolioServiceV2.evaluateAsync(valuation, "P1", RUB, io, calc, QuoteFailurePolicy.BACKUP_ON_503);
            if (primaryStatus == 404 || backupFails) {
                Throwable error = assertThrows(ExecutionException.class, () -> report.get(5, TimeUnit.SECONDS)).getCause();
                assertThat(error).isInstanceOfSatisfying(PortfolioHttpStatusException.class,
                        status -> assertThat(status.status()).isEqualTo(backupFails ? backupStatus : primaryStatus));
                assertThat(events).noneMatch(event -> event.stage().startsWith("calculate"));
                if (backupFails) {
                    assertThat(error.getSuppressed()).singleElement().satisfies(
                            suppressed -> assertThat(suppressed).hasMessageContaining("HTTP 503"));
                }
                System.out.println(error);
            } else {
                var result = report.get(5, TimeUnit.SECONDS);
                assertThat(result.total()).isEqualByComparingTo(backupStatus != null ? "2460" : "2100");
                assertThat(events).filteredOn(event -> event.stage().equals("calculate:ok"))
                        .singleElement().satisfies(event -> assertThat(event.thread()).startsWith("portfolio-calc-"));
                System.out.println("total=" + result.total());
            }
            server.verify(1, getRequestedFor(urlPathEqualTo("/v1/quotes")));
            server.verify(backupStatus != null ? 1 : 0, getRequestedFor(urlPathEqualTo("/v1/backup/quotes")));
            assertThat(events).filteredOn(event -> !event.stage().startsWith("calculate"))
                    .allMatch(event -> event.thread().startsWith("portfolio-io-"));
        } finally {
            server.stop();
        }
    }
}
