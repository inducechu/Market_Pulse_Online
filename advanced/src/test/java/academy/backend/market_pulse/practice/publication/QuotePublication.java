package academy.backend.market_pulse.practice.publication;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;

final class QuotePublication {
    private int priceInKopecks;
    private volatile boolean ready;

    CompletableFuture<Void> publishWhenReady(CompletableFuture<Integer> response) {
        // TODO: определить, какие данные должны быть записаны к моменту публикации флага.
        ready = true;
        return response.thenAccept(price -> priceInKopecks = price);
    }

    OptionalInt current() {
        if (ready) {
            return OptionalInt.of(priceInKopecks);
        }
        return OptionalInt.empty();
    }
}
