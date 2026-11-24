package academy.backend.market_pulse.portfolio;

import academy.backend.market_pulse.dto.portfolio.PortfolioResult;
import academy.backend.market_pulse.dto.portfolio.PortfolioReport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Собирает результаты Future и снимает обёртки асинхронных исключений.
 */
final class AsyncUtils {
    private AsyncUtils() {}

    /**
     * Ожидает задачи в порядке ввода; ошибка задачи становится результатом, прерывание передаётся вызывающему коду.
     */
    static List<PortfolioResult> collectInOrder(List<String> ids, List<? extends Future<PortfolioReport>> pending)
            throws InterruptedException {
        List<PortfolioResult> results = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            try {
                results.add(PortfolioResult.success(pending.get(i).get()));
            } catch (ExecutionException error) {
                results.add(PortfolioResult.failure(ids.get(i), unwrapAsyncException(error)));
            }
        }
        return List.copyOf(results);
    }

    static Throwable unwrapAsyncException(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
