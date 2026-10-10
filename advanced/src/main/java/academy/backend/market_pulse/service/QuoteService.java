package academy.backend.market_pulse.service;

import academy.backend.market_pulse.model.Quote;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class QuoteService {

    private final QuoteSource source;
    // NOTICE: ArrayDeque — реализация Queue общего назначения на кольцевом буфере, offer/poll —
    // O(1) на обоих концах, накладных расходов меньше, чем у LinkedList.
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final Map<String, Quote> cache = new ConcurrentHashMap<>();

    public QuoteService(QuoteSource source) {
        this.source = source;
    }

    public void request(String ticker) {
        pending.offer(ticker.toUpperCase());
    }

    public void processAll() {
        if (pending.isEmpty()) return;

        ExecutorService executor = Executors.newCachedThreadPool();

        while (!pending.isEmpty()) {
            String ticker = pending.poll();
            // NOTICE: проверка кеша ДО обращения к source — повторный запрос того же тикера
            // не ходит к источнику снова, это и есть смысл кеша.
            if (ticker == null) continue;

            if (cache.containsKey(ticker)) {
                continue;
            }

            executor.submit(() -> {
                source.fetch(ticker).ifPresent(quote -> cache.put(ticker, quote));
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public Optional<Quote> cached(String ticker) {
        return Optional.ofNullable(cache.get(ticker.toUpperCase()));
    }
}
