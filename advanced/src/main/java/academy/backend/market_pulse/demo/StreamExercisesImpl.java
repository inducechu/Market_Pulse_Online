package academy.backend.market_pulse.demo;

import academy.backend.market_pulse.dictionary.InstrumentType;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.model.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;

public class StreamExercisesImpl implements StreamExercises {

    @Override
    public List<String> tickersInCurrency(List<Quote> quotes, Currency currency) {
        return quotes.stream()
                .filter(it -> it.getInstrument().getCurrency() == currency)
                .map(it -> it.getInstrument().getTicker())
                .toList();
    }

    @Override
    public List<Quote> topByPrice(List<Quote> quotes, int n) {
        return quotes.stream()
                .sorted(Comparator.comparing(Quote::getPrice, Comparator.reverseOrder()))
                .limit(n)
                .toList();
    }

    @Override
    public Set<Currency> distinctCurrencies(List<Quote> quotes) {
        return quotes.stream()
                .map(it -> it.getInstrument().getCurrency())
//                .distinct() А точно тут нужен distinct, если можно сразу в Set все положить?
                .collect(Collectors.toSet());
    }

    @Override
    public boolean hasAnyLoser(List<Quote> quotes) {
        return quotes.stream()
                .anyMatch(it -> it.getChangePercent().compareTo(BigDecimal.ZERO) < 0);
    }

    @Override
    public long countByType(List<Quote> quotes, InstrumentType instrumentType) {
        return quotes.stream()
                .filter(it -> it.getInstrument().getType() == instrumentType)
                .count();
    }

    @Override
    public BigDecimal totalPrice(List<Quote> quotes) {
        return quotes.stream()
                .map(Quote::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public double averageChangePercent(List<Quote> quotes) {
        return quotes.stream()
                .mapToDouble(it -> it.getChangePercent().doubleValue())
                .average()
                .orElse(0.0);
    }

    @Override
    public Map<InstrumentType, Long> countByTypeGrouped(List<Quote> quotes) {
        return quotes.stream()
                .collect(
                        Collectors.groupingBy(it ->
                                        it.getInstrument().getType(),
                                Collectors.counting()
                        )
                );
    }

    @Override
    public Map<InstrumentType, Double> averageChangeByType(List<Quote> quotes) {
        return quotes.stream()
                .collect(
                        Collectors.groupingBy(it ->
                                        it.getInstrument().getType(),
                                Collectors.averagingDouble(
                                        it -> it.getChangePercent().doubleValue()
                                )
                        )
                );
    }

    @Override
    public Map<Boolean, List<Quote>> partitionByGain(List<Quote> quotes) {
        return quotes.stream()
                .collect(
                        Collectors.partitioningBy(it ->
                                it.getChangePercent().compareTo(BigDecimal.ZERO) > 0
                        )
                );
    }

    @Override
    public Map<String, Quote> latestByTicker(List<Quote> quotes) {
        return quotes.stream()
                .collect(Collectors.toMap(it ->
                                it.getInstrument().getTicker(),
                        it -> it,
                        (q1, q2) -> q1.getPrice().compareTo(q2.getPrice()) > 0 ? q1 : q2
                ));
    }

    @Override
    public String joinTickers(List<Quote> quotes) {
        return quotes.stream()
                .map(it -> it.getInstrument().getTicker())
                .collect(
                        Collectors.joining(", ", "[", "]")
                );
    }

    @Override
    public Map<Currency, String> tickersJoinedByCurrency(List<Quote> quotes) {
        return quotes.stream()
                .collect(
                        Collectors.groupingBy(it ->
                                        it.getInstrument().getCurrency(),
                                Collectors.mapping(it ->
                                                it.getInstrument().getTicker(),
                                        Collectors.joining(", ")
                                )
                        )
                );
    }

    @Override
    public List<Quote> findByTickers(List<Quote> quotes, List<String> tickers) {
        return tickers.stream()
                .map(ticker -> quotes.stream()
                        .filter(it -> it.getInstrument().getTicker().equals(ticker))
                        .findFirst())
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<BigDecimal> movingAveragePrice(List<Quote> quotes, int windowSize) {
        return quotes.stream()
                .gather(Gatherers.windowSliding(windowSize))
                .map(it -> it.stream()
                        .map(Quote::getPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(windowSize), 2, RoundingMode.HALF_UP)
                )
                .toList();
    }

//    @Override
//    public int longestLossStreak(List<Quote> quotes) {
//        var max = 0;
//        var current = 0;
//
//        for(Quote quote: quotes) {
//            var changePercent = quote.getChangePercent();
//            if (changePercent.signum() < 0) current++;
//            else current = 0;
//
//            max = Math.max(max, current);
//        }
//
//        return max;
//
//        Just KISS
//
//    }

    @Override
    public int longestLossStreak(List<Quote> quotes) {
        return quotes.stream()
                .gather(lossStreaks())
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    private static Gatherer<Quote, ?, Integer> lossStreaks() {
        class Streak { int current; }

        return Gatherer.ofSequential(
                Streak::new,
                Gatherer.Integrator.ofGreedy((state, quote, downstream) -> {
                    if (quote.getChangePercent().signum() < 0) {
                        state.current++;
                        return true;
                    }
                    if (state.current > 0) {
                        int finished = state.current;
                        state.current = 0;
                        return downstream.push(finished);
                    }
                    return true;
                }),
                (state, downstream) -> {
                    if (state.current > 0) {
                        downstream.push(state.current);
                    }
                });
    }
}
