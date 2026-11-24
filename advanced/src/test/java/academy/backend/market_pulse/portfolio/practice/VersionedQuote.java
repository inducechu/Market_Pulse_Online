package academy.backend.market_pulse.portfolio.practice;

import java.math.BigDecimal;

/**
 * Неизменяемые данные для проверки публикации и порядка обновлений.
 */
public record VersionedQuote(long version, BigDecimal price) {}
