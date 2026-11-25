package academy.backend.market_pulse.practice.quota;

import java.util.concurrent.Semaphore;

final class QuoteQuota {
    private final Semaphore slots;

    QuoteQuota(Semaphore slots) {
        this.slots = slots;
    }

    void refresh(Runnable request) throws InterruptedException {
        slots.acquire();
        // TODO: сохранить свободное место для следующего запроса при любом исходе текущего.
        request.run();
        slots.release();
    }
}
