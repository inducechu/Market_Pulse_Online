package academy.backend.market_pulse.cli;

import academy.backend.market_pulse.dictionary.InstrumentType;
import academy.backend.market_pulse.filter.*;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.model.Instrument;
import academy.backend.market_pulse.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.math.BigDecimal;
import java.util.concurrent.Callable;

@RequiredArgsConstructor
@Command(name = "list", description = "Список инструментов")
public class ListCommand implements Callable<Integer> {

    @Option(names = "--type", description = "Фильтр по типу инструмента")
    private InstrumentType type;

    @Option(names = "--ticker", description = "Тикер")
    private String ticker;

    @Option(names = "--currency", description = "Кюренси")
    private Currency currency;

    @Option(names = "--price", description = "Стоимость")
    private BigDecimal price;

    @Option(names = "--price-op", description = "Опция по отношению к цене (например: GE, LE, EQ)")
    private String priceOp;

    private final InstrumentRepository repository;

    @Override
    public Integer call() {
        try {
            InstrumentFilter filter = findNeedFilter();

            for (Instrument instrument : repository) {
                if (filter.matches(instrument)) {
                    System.out.println(instrument.getName());
                }
            }
            return 0;

        } catch (IllegalArgumentException e) {
            System.err.println("Ошибка валидации: " + e.getMessage());
            return 1;
        }
    }

    private InstrumentFilter findNeedFilter() {
        int filtersCount = 0;

        if (type != null) filtersCount++;
        if (ticker != null) filtersCount++;
        if (currency != null) filtersCount++;
        if (price != null || priceOp != null) filtersCount++;

        if (filtersCount > 1) {
            throw new IllegalArgumentException("Несколько фильтров");
        }

        if (priceOp != null && price == null) {
            throw new IllegalArgumentException("Параметр --price-op требует --price.");
        }

        if (type != null) {
            return new TypeFilter(type);
        }
        if (ticker != null) {
            return new TickerFilter(ticker);
        }
        if (currency != null) {
            return new CurrencyFilter(currency);
        }
        if (price != null) {
            return new PriceFilter(priceOp, price);
        }

        return new AllPassFilter();
    }
}
