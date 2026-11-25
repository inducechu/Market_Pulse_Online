package academy.backend.market_pulse.client.impl;

import academy.backend.market_pulse.client.PortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.model.Currency;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Клиент портфельного API. Все запросы одного клиента используют общий пул HTTP-соединений. */
public final class PortfolioHttpClient implements PortfolioMarket {
    private final URI baseUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public PortfolioHttpClient(URI baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public List<PortfolioPosition> loadPositions(String portfolioId) throws IOException, InterruptedException {
        String id = encode(portfolioId).replace("+", "%20");
        return get("/v1/portfolios/" + id + "/positions", new TypeReference<>() {});
    }

    @Override
    public Map<String, BigDecimal> loadQuotes(Set<String> tickers) throws IOException, InterruptedException {
        return tickers.isEmpty() ? Map.of() : get("/v1/quotes?tickers=" + csv(tickers), new TypeReference<>() {});
    }

    @Override
    public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to)
            throws IOException, InterruptedException {
        return from.isEmpty() ? Map.of() : get("/v1/fx?from=" + csv(from) + "&to=" + to, new TypeReference<>() {});
    }

    private <T> T get(String resource, TypeReference<T> type) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(baseUri.resolve(resource)).timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json").GET().build();
        // NOTICE: send блокирует текущий поток. При отмене задачи interrupt прерывает ожидание ответа.
        var response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new PortfolioHttpStatusException(response.statusCode(), resource);
        }
        return mapper.readValue(response.body(), type);
    }

    private static String csv(Set<?> values) {
        return values.stream().map(Object::toString).sorted().map(PortfolioHttpClient::encode)
                .collect(Collectors.joining(","));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        http.close();
    }
}
