package academy.backend.market_pulse.portfolio;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static academy.backend.market_pulse.model.Currency.RUB;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет поступление второго запроса при удержанном первом ответе и одном carrier thread.
 */
class OneCarrierTest {
    @Test
    void secondPortfolioReachesRealSocketWhileFirstResponseIsHeld() throws Exception {
        var p1 = new ResponseGate("P1", true);
        var p2 = new ResponseGate("P2", false);
        var server = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort().extensions(p1, p2));
        server.start();
        try {
            PortfolioMock.standard(server);
            PortfolioMock.stubPositions(server, "P1", okJson(PortfolioMock.POSITIONS).withTransformers(p1.getName()));
            PortfolioMock.stubPositions(server, "P2", okJson(PortfolioMock.POSITIONS).withTransformers(p2.getName()));
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            Process child = new ProcessBuilder(java, "-Djdk.virtualThreadScheduler.parallelism=1",
                    "-Djdk.virtualThreadScheduler.maxPoolSize=1", "-cp", classpath,
                    OneCarrierClient.class.getName(), server.baseUrl()).redirectErrorStream(true).start();

            var output = new FutureTask<>(() -> new String(child.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8));
            Thread.ofPlatform().daemon().start(output);
            try {
                assertThat(p1.awaitRequest()).as("P1 уже ожидает сетевой ответ").isTrue();
                try (var stdin = child.getOutputStream()) {
                    stdin.write("start P2\n".getBytes(StandardCharsets.UTF_8));
                }
                assertThat(p2.awaitRequest()).as("P2 достиг сервера до освобождения ответа P1").isTrue();
                System.out.println("P2 observed while P1 response is held");
                p1.open();

                assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
                String trace = output.get(2, TimeUnit.SECONDS);
                System.out.print(trace);
                assertThat(child.exitValue()).as(trace).isZero();
                assertThat(trace).contains("parallelism=1 poolSize=1", "P1=2100", "P2=2100");
            } finally {
                p1.open();
                if (child.isAlive()) {
                    child.destroyForcibly().waitFor(3, TimeUnit.SECONDS);
                }
            }
        } finally {
            server.stop();
        }
    }
}
