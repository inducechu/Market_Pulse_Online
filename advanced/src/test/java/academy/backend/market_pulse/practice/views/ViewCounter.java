package academy.backend.market_pulse.practice.views;

import java.util.concurrent.atomic.AtomicInteger;

final class ViewCounter {
    private final AtomicInteger views = new AtomicInteger();

    void openCard(Runnable render) {
        // TODO: учесть каждый успешно показанный экран, когда карточку открывают одновременно.
        int previous = views.get();
        render.run();
        views.set(previous + 1);
    }

    int total() {
        return views.get();
    }
}
