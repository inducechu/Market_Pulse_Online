package academy.backend.market_pulse.portfolio;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Поднимает локальный HTTP mock с позициями, котировками, курсами и управляемыми ошибками.
 */
public final class PortfolioMock {
    public static final String POSITIONS = """
            [
              {"ticker":"T","quantity":3,"currency":"RUB"},
              {"ticker":"AAPL","quantity":2,"currency":"USD"}
            ]
            """;
    public static final String QUOTES = """
            {"AAPL":10,"T":100}
            """;
    public static final String BACKUP = """
            {"AAPL":12,"T":100}
            """;
    public static final String RATES = """
            {"USD":90}
            """;

    private PortfolioMock() {}

    public static void main(String[] args) throws InterruptedException {
        int optionIndex = args.length > 0 && !args[0].startsWith("--") ? 1 : 0;
        int port = optionIndex == 1 ? Integer.parseInt(args[0]) : 1081;
        var flags = new HashSet<>(Arrays.asList(args).subList(optionIndex, args.length));
        boolean quotesUnavailable = flags.remove("--quotes-503");
        boolean quotesFailed = flags.remove("--quotes-500");
        boolean ratesFailed = flags.remove("--rates-500");
        if (!flags.isEmpty() || (quotesUnavailable && quotesFailed)) {
            throw new IllegalArgumentException("Аргументы: [порт] [--quotes-500 | --quotes-503] [--rates-500]");
        }

        var server = new WireMockServer(options().bindAddress("127.0.0.1").port(port));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "stop-portfolio-mock"));
        standard(server);
        if (quotesUnavailable || quotesFailed) {
            stubFailure(server, "/v1/quotes", quotesUnavailable ? 503 : 500);
        }
        if (ratesFailed) {
            stubFailure(server, "/v1/fx", 500);
        }
        System.out.println("Portfolio mock: http://127.0.0.1:" + server.port());
        new CountDownLatch(1).await();
    }

    private static void stubFailure(WireMockServer server, String path, int status) {
        server.stubFor(get(urlPathEqualTo(path)).atPriority(1).willReturn(aResponse().withStatus(status)));
        System.out.println(path + " вернёт HTTP " + status);
    }

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
        stubBackupQuotes(server, okJson(BACKUP));
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

    static void stubBackupQuotes(WireMockServer server, ResponseDefinitionBuilder response) {
        server.stubFor(get(urlPathEqualTo("/v1/backup/quotes")).withQueryParam("tickers", equalTo("AAPL,T"))
                .willReturn(response));
    }
}
