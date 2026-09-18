package academy.backend.market_pulse.filter;

import academy.backend.market_pulse.model.Instrument;
import academy.backend.market_pulse.model.Stock;

import java.math.BigDecimal;

public class PriceFilter implements InstrumentFilter {
    private final String op;
    private final BigDecimal targetPrice;

    public PriceFilter(String op, BigDecimal targetPrice) {
        this.op = op;
        this.targetPrice = targetPrice;
    }

    @Override
    public boolean matches(Instrument instrument) {
        if (!(instrument instanceof Stock stock)) {
            return false;
        }

        BigDecimal yield = stock.getDividendYield();

        int comparison = yield.compareTo(targetPrice);
        return switch (op) {
            case "GE" -> comparison >= 0;
            case "LE" -> comparison <= 0;
            case "GT" -> comparison > 0;
            case "LT" -> comparison < 0;
            case "EQ" -> comparison == 0;
            default -> throw new IllegalArgumentException("Неизвестная операция: " + op);
        };
    }
}
