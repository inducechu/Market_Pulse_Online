package academy.backend.market_pulse.cli;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

import academy.backend.market_pulse.exception.InstrumentSaveException;
import academy.backend.market_pulse.repository.InstrumentRepository;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Сохраняет текущий список инструментов в файл по указанному пользователем пути — первый в
 * проекте настоящий {@link AutoCloseable} ресурс (файл), а не игрушечная демонстрация.
 */
@Command(name = "save", description = "Сохранение инструментов из репозитория в файл")
@RequiredArgsConstructor
public class SaveCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Путь к файлу для сохранения")
    private String path;

    private final InstrumentRepository repository;

    // TODO №1: реализовать сохранение через try-with-resources
    // TODO №2: показать боль от связки FI <-> throws
    // TODO №3: обернуть IOException в InstrumentSaveException, а не отдавать его как есть
    // TODO №4: реализовать с помощью Vavr.Try
    @Override
    public Integer call() {
        return Try.of(this::saveToFile)
                .onSuccess((code) -> System.out.println("Успешно сохранено, код - " + code))
                .onFailure(InstrumentSaveException.class, e -> System.out.println("Ошибка сохранения: " + e.getMessage()))
                .getOrElse(1);
    }

    private int saveToFile() {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            for (var instrument : repository) {
                writer.write(instrument.toString() + "\n");
            }
        } catch (IOException e) {
            throw new InstrumentSaveException("Не удалось сохранить данные в файл");
        }

        return 0;
    }
}
