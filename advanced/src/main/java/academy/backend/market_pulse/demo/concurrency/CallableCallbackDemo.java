package academy.backend.market_pulse.demo.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Две задачи получают цену и курс. Каждая передаёт свой результат в callback. */
public final class CallableCallbackDemo {
    private CallableCallbackDemo() {}

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        show("Прямой вызов внутри call()", false);
        show("Callback отдельной задачей в том же пуле", true);
    }

    private static void show(String title, boolean submitCallback) throws InterruptedException, ExecutionException {
        System.out.println("\n" + title);
        var position = new Position();
        try (var pool = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("quotes-", 1).factory())) {
            var callbacks = new ConcurrentLinkedQueue<Future<?>>();
            Executor dispatch = submitCallback
                    ? callback -> callbacks.add(pool.submit(callback))
                    : Runnable::run;

            var quote = pool.submit(withCallback("Цена", () -> 10, position::onPrice, dispatch));
            var fx = pool.submit(withCallback("Курс", () -> 90, position::onRate, dispatch));
            quote.get();
            fx.get();
            // В режиме submit у каждого callback свой Future. Оба уже отправлены, когда call() вернулся.
            for (var callback : callbacks) {
                callback.get();
            }
        }
    }

    private static <T> Callable<T> withCallback(String name, Callable<T> request, Consumer<T> onLoaded,
                                                Executor dispatch) {
        return () -> {
            T value = request.call();
            System.out.printf("%s: получено %s, поток %s%n", name, value, Thread.currentThread().getName());
            Runnable callback = () -> {
                System.out.printf("%s: callback, поток %s%n", name, Thread.currentThread().getName());
                onLoaded.accept(value);
            };
            // NOTICE: значение уже получено. Здесь callback либо выполняется сразу, либо попадает в очередь пула.
            dispatch.execute(callback);
            return value;
        };
    }

    private static final class Position {
        private Integer price;
        private Integer rate;

        synchronized void onPrice(int value) {
            price = value;
            printIfReady();
        }

        synchronized void onRate(int value) {
            rate = value;
            printIfReady();
        }

        private void printIfReady() {
            // Второй callback видит оба результата под тем же монитором и печатает стоимость один раз.
            if (price != null && rate != null) {
                System.out.println("Стоимость позиции: " + price * rate + " RUB");
            }
        }
    }
}
