package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.exception.PortfolioHttpStatusException;
import academy.backend.market_pulse.cli.PortfolioValueCommand;
import academy.backend.market_pulse.portfolio.SamplePortfolioMarket;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import picocli.CommandLine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет формат CLI-результатов, коды завершения и отложенное чтение конфигурации.
 */
class PortfolioValueCommandTest {
    @Test
    void rejectsInvalidOptionsBeforeCreatingMarket() {
        var errors = new StringWriter();
        var command = new CommandLine(new PortfolioValueCommand(() -> {
            throw new AssertionError("Недопустимые параметры не должны запускать загрузку");
        })).setErr(new PrintWriter(errors));
        assertThat(command.execute("P1", "--quote-limit", "0")).isEqualTo(2);
        assertThat(errors.toString()).contains("--quote-limit должен быть положительным");
        errors.getBuffer().setLength(0);
        assertThat(command.execute(" ")).isEqualTo(2);
        assertThat(errors.toString()).contains("непустые идентификаторы");
    }

    @Test
    void marketConfigurationIsOnlyNeededForValuation() {
        var output = new StringWriter();
        var errors = new StringWriter();
        var command = new CommandLine(new PortfolioValueCommand(() -> {
            throw new IllegalArgumentException("Неверный адрес в MARKET_PULSE_PORTFOLIO_URL");
        })).setOut(new PrintWriter(output)).setErr(new PrintWriter(errors));

        assertThat(command.execute("--help")).isZero();
        assertThat(output.toString()).contains("portfolio-value");
        assertThat(errors.toString()).isEmpty();
        assertThat(command.execute("P1")).isEqualTo(1);
        assertThat(errors.toString().lines()).containsExactly("Неверный адрес в MARKET_PULSE_PORTFOLIO_URL");
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void retriesQuotesOnlyWhenRequested(PortfolioEngine engine) {
        var output = new StringWriter();
        var command = new CommandLine(new PortfolioValueCommand(() -> new SamplePortfolioMarket() {
            @Override
            public Map<String, BigDecimal> loadQuotes(Set<String> tickers) {
                throw new PortfolioHttpStatusException(503, "/v1/quotes");
            }
        })).setOut(new PrintWriter(output));
        assertThat(command.execute("P1", "--engine", engine.name())).isEqualTo(1);
        assertThat(output.toString()).contains("HTTP 503");
        output.getBuffer().setLength(0);
        assertThat(command.execute("P1", "--engine", engine.name(), "--quote-failure", "BACKUP_ON_503")).isZero();
        assertThat(output.toString().strip()).isEqualTo("P1: 2460 RUB");
    }

    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void formatsResultsInInputOrderAndReturnsBatchExitCode(PortfolioEngine engine) {
        var output = new StringWriter();
        var command = new CommandLine(new PortfolioValueCommand(SamplePortfolioMarket::new)).setOut(new PrintWriter(output));
        assertThat(command.execute("P2", "empty", "bad-price", "P1", "--currency", "RUB", "--engine", engine.name()))
                .isEqualTo(1);
        assertThat(output.toString().lines()).containsExactly(
                "P2: 2100 RUB", "empty: 0 RUB",
                "bad-price: ошибка оценки - Отсутствует цена для UNKNOWN", "P1: 2100 RUB");
        assertThat(command.execute("P1", "P2", "--engine", engine.name())).isZero();
    }
}
