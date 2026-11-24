package academy.backend.market_pulse.portfolio;

/**
 * Обе независимые загрузки завершились ошибкой. Причины перечислены в порядке: котировки, курсы.
 */
public final class MarketDataException extends RuntimeException {
    MarketDataException(String id, Throwable quotes, Throwable rates) {
        super("Не получены котировки и курсы для " + id + ": " + quotes.getMessage() + "; " + rates.getMessage(), quotes);
        addSuppressed(rates);
    }
}
