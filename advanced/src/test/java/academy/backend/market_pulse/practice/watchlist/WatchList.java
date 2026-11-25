package academy.backend.market_pulse.practice.watchlist;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class WatchList {
    // TODO: подобрать коллекцию для частых обходов и редких изменений подписок.
    private final Set<String> tickers = new LinkedHashSet<>();

    void subscribe(String ticker) {
        tickers.add(ticker);
    }

    void unsubscribe(String ticker) {
        tickers.remove(ticker);
    }

    Iterator<String> startRefresh() {
        return tickers.iterator();
    }
}
