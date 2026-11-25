package academy.backend.market_pulse.exception;

/**
 * HTTP-статус и адрес запроса, который не удалось выполнить.
 */
public final class PortfolioHttpStatusException extends RuntimeException {
    private final int status;

    public PortfolioHttpStatusException(int status, String resource) {
        super("HTTP " + status + " для " + resource);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
