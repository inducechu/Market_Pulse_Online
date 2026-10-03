package academy.backend.market_pulse.exception;

/**
 * Ошибка сохранения инструментов на диск — оборачивает {@link java.io.IOException} из
 * {@code SaveCommand}, не теряя исходную причину.
 * TODO: требует реализации и применения!
 */
public class InstrumentSaveException extends RuntimeException {

    public InstrumentSaveException(String message) {
        super(message);
    }
}
