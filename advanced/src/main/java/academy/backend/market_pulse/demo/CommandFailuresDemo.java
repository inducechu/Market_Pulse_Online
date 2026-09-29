package academy.backend.market_pulse.demo;

import academy.backend.market_pulse.cli.AddCommand;
import academy.backend.market_pulse.cli.ListCommand;
import academy.backend.market_pulse.cli.MarketPulseCli;
import academy.backend.market_pulse.model.Currency;
import academy.backend.market_pulse.model.Stock;
import academy.backend.market_pulse.repository.InMemoryInstrumentRepository;
import academy.backend.market_pulse.repository.InstrumentRepository;
import lombok.SneakyThrows;
import picocli.CommandLine;

import java.math.BigDecimal;

/**
 * Запускает сценарии, которые сегодня падают необработанным исключением прямо в консоль —
 * {@code AddCommand.call()} пока ничего не ловит. Первые два — через CLI-команды, picocli сам
 * печатает стектрейс и возвращает код ошибки (см. {@link CommandLine#execute}), так что
 * демонстрация не требует собственной обработки. Третий — тот же необработанный
 * {@code IllegalArgumentException} из валидации модели, но уже вне picocli.
 */
public class CommandFailuresDemo {

    @SneakyThrows
    public static void main(String[] args) {
        InstrumentRepository repository = new InMemoryInstrumentRepository();
        CommandLine cli = new CommandLine(new MarketPulseCli())
                .addSubcommand(new AddCommand(repository))
                .addSubcommand(new ListCommand(repository));

        System.out.println("=== Неподдерживаемыая комманда ===");
        cli.execute("test");
        Thread.sleep(1000);
        System.out.println();

        System.out.println("=== Невалидный инструмент: пустой тикер ===");
        cli.execute("add", "STOCK", "", "Без тикера", "RUB");
        Thread.sleep(1000);
        System.out.println();

        System.out.println("=== Повторное добавление уже существующего тикера ===");
        // SBER уже есть в стартовых данных InMemoryInstrumentRepository — findByTicker(...) найдёт
        // его, но AddCommand ещё не ловит DuplicateTickerException.
        cli.execute("add", "STOCK", "SBER", "Сбербанк", "RUB");
        Thread.sleep(1000);
        System.out.println();

        System.out.println("=== Невалидный инструмент: создание напрямую, в обход CLI ===");
        // Через add так не получится: StockFactory сама подставляет sector/dividendYield по
        // умолчанию, CLI их вообще не спрашивает. Здесь создаём Stock напрямую — валидация в
        // конструкторе модели точно так же не перехвачена, как и первые два случая, и её
        // IllegalArgumentException завершает программу.
        new Stock("CRYPTO", "Крипта", Currency.RUB, "", BigDecimal.ZERO);
    }
}
