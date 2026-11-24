package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.dto.portfolio.PortfolioPosition;
import academy.backend.market_pulse.model.Currency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Загружает учебные рыночные данные через блокирующий HTTP API с ограниченным временем ожидания.
 */
public final class PortfolioHttpClient implements PortfolioMarket {
    /**
     * Общий JSON mapper, настроенный до начала конкурентных запросов.
     */
    private final ObjectMapper mapper = new ObjectMapper();
    /**
     * Адрес учебного поставщика рыночных данных.
     */
    private final URI baseUri;

    public PortfolioHttpClient(URI baseUri) {
        this.baseUri = baseUri;
    }

    @Override
    public List<PortfolioPosition> loadPositions(String portfolioId) {
        return get("/v1/portfolios/" + encode(portfolioId).replace("+", "%20") + "/positions",
                new TypeReference<>() {});
    }

    @Override
    public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
        return loadQuotes("/v1/quotes", tickers);
    }

    @Override
    public Map<String, BigDecimal> loadBackupQuotes(Set<String> tickers) {
        return loadQuotes("/v1/backup/quotes", tickers);
    }

    private Map<String, BigDecimal> loadQuotes(String path, Set<String> tickers) {
        return tickers.isEmpty() ? Map.of() : get(path + "?tickers=" + csv(tickers), new TypeReference<>() {});
    }

    @Override
    public Map<Currency, BigDecimal> loadExchangeRates(Set<Currency> from, Currency to) {
        return from.isEmpty() ? Map.of() : get("/v1/fx?from=" + csv(from) + "&to=" + to, new TypeReference<>() {});
    }

    /**
     * Выполняет HTTP-запрос, проверяет статус и читает JSON; соединение закрывается при любом исходе.
     */
    private <T> T get(String resource, TypeReference<T> type) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) baseUri.resolve(resource).toURL().openConnection();
            connection.setConnectTimeout(2_000);
            connection.setReadTimeout(8_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");

            int status = connection.getResponseCode(); // NOTICE: блокирующее ожидание HTTP-ответа.
            if (status != 200) {
                throw new PortfolioHttpStatusException(status, resource);
            }
            try (InputStream input = connection.getInputStream()) {
                return mapper.readValue(input, type);
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Не удалось загрузить " + resource, error);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String csv(Set<?> values) {
        return values.stream().map(Object::toString).sorted().map(PortfolioHttpClient::encode)
                .collect(Collectors.joining(","));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
