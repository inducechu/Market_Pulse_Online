package academy.backend.market_pulse.practice.quota;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class QuoteQuotaCheck {
    @Test
    void failedRequestLeavesRoomForTheNextOne() throws InterruptedException {
        var slots = new Semaphore(1);
        var quota = new QuoteQuota(slots);
        var offline = new IllegalStateException("Поставщик недоступен");
        assertSame(offline, assertThrows(IllegalStateException.class, () -> quota.refresh(() -> {
            throw offline;
        })));

        assertEquals(1, slots.availablePermits(), "После ошибки следующее обновление должно получить место");
        quota.refresh(() -> assertEquals(0, slots.availablePermits(), "Запрос занял единственное место"));
    }

    @Test
    void aRequestBorrowsExactlyOneSlot() throws InterruptedException {
        var slots = new Semaphore(2);
        var quota = new QuoteQuota(slots);
        quota.refresh(() -> assertEquals(1, slots.availablePermits(), "Второе место осталось свободно"));
        assertEquals(2, slots.availablePermits(), "Запрос вернул своё разрешение");
    }

    @Test
    void interruptedAcquisitionLeavesTheQuotaUntouched() {
        var slots = new Semaphore(1);
        var quota = new QuoteQuota(slots);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> quota.refresh(() -> fail("Запрос ушёл поставщику")));
            assertEquals(1, slots.availablePermits(), "До захвата разрешение принадлежало семафору");
        } finally {
            Thread.interrupted();
        }
    }
}
