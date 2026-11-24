package academy.backend.market_pulse.exception;

/**
 * HTTP-статус источника портфельных данных для диагностики и выбора резервного поставщика.
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
