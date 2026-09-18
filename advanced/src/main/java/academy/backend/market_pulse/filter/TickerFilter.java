package academy.backend.market_pulse.filter;

import academy.backend.market_pulse.model.Instrument;

public class TickerFilter implements InstrumentFilter {

    private final String ticker;

    public TickerFilter(String ticker) {
        this.ticker = ticker;
    }

    @Override
    public boolean matches(Instrument instrument) {
        if (instrument.getTicker() == null) return false;
        return instrument
                .getTicker()
                .toUpperCase()
                .contains(ticker.toUpperCase());
    }
}
