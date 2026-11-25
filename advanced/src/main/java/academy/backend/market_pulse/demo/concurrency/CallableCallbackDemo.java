package academy.backend.market_pulse.demo.concurrency;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Две задачи в одном executor. Каждый следующий шаг задаётся callback-функцией. */
public final class CallableCallbackDemo {
    // Учебные цены в рублях. В портфеле по одной акции каждого тикера.
    private static final Map<String, Integer> QUOTES = Map.of("T", 500, "YDEX", 400);

    private CallableCallbackDemo() {}

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        var tickers = List.of("T", "YDEX");
        try (var dispatcher = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("quotes-", 1).factory())) {
            Future<Future<Integer>> loading = dispatcher.submit(() ->
                    getQuotes(tickers, quotes ->
                            dispatcher.submit(() ->
                                    processQuotes(quotes, total ->
                                            System.out.println(total + " RUB")))));

            // NOTICE: ждём в main. Если рабочий поток начнёт ждать следующую задачу в своём пуле,
            // он сам займёт единственное место, на котором она могла бы выполниться.
            Future<Integer> processing = loading.get();
            processing.get();
        }
    }

    private static <R> R getQuotes(List<String> tickers, Function<List<Integer>, R> onQuotes) {
        var quotes = tickers.stream().map(QUOTES::get).toList();
        System.out.printf("[%s] getQuotes: %s%n", Thread.currentThread().getName(), quotes);
        return onQuotes.apply(quotes);
    }

    private static int processQuotes(List<Integer> quotes, IntConsumer onTotal) {
        int total = quotes.stream().mapToInt(Integer::intValue).sum();
        System.out.printf("[%s] processQuotes%n", Thread.currentThread().getName());
        onTotal.accept(total);
        return total;
    }
}
