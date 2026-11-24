package academy.backend.market_pulse.portfolio;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Удерживает HTTP-ответ, пока тест не разрешит его отправку.
 */
final class ResponseGate implements ResponseDefinitionTransformerV2 {
    private final String name;
    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch release;

    ResponseGate(String name, boolean hold) {
        this.name = name;
        this.release = new CountDownLatch(hold ? 1 : 0);
    }

    boolean awaitRequest() throws InterruptedException {
        return arrived.await(5, TimeUnit.SECONDS);
    }

    void open() {
        release.countDown();
    }

    @Override
    public ResponseDefinition transform(ServeEvent event) {
        System.out.println("server received " + event.getRequest().getUrl() + " gate=" + name);
        arrived.countDown();
        try {
            if (!release.await(7, TimeUnit.SECONDS)) {
                return new ResponseDefinition(500, "Не дождались освобождения ответа");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return new ResponseDefinition(500, "Не дождались освобождения ответа");
        }
        System.out.println("server releases " + name);
        return event.getResponseDefinition();
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }
}
