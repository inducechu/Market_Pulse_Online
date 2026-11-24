package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReportLine;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvent;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static academy.backend.market_pulse.model.Currency.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет одинаковые результаты и порядок отчётов у трёх способов оценки.
 */
public class PortfolioEngineTest {
    @ParameterizedTest
    @EnumSource(PortfolioEngine.class)
    void sameTotalsOrderAndExplicitErrors(PortfolioEngine engine) throws InterruptedException {
        var events = new ConcurrentLinkedQueue<PortfolioEvent>();
        var valuation = new PortfolioValuation(new SamplePortfolioMarket(), events::add);
        List<String> ids = List.of("P2", "bad-price", "empty", "bad-fx", "P1", "P2");
        List<PortfolioResult> results;
        try (var service = PortfolioServiceFactory.create(engine, valuation, 1)) {
            results = service.evaluatePortfolios(ids, RUB);
        }

        assertThat(results).extracting(PortfolioResult::portfolioId).containsExactlyElementsOf(ids);
        for (int index : List.of(0, 4, 5)) {
            assertThat(results.get(index).report().total()).isEqualByComparingTo("2100");
            assertThat(results.get(index).report().positions()).extracting(PortfolioReportLine::value)
                    .containsExactly(new BigDecimal("300"), new BigDecimal("1800"));
        }
        assertThat(results.get(1).error()).hasMessageContaining("цена").hasMessageContaining("UNKNOWN");
        assertThat(results.get(2).report().total()).isEqualByComparingTo("0");
        assertThat(events).filteredOn(event -> event.portfolioId().equals("empty"))
                .extracting(PortfolioEvent::stage)
                .containsExactly("positions:start", "positions:ok", "calculate:start", "calculate:ok");
        assertThat(results.get(3).error()).hasMessageContaining("курс").hasMessageContaining("EUR");

        var p1 = events.stream().filter(event -> event.portfolioId().equals("P1")).toList();
        assertThat(p1.getFirst().stage()).isEqualTo("positions:start");
        assertThat(p1.getLast().stage()).isEqualTo("calculate:ok");
        if (engine == PortfolioEngine.VT) {
            assertThat(p1).allMatch(PortfolioEvent::virtual);
            assertThat(p1).extracting(PortfolioEvent::thread).containsOnly(p1.getFirst().thread());
            assertThat(p1).extracting(PortfolioEvent::stage).containsExactly(
                    "positions:start", "positions:ok", "quotes:start", "quotes:ok",
                    "fx:start", "fx:ok", "calculate:start", "calculate:ok");
        } else {
            assertThat(p1).noneMatch(PortfolioEvent::virtual);
            assertThat(p1.subList(0, 6)).allMatch(event -> event.thread().startsWith("portfolio-io-"));
            String calcThread = p1.getLast().thread();
            if (engine == PortfolioEngine.CF) {
                assertThat(calcThread).startsWith("portfolio-calc-");
            } else {
                assertThat(calcThread).isEqualTo(Thread.currentThread().getName());
            }
        }
    }
}
