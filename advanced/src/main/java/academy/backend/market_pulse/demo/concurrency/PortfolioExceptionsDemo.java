package academy.backend.market_pulse.demo.concurrency;

import academy.backend.market_pulse.exception.PortfolioHttpStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/** Показывает исход двух независимых запросов и три способа обработать результат стейджа. */
public final class PortfolioExceptionsDemo {
    private PortfolioExceptionsDemo() {}

    public static void main(String[] args) {
        // Цену и курс задаём явно, чтобы в IDE можно было воспроизвести каждый исход.
        var price = CompletableFuture.completedFuture(10);
        var rate = CompletableFuture.completedFuture(90);
        var http = CompletableFuture.<Integer>failedFuture(new PortfolioHttpStatusException(500, "/quotes"));
        var timeout = CompletableFuture.<Integer>failedFuture(new TimeoutException("Курсы валют не ответили вовремя"));

        show("Оба запроса успешны", price, rate);
        show("Котировки вернули 500", http, rate);
        show("Истекло время ожидания курса", price, timeout);
        show("Котировки вернули 500, курсы не ответили вовремя", http, timeout);
    }

    private static void show(String scenario, CompletableFuture<Integer> quote, CompletableFuture<Integer> fx) {
        System.out.println("\n" + scenario);
        var value = quote.thenCombine(fx, (price, rate) -> {
            System.out.println("  thenCombine: считаем стоимость позиции");
            return price * rate;
        });

        // NOTICE: все три примера начинаются с одного исхода. У каждого свой зависимый стейдж.
        var observed = value.whenComplete((amount, error) ->
                System.out.println("  whenComplete: " + (error == null ? amount + " RUB" : error.getMessage())));
        var report = value.handle((amount, error) ->
                error == null ? "стоимость " + amount + " RUB" : "расчёт не выполнен");
        var display = value.thenApply(amount -> amount + " RUB").exceptionally(error -> "цена недоступна");

        System.out.println("  handle: " + report.join());
        System.out.println("  exceptionally (итог для экрана): " + display.join());
        try {
            System.out.println("  join после whenComplete: " + observed.join());
        } catch (CompletionException error) {
            // NOTICE: при двух сбоях thenCombine может сообщить любую из двух причин.
            System.out.println("  join после whenComplete: CompletionException, причина: " + error.getCause());
        }
    }
}
