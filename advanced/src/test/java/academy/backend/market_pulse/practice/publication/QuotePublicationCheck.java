package academy.backend.market_pulse.practice.publication;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuotePublicationCheck {
    @Test
    void readersSeeThePriceOnlyAfterTheQuoteArrives() throws Exception {
        var quote = new QuotePublication();
        var response = new CompletableFuture<Integer>();
        var publication = quote.publishWhenReady(response);

        assertEquals(OptionalInt.empty(), quote.current(), "Поставщик ещё не прислал цену");
        response.complete(31_550);
        publication.get(5, SECONDS);
        assertEquals(OptionalInt.of(31_550), quote.current());
    }

    @Test
    void failedResponseLeavesThePriceUnpublished() {
        var quote = new QuotePublication();
        var response = CompletableFuture.<Integer>failedFuture(new IllegalStateException("Нет котировки"));
        var publication = quote.publishWhenReady(response);

        assertThrows(ExecutionException.class, () -> publication.get(5, SECONDS));
        assertEquals(OptionalInt.empty(), quote.current(), "По этому запросу цены не будет");
    }
}
