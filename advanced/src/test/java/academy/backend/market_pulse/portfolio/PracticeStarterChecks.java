package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.portfolio.practice.DefectCards;
import academy.backend.market_pulse.portfolio.practice.DefectStarters;
import academy.backend.market_pulse.portfolio.practice.TaskCards;
import academy.backend.market_pulse.portfolio.practice.TaskStarters;

import java.util.concurrent.Executor;

/**
 * Явный запуск: mvn -pl advanced -Dtest=PracticeStarterChecks#d1 test
 */
public class PracticeStarterChecks extends PracticeContracts {
    @Override
    DefectCards defects(Executor io) {
        return new DefectStarters(io);
    }

    @Override
    TaskCards tasks() {
        return new TaskStarters();
    }
}
