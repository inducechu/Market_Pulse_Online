package academy.backend.market_pulse.portfolio;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Ответы локального HTTP-сервера для контрактных тестов портфеля.
 */
final class PortfolioMock {
    static final String POSITIONS = """
            [
              {"ticker":"T","quantity":3,"currency":"RUB"},
              {"ticker":"AAPL","quantity":2,"currency":"USD"}
            ]
            """;
    static final String QUOTES = """
            {"AAPL":10,"T":100}
            """;
    static final String RATES = """
            {"USD":90}
            """;

    private PortfolioMock() {}

    static void standard(WireMockServer server) {
        stubPositions(server, "P1", okJson(POSITIONS));
        stubPositions(server, "P2", okJson(POSITIONS));
        stubPositions(server, "EMPTY", okJson("[]"));
        stubPositions(server, "NO-PRICE", okJson("""
                [{"ticker":"UNKNOWN","quantity":1,"currency":"RUB"}]
                """));
        stubPositions(server, "NO-FX", okJson("""
                [{"ticker":"AAPL","quantity":1,"currency":"EUR"}]
                """));
        stubQuotes(server, okJson(QUOTES));
        server.stubFor(get(urlPathEqualTo("/v1/quotes")).withQueryParam("tickers", equalTo("UNKNOWN"))
                .willReturn(okJson("{}")));
        server.stubFor(get(urlPathEqualTo("/v1/quotes")).withQueryParam("tickers", equalTo("AAPL"))
                .willReturn(okJson(QUOTES)));
        server.stubFor(get(urlPathEqualTo("/v1/fx")).withQueryParam("from", equalTo("EUR"))
                .withQueryParam("to", equalTo("RUB")).willReturn(okJson("{}")));
        stubRates(server, okJson(RATES));
    }

    static void stubPositions(WireMockServer server, String id, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlEqualTo("/v1/portfolios/" + id + "/positions")).willReturn(response));
    }

    static void stubQuotes(WireMockServer server, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo("/v1/quotes")).withQueryParam("tickers", equalTo("AAPL,T"))
                .willReturn(response));
    }

    static void stubRates(WireMockServer server, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo("/v1/fx")).withQueryParam("from", equalTo("USD"))
                .withQueryParam("to", equalTo("RUB")).willReturn(response));
    }

}
