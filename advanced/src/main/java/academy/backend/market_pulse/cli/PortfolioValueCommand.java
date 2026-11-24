package academy.backend.market_pulse.cli;

import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.dto.portfolio.PortfolioEvaluationOptions;
import academy.backend.market_pulse.portfolio.PortfolioMarket;
import academy.backend.market_pulse.portfolio.QuoteFailurePolicy;
import academy.backend.market_pulse.dto.portfolio.PortfolioEngine;
import academy.backend.market_pulse.portfolio.PortfolioServiceFactory;
import academy.backend.market_pulse.portfolio.PortfolioValuation;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Запускает оценку портфелей выбранным способом и выводит отчёты или ошибки.
 */
@Command(name = "portfolio-value", mixinStandardHelpOptions = true, description = "Оценить портфели по котировкам и валютным курсам")
public final class PortfolioValueCommand implements Callable<Integer> {
    private final Supplier<PortfolioMarket> createMarket;

    @Parameters(arity = "1..*", paramLabel = "PORTFOLIO")
    private List<String> portfolioIds;

    @Option(names = "--currency", defaultValue = "RUB", description = "Валюта оценки")
    private Currency currency;

    @Option(names = "--engine", defaultValue = "VT", description = "Оркестрация: ${COMPLETION-CANDIDATES}")
    private PortfolioEngine engine;

    @Option(names = "--quote-limit", defaultValue = "2", description = "Квота одновременных запросов котировок для VT")
    private int quoteLimit;

    @Option(names = "--quote-failure", defaultValue = "REPORT_ERROR",
            description = "При сбое котировок: ${COMPLETION-CANDIDATES}")
    private QuoteFailurePolicy quoteFailure;

    @Option(names = "--trace", description = "Показать стадии, имена потоков и признак виртуального потока")
    private boolean trace;

    @Spec
    private CommandSpec spec;

    public PortfolioValueCommand(Supplier<PortfolioMarket> createMarket) {
        this.createMarket = createMarket;
    }

    @Override
    public Integer call() throws InterruptedException {
        PortfolioEvaluationOptions options;
        try {
            options = new PortfolioEvaluationOptions(portfolioIds, currency, engine, quoteLimit, quoteFailure, trace);
        } catch (IllegalArgumentException error) {
            throw new ParameterException(spec.commandLine(), error.getMessage());
        }
        PortfolioMarket market;
        try {
            market = createMarket.get();
        } catch (IllegalArgumentException error) {
            spec.commandLine().getErr().println(error.getMessage());
            return 1;
        }

        var out = spec.commandLine().getOut();
        var valuation = new PortfolioValuation(market, event -> {
            if (options.trace()) {
                out.printf("%s %s thread=%s virtual=%s%n",
                        event.portfolioId(), event.stage(), event.thread(), event.virtual());
            }
        });
        try (var service = PortfolioServiceFactory.create(options.engine(), valuation, options.quoteLimit())) {
            int exitCode = 0;
            for (var result : service.evaluatePortfolios(options.portfolioIds(), options.currency(), options.quoteFailure())) {
                if (result.error() == null) {
                    out.printf("%s: %s %s%n", result.portfolioId(), result.report().total().toPlainString(), options.currency());
                } else {
                    out.printf("%s: ошибка оценки - %s%n", result.portfolioId(), result.error().getMessage());
                    exitCode = 1;
                }
            }
            return exitCode;
        }
    }
}
