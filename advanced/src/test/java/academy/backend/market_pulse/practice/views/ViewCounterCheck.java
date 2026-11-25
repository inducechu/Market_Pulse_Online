package academy.backend.market_pulse.practice.views;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewCounterCheck {
    @Test
    void countsBothViewsWhenSecondCardFinishesFirst() throws Exception {
        var counter = new ViewCounter();
        var firstScreenStarted = new CompletableFuture<Void>();
        var finishFirstScreen = new CompletableFuture<Void>();

        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstView = workers.submit(() -> counter.openCard(() -> {
                firstScreenStarted.complete(null);
                finishFirstScreen.join();
            }));
            try {
                firstScreenStarted.get(5, SECONDS);
                var secondView = workers.submit(() -> counter.openCard(() -> {}));
                secondView.get(5, SECONDS);
                assertEquals(1, counter.total(), "Пока открыт только второй экран");
            } finally {
                finishFirstScreen.complete(null);
            }
            firstView.get(5, SECONDS);
        }

        assertEquals(2, counter.total(), "Два пользователя увидели карточку");
    }

    @Test
    void failedScreenDoesNotCountAsAView() {
        var counter = new ViewCounter();
        assertThrows(IllegalStateException.class, () -> counter.openCard(() -> {
            throw new IllegalStateException("Не удалось показать карточку");
        }));
        assertEquals(0, counter.total());
    }
}
