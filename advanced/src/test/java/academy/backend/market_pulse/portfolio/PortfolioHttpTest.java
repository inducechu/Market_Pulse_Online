package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URI;
import java.util.List;

import static academy.backend.market_pulse.model.Currency.RUB;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет HTTP-контракт загрузки рыночных данных и обработку некорректных ответов.
 */
class PortfolioHttpTest {
    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void sameReportAndBatchedRequestsOverHttp(PortfolioEngine engine) throws InterruptedException {
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        server.start();
        try {
            PortfolioMock.standard(server);
            var market = new PortfolioHttpClient(URI.create(server.baseUrl()));
            var valuation = new PortfolioValuation(market, System.out::println);
            try (var service = PortfolioServiceFactory.create(engine, valuation, 1)) {
                var results = service.evaluatePortfolios(List.of("P1", "EMPTY", "NO-PRICE", "NO-FX"), RUB);
                assertThat(results.get(0).report().total()).isEqualByComparingTo("2100");
                assertThat(results.get(1).report().total()).isEqualByComparingTo("0");
                assertThat(results.get(2).error()).hasMessageContaining("UNKNOWN");
                assertThat(results.get(3).error()).hasMessageContaining("EUR");
                server.verify(1, getRequestedFor(urlPathEqualTo("/v1/quotes")).withQueryParam("tickers", equalTo("AAPL,T")));
                server.verify(3, getRequestedFor(urlPathEqualTo("/v1/quotes")));
                server.verify(1, getRequestedFor(urlPathEqualTo("/v1/fx")).withQueryParam("from", equalTo("USD"))
                        .withQueryParam("to", equalTo("RUB")));
                server.verify(2, getRequestedFor(urlPathEqualTo("/v1/fx")));
            }
        } finally {
            server.stop();
        }
    }
}
