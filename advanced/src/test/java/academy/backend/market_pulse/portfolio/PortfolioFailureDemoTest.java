package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioReport;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static academy.backend.market_pulse.model.Currency.RUB;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Показывает отказ одной или двух независимых загрузок и переход на резерв.
 */
class PortfolioFailureDemoTest {
    private final WireMockServer server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
    private final ExecutorService io = PortfolioExecutors.ioExecutor();
    private final ExecutorService calc = PortfolioExecutors.calcExecutor();
    private final ConcurrentLinkedQueue<PortfolioEvent> events = new ConcurrentLinkedQueue<>();
    private PortfolioValuation valuation;

    @BeforeEach
    void startMarket() {
        server.start();
        PortfolioMock.standard(server);
        valuation = new PortfolioValuation(new PortfolioHttpClient(URI.create(server.baseUrl())), event -> {
            events.add(event);
            System.out.printf("%s %s [%s]%n", event.portfolioId(), event.stage(), event.thread());
        });
    }

    @AfterEach
    void closeResources() {
        io.close();
        calc.close();
        server.stop();
    }

    @Test
    void bothSucceed() throws Exception {
        assertThat(evaluate().get(5, TimeUnit.SECONDS).total()).isEqualByComparingTo("2100");
        assertThat(events).extracting(PortfolioEvent::stage).contains("quotes:ok", "fx:ok", "calculate:ok");
    }

    @Test
    void quoteFailure() {
        failQuotes();
        var failure = observeFailure(evaluate());
        assertThat(failure).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/quotes");
        assertThat(events).extracting(PortfolioEvent::stage).contains("fx:ok", "quotes:error");
    }

    @Test
    void exchangeRateFailure() {
        failRates();
        var failure = observeFailure(evaluate());
        assertThat(failure).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/fx");
        assertThat(events).extracting(PortfolioEvent::stage).contains("fx:error", "quotes:ok");
    }

    @Test
    void bothFail() {
        failQuotes();
        failRates();
        assertBothCauses(observeFailure(evaluate()));
    }

    @ParameterizedTest
    @EnumSource(value = PortfolioEngine.class, names = {"FUTURE", "CF"})
    void parallelImplementationsKeepBothCauses(PortfolioEngine engine) throws InterruptedException {
        failQuotes();
        failRates();
        try (var service = PortfolioServiceFactory.create(engine, valuation, 2)) {
            var result = service.evaluatePortfolios(List.of("P1"), RUB).getFirst();
            assertBothCauses(result.error());
        }
    }

    private CompletableFuture<PortfolioReport> evaluate() {
        return PortfolioServiceV2.evaluateAsync(valuation, "P1", RUB, io, calc, QuoteFailurePolicy.REPORT_ERROR);
    }

    private Throwable observeFailure(CompletableFuture<PortfolioReport> report) {
        // NOTICE: ошибка доходит до вызывающего кода. get и join оборачивают одну причину по-разному.
        var total = report.thenApply(PortfolioReport::total);
        var fromGet = assertThrows(ExecutionException.class, () -> total.get(5, TimeUnit.SECONDS));
        var fromJoin = assertThrows(CompletionException.class, total::join);
        System.out.println("get(): " + fromGet);
        System.out.println("join(): " + fromJoin);
        assertThat(fromJoin.getCause()).isSameAs(fromGet.getCause());
        assertThat(events).noneMatch(event -> event.stage().startsWith("calculate"));
        return fromGet.getCause();
    }

    private void assertBothCauses(Throwable failure) {
        // Обе причины доступны благодаря явной обработке в сервисе.
        assertThat(failure).isInstanceOf(MarketDataException.class);
        assertThat(failure.getCause()).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/quotes");
        assertThat(failure.getSuppressed()).singleElement().satisfies(error ->
                assertThat(error).isInstanceOf(PortfolioHttpStatusException.class).hasMessageContaining("/v1/fx"));
    }

    private void failQuotes() {
        PortfolioMock.stubQuotes(server, aResponse().withStatus(500));
    }

    private void failRates() {
        PortfolioMock.stubRates(server, aResponse().withStatus(500));
    }
}
