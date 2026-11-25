package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.client.impl.PortfolioHttpClient;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.exception.MarketDataException;
import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.service.portfolio.PortfolioServiceFactory;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static academy.backend.market_pulse.model.Currency.RUB;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/** Контракт HTTP для каждого движка, включая сетевые ошибки и независимость запросов. */
class PortfolioHttpTest {
    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void sameReportAndBatchedRequestsOverHttp(PortfolioEngine engine) throws InterruptedException {
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        try (var market = new PortfolioHttpClient(URI.create(server.baseUrl()));
             var service = PortfolioServiceFactory.create(engine, market, 1, event -> {})) {
            PortfolioMock.standard(server);
            PortfolioMock.stubPositions(server, "BROKEN", aResponse().withFault(Fault.EMPTY_RESPONSE));
            var ids = List.of("P1", "BROKEN", "EMPTY", "NO-PRICE", "NO-FX");
            var results = service.evaluatePortfolios(ids, RUB);
            assertThat(results).extracting(PortfolioResult::portfolioId).containsExactlyElementsOf(ids);
            assertThat(results.get(0).report().total()).isEqualByComparingTo("2100");
            assertThat(results.get(1).report()).isNull();
            assertThat(results.get(1).error()).isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("positions").hasMessageContaining("BROKEN").hasCauseInstanceOf(IOException.class);
            assertThat(results.get(2).report().total()).isEqualByComparingTo("0");
            assertThat(results.get(3).error()).hasMessageContaining("UNKNOWN");
            assertThat(results.get(4).error()).hasMessageContaining("EUR");
            server.verify(1, getRequestedFor(urlPathEqualTo("/v1/quotes")).withQueryParam("tickers", equalTo("AAPL,T")));
            server.verify(3, getRequestedFor(urlPathEqualTo("/v1/quotes")));
            server.verify(1, getRequestedFor(urlPathEqualTo("/v1/fx")).withQueryParam("from", equalTo("USD"))
                    .withQueryParam("to", equalTo("RUB")));
            server.verify(2, getRequestedFor(urlPathEqualTo("/v1/fx")));
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @CsvSource({"FUTURE,quotes", "FUTURE,fx", "CF,quotes", "CF,fx", "VT,quotes", "VT,fx"})
    void startsBothRequestsAndWaitsForBothResponses(PortfolioEngine engine, String first) throws Exception {
        var quotes = new ResponseGate("quotes", true);
        var fx = new ResponseGate("fx", true);
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().extensions(quotes, fx));
        server.start();
        var firstReady = new CountDownLatch(1);
        try (var market = new PortfolioHttpClient(URI.create(server.baseUrl()));
             var service = PortfolioServiceFactory.create(engine, market, 1, event -> {
                 if (event.stage().equals(first + ":ok")) {
                     firstReady.countDown();
                 }
             }); var caller = Executors.newSingleThreadExecutor()) {
            PortfolioMock.stubPositions(server, "P1", okJson(PortfolioMock.POSITIONS));
            PortfolioMock.stubQuotes(server, okJson(PortfolioMock.QUOTES).withTransformers(quotes.getName()));
            PortfolioMock.stubRates(server, okJson(PortfolioMock.RATES).withTransformers(fx.getName()));
            var report = caller.submit(() -> service.evaluatePortfolios(List.of("P1"), RUB).getFirst());
            try {
                assertThat(quotes.awaitRequest()).as("Котировки дошли до сервера").isTrue();
                assertThat(fx.awaitRequest()).as("Курсы запрошены до получения котировок").isTrue();
                (first.equals("quotes") ? quotes : fx).open();
                assertThat(firstReady.await(3, SECONDS)).isTrue();
                assertThat(report.isDone()).as("Отчёт ждёт второй запрос").isFalse();
            } finally {
                fx.open();
                quotes.open();
            }
            assertThat(report.get(3, SECONDS).report().total()).isEqualByComparingTo("2100");
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void preservesSingleAndDoubleHttpFailuresAndReleasesQuotePermit(PortfolioEngine engine) throws Exception {
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        var events = new ConcurrentLinkedQueue<PortfolioEvent>();
        try (var market = new PortfolioHttpClient(URI.create(server.baseUrl()));
             var service = PortfolioServiceFactory.create(engine, market, 1, events::add)) {
            PortfolioMock.standard(server);
            PortfolioMock.stubQuotes(server, aResponse().withStatus(503));
            var pricesFailed = service.evaluatePortfolios(List.of("P1", "EMPTY"), RUB);
            assertThat(pricesFailed.getFirst().error()).isInstanceOf(PortfolioHttpStatusException.class)
                    .hasMessageContaining("503").hasMessageContaining("/v1/quotes");
            assertThat(pricesFailed.getLast().report().total()).isEqualByComparingTo("0");

            PortfolioMock.stubRates(server, aResponse().withStatus(502));
            var bothFailed = service.evaluatePortfolios(List.of("P1"), RUB).getFirst().error();
            assertThat(bothFailed).isInstanceOf(MarketDataException.class);
            assertThat(bothFailed.getCause()).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/quotes");
            assertThat(bothFailed.getSuppressed()).singleElement().satisfies(rate ->
                    assertThat(rate).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/fx"));

            PortfolioMock.stubQuotes(server, okJson(PortfolioMock.QUOTES));
            var ratesFailed = service.evaluatePortfolios(List.of("P1"), RUB).getFirst().error();
            assertThat(ratesFailed).isInstanceOf(PortfolioHttpStatusException.class)
                    .hasMessageContaining("502").hasMessageContaining("/v1/fx");
            assertThat(events).filteredOn(event -> event.portfolioId().equals("P1"))
                    .noneMatch(event -> event.stage().startsWith("calculate"));

            PortfolioMock.stubRates(server, okJson(PortfolioMock.RATES));
            assertThat(service.evaluatePortfolios(List.of("P1"), RUB).getFirst().report().total()).isEqualByComparingTo("2100");
        } finally {
            server.stop();
        }
    }
}
