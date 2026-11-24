package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.exception.PortfolioHttpStatusException;

/**
 * Действие после отказа основного поставщика котировок.
 */
public enum QuoteFailurePolicy {
    REPORT_ERROR,
    BACKUP_ON_503;

    boolean canUseBackup(Throwable error) {
        return this == BACKUP_ON_503 && error instanceof PortfolioHttpStatusException status && status.status() == 503;
    }
}
