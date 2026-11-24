package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.portfolio.practice.DefectCards;
import academy.backend.market_pulse.portfolio.practice.DefectReference;
import academy.backend.market_pulse.portfolio.practice.TaskCards;
import academy.backend.market_pulse.portfolio.practice.TaskReference;

import java.util.concurrent.Executor;

/**
 * Проверяет готовые решения всех карточек практики.
 */
class PracticeReferenceTest extends PracticeContracts {
    @Override
    DefectCards defects(Executor io) {
        return new DefectReference(io);
    }

    @Override
    TaskCards tasks() {
        return new TaskReference();
    }
}
