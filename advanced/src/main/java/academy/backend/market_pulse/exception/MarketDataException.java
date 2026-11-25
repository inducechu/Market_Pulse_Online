package academy.backend.market_pulse.exception;

/** Обе независимые загрузки завершились ошибкой. Первая причина относится к котировкам, вторая к курсам. */
public final class MarketDataException extends RuntimeException {
    public MarketDataException(String id, Throwable quotes, Throwable rates) {
        super("Не получены котировки и курсы для " + id + ": " + quotes.getMessage() + "; " + rates.getMessage(), quotes);
        addSuppressed(rates);
    }
}
