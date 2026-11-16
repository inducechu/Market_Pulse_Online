package academy.backend.market_pulse.concurrent;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Фиксированный набор платформенных потоков, извлекающих задачи из блокирующей очереди.
 * Задачи выполняются в порядке постановки; ограничений размера и гарантий справедливости между worker нет.
 * Close вызывается владельцем после остановки всех producer; конкурентные submit и close не поддерживаются.
 * Interrupt во время idle-ожидания завершает worker; отмена выполняемой задачи не завершает worker.
 */
public final class BlockingQueueThreadPool implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(BlockingQueueThreadPool.class.getName());
    /**
     * Очередь ожидающих задач; потокобезопасна и сама блокирует worker, пока она пуста.
     * При close оставшиеся задачи отбрасываются.
     */
    private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    /**
     * Worker-потоки пула; массив заполняется до возврата из конструктора.
     */
    private final Thread[] workers;
    /**
     * Обработчик RuntimeException отдельных задач; такая ошибка не завершает worker.
     */
    private final Consumer<RuntimeException> onFailure;
    /**
     * Видимый всем worker-потокам запрос на выход из цикла выполнения.
     */
    private volatile boolean stopped;

    /**
     * Запускает worker-потоки. Задачи можно передавать только после завершения конструктора.
     */
    public BlockingQueueThreadPool(int size, Consumer<RuntimeException> onFailure) {
        if (size <= 0) {
            throw new IllegalArgumentException("Число worker-потоков должно быть положительным");
        }
        this.onFailure = Objects.requireNonNull(onFailure);
        this.workers = new Thread[size];
        for (int index = 0; index < size; index++) {
            this.workers[index] = Thread.ofPlatform()
                    .name("quote-worker-" + (index + 1))
                    .start(this::work);
        }
    }

    /**
     * Передаёт задачу worker-потокам; после close отклоняет её.
     */
    public void submit(Runnable task) {
        Objects.requireNonNull(task);
        if (stopped) {
            throw new IllegalStateException("Пул закрыт");
        }
        tasks.offer(task);
    }

    /**
     * Извлекает задачи до остановки пула; если очередь пуста, take блокирует worker до появления задачи.
     */
    private void work() {
        while (!stopped) {
            Runnable task;
            try {
                task = tasks.take();
            } catch (InterruptedException error) {
                return;
            }
            if (!stopped) {
                execute(task);
            }
        }
    }

    /**
     * Выполняет одну задачу и изолирует её ошибку и флаг прерывания от следующей задачи.
     */
    private void execute(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            reportFailure(error);
        } finally {
            Thread.interrupted();
        }
    }

    private void reportFailure(RuntimeException error) {
        try {
            onFailure.accept(error);
        } catch (RuntimeException reportingError) {
            LOGGER.log(System.Logger.Level.ERROR, "Ошибка обработчика задачи", reportingError);
        }
    }

    /**
     * Отбрасывает ожидающие задачи, прерывает worker-потоки и дожидается их завершения.
     * Вызывается владельцем пула, вне его worker-потоков.
     */
    @Override
    public void close() throws InterruptedException {
        stopped = true;
        tasks.clear();
        for (Thread worker : workers) {
            worker.interrupt();
        }
        boolean interrupted = Thread.interrupted();
        for (Thread worker : workers) {
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException error) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Ожидание остановки пула было прервано");
        }
    }
}
