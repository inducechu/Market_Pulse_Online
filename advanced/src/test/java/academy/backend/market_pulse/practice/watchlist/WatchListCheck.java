package academy.backend.market_pulse.practice.watchlist;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchListCheck {
    @Test
    void editsDuringRefreshApplyOnTheNextRefresh() {
        var watchList = new WatchList();
        watchList.subscribe("SBER");
        watchList.subscribe("GAZP");
        watchList.subscribe("SBER");
        var currentRefresh = watchList.startRefresh();

        assertEquals("SBER", currentRefresh.next());
        watchList.unsubscribe("GAZP");
        watchList.subscribe("LKOH");

        assertEquals(List.of("GAZP"), remainingTickers(currentRefresh),
                "Текущий обход должен дочитать подписки, с которыми начинался");
        assertEquals(List.of("SBER", "LKOH"), remainingTickers(watchList.startRefresh()));
    }

    private static List<String> remainingTickers(Iterator<String> refresh) {
        var tickers = new ArrayList<String>();
        refresh.forEachRemaining(tickers::add);
        return tickers;
    }
}
