package academy.backend.market_pulse.filter;

import academy.backend.market_pulse.dictionary.InstrumentType;
import academy.backend.market_pulse.model.Instrument;

public class TypeFilter implements InstrumentFilter {
    private final InstrumentType type;

    public TypeFilter(InstrumentType type) {
        this.type = type;
    }

    @Override
    public boolean matches(Instrument instrument) {
        return instrument.getType() == type;
    }
}
