package academy.backend.market_pulse.service.portfolio;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/** Задачи одного вызова evaluatePortfolios. Отмена одного пакета оставляет остальные пакеты работать. */
final class PortfolioBatch {
    private final List<Future<?>> tasks = new ArrayList<>();
    private boolean cancelled;

    synchronized <T> Future<T> submit(ExecutorService executor, Callable<T> action) {
        if (cancelled) {
            throw new RejectedExecutionException("Оценка портфелей отменена");
        }
        var task = executor.submit(action);
        tasks.add(task);
        return task;
    }

    // NOTICE: CompletableFuture.cancel(true) не прерывает выполняющийся HTTP-запрос.
    // Сохраняем Future задачи исполнителя, в том числе для стадий, запущенных из thenCompose.
    Executor on(ExecutorService executor) {
        return action -> submit(executor, Executors.callable(action));
    }

    synchronized void cancel() {
        cancelled = true;
        tasks.forEach(task -> task.cancel(true));
    }
}
