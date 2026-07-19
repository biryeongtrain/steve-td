package kim.biryeong.semiontd.balance;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancePatchNotifierTest {
    private static final String FIRST_SHA = "1111111111111111111111111111111111111111";
    private static final String SECOND_SHA = "2222222222222222222222222222222222222222";

    @TempDir
    Path temporaryDirectory;

    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private HttpServer server;
    private URI endpoint;
    private Path statePath;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/latest", this::handleRequest);
        server.start();
        endpoint = URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/latest");
        statePath = temporaryDirectory.resolve("balance_notification_state.json");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void firstFetchStoresShaWithoutNotification() throws IOException {
        responseBody.set(response(FIRST_SHA, "밸런스: 첫 변경"));
        BalancePatchNotifier notifier = new BalancePatchNotifier(statePath, endpoint);

        assertTrue(notifier.checkForUpdate().isEmpty());
        assertEquals(FIRST_SHA, storedSha());
        assertTrue(notifier.checkForUpdate().isEmpty());
    }

    @Test
    void newShaNotifiesOnceAndLimitsChangePreview() {
        responseBody.set(response(FIRST_SHA, "밸런스: 첫 변경"));
        BalancePatchNotifier notifier = new BalancePatchNotifier(statePath, endpoint);
        assertTrue(notifier.checkForUpdate().isEmpty());

        responseBody.set(response(SECOND_SHA, """
                밸런스: 설정 4개 변경

                tower_balance.json

                - 첫 번째 변경
                - 두 번째 변경
                - 세 번째 변경
                - 네 번째 변경
                """));
        BalancePatchNotifier.BalancePatch patch = notifier.checkForUpdate().orElseThrow();

        assertEquals("밸런스: 설정 4개 변경", patch.title());
        assertEquals(
                java.util.List.of("첫 번째 변경", "두 번째 변경", "세 번째 변경"),
                patch.previewChanges()
        );
        assertEquals(1, patch.remainingChangeCount());
        assertTrue(notifier.checkForUpdate().isEmpty());
    }

    @Test
    void titleOnlyCommitStillProvidesLink() {
        responseBody.set(response(FIRST_SHA, "밸런스: 첫 변경"));
        BalancePatchNotifier notifier = new BalancePatchNotifier(statePath, endpoint);
        assertTrue(notifier.checkForUpdate().isEmpty());

        responseBody.set(response(SECOND_SHA, "본문 없는 밸런스 패치"));
        BalancePatchNotifier.BalancePatch patch = notifier.checkForUpdate().orElseThrow();

        assertEquals("본문 없는 밸런스 패치", patch.title());
        assertTrue(patch.changes().isEmpty());
        assertEquals(
                URI.create("https://github.com/biryeongtrain/semiontd-balance/commit/" + SECOND_SHA),
                patch.commitUrl()
        );
    }

    @Test
    void failedAndInvalidResponsesKeepPreviousState() throws IOException {
        responseBody.set(response(FIRST_SHA, "밸런스: 첫 변경"));
        BalancePatchNotifier notifier = new BalancePatchNotifier(statePath, endpoint);
        assertTrue(notifier.checkForUpdate().isEmpty());

        statusCode.set(500);
        responseBody.set("server error");
        assertTrue(notifier.checkForUpdate().isEmpty());
        assertEquals(FIRST_SHA, storedSha());

        statusCode.set(200);
        responseBody.set("{\"sha\":");
        assertTrue(notifier.checkForUpdate().isEmpty());
        assertEquals(FIRST_SHA, storedSha());

        responseBody.set("{\"sha\":{\"unexpected\":true}}");
        assertTrue(notifier.checkForUpdate().isEmpty());
        assertEquals(FIRST_SHA, storedSha());
        assertFalse(Files.exists(statePath.resolveSibling(statePath.getFileName() + ".tmp")));
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode.get(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String storedSha() throws IOException {
        return JsonParser.parseString(Files.readString(statePath))
                .getAsJsonObject()
                .get("lastSeenSha")
                .getAsString();
    }

    private static String response(String sha, String message) {
        String escapedMessage = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        return """
                {
                  "sha": "%s",
                  "html_url": "https://github.com/biryeongtrain/semiontd-balance/commit/%s",
                  "commit": {
                    "message": "%s"
                  }
                }
                """.formatted(sha, sha, escapedMessage);
    }
}
