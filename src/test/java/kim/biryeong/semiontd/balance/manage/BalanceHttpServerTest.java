package kim.biryeong.semiontd.balance.manage;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BalanceHttpServerTest {
    private static final String SECRET = "local-http-test-key-not-a-real-secret-12345";
    private static final List<String> SCOPES = List.of("server:test", "balance:read", "balance:edit", "balance:apply", "domain:tower", "mode:NOW");
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void signedTransportValidatesSchedulesReconcilesAndScopesReads() throws Exception {
        BalanceBundle initial = BalanceBundle.defaults();
        var announcements = new java.util.concurrent.atomic.AtomicInteger();
        var runtime = new BalanceChangeService.Runtime() {
            public BalanceChangeService.RuntimeView view() { return new BalanceChangeService.RuntimeView(null, 0, "WAITING", "test", true); }
            public String apply(BalanceBundle candidate, ApplyMode mode, String id, String revision) { return "SYNCED"; }
            public void broadcastApplied(BalanceDtos.BalanceDeployment deployment) { announcements.incrementAndGet(); }
        };
        try (var service = new BalanceChangeService(directory, "test", initial,
                new BalanceFieldRegistry(initial, null, Set.of("t1_pig_tower")), runtime);
             var server = new BalanceHttpServer(0, service, new BalanceApiAuth("test", SECRET, Clock.systemUTC()));
             var client = HttpClient.newHttpClient()) {
            server.start();
            assertEquals(401, client.send(HttpRequest.newBuilder(url(server, "/state")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            var fields = send(client, server, "GET", "/fields?domain=tower&limit=2", "", SCOPES, null);
            assertEquals(200, fields.statusCode());
            assertEquals(2, json(fields).getAsJsonArray("fields").size());
            String fieldsVersion = json(fields).get("fieldsVersion").getAsString();
            assertEquals(service.state().fieldsVersion(), fieldsVersion);
            assertEquals(fieldsVersion, json(send(client, server, "GET", "/fields?domain=tower&limit=2&cursor=2", "", SCOPES, null)).get("fieldsVersion").getAsString());
            assertEquals(403, send(client, server, "GET", "/revisions/" + initial.revision(), "", SCOPES, null).statusCode());

            JsonObject patch = new JsonObject();
            patch.addProperty("baseRevision", initial.revision());
            patch.addProperty("reason", "local integration test");
            patch.addProperty("applyMode", "NOW");
            patch.add("changes", BalanceBundle.GSON.toJsonTree(List.of(Map.of("fieldId", "tower:/towers/t1_pig_tower/damage",
                    "expectedValue", initial.tower().towers().get("t1_pig_tower").damage(), "value", 6))));
            patch.addProperty("notifyPlayers", "true");
            assertEquals(400, send(client, server, "POST", "/validations", patch.toString(), SCOPES, null).statusCode());
            patch.addProperty("notifyPlayers", true);
            var validation = send(client, server, "POST", "/validations", patch.toString(), SCOPES, null);
            assertEquals(200, validation.statusCode(), validation.body());
            String id = UUID.randomUUID().toString();
            patch.addProperty("validationHash", json(validation).get("validationHash").getAsString());
            patch.addProperty("requestId", id);
            assertEquals(400, send(client, server, "POST", "/deployments", patch.toString(), SCOPES, UUID.randomUUID().toString()).statusCode());
            var scheduled = send(client, server, "POST", "/deployments", patch.toString(), SCOPES, id);
            assertEquals(200, scheduled.statusCode(), scheduled.body());
            assertEquals(id, json(scheduled).get("requestId").getAsString());
            assertEquals("SCHEDULED", json(send(client, server, "GET", "/deployments/" + id, "", SCOPES, null)).get("state").getAsString());
            List<String> waveScope = SCOPES.stream().map(scope -> scope.equals("domain:tower") ? "domain:wave" : scope).toList();
            assertEquals(403, send(client, server, "GET", "/deployments/" + id, "", waveScope, null).statusCode());
            assertTrue(json(send(client, server, "GET", "/history", "", waveScope, null)).getAsJsonArray("deployments").isEmpty());
            assertNotNull(json(send(client, server, "GET", "/state", "", waveScope, null)).get("writeBlocked"));
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while (service.deployment(id).state() == BalanceDtos.DeploymentState.APPLYING) { Thread.sleep(5); }
            });
            var replay = send(client, server, "POST", "/deployments", patch.toString(), SCOPES, id);
            assertEquals(200, replay.statusCode());
            assertEquals("APPLIED", json(replay).get("state").getAsString());
            assertEquals(1, service.history().size());
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            assertEquals(1, announcements.get());
            assertEquals(403, send(client, server, "POST", "/validations", "{}", List.of("server:test", "balance:read"), null).statusCode());
            assertEquals(400, send(client, server, "GET", "/fields?limit=0", "", SCOPES, null).statusCode());
        }
    }

    private static URI url(BalanceHttpServer server, String path) { return URI.create("http://127.0.0.1:" + server.port() + BalanceHttpServer.PREFIX + path); }
    private static JsonObject json(HttpResponse<String> response) { return JsonParser.parseString(response.body()).getAsJsonObject(); }
    private static HttpResponse<String> send(HttpClient client, BalanceHttpServer server, String method, String path,
                                              String body, List<String> scopes, String requestId) throws Exception {
        String timestamp = String.valueOf(Clock.systemUTC().instant().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String actor = Base64.getUrlEncoder().withoutPadding().encodeToString(BalanceBundle.GSON
                .toJson(Map.of("id", "test-operator", "kind", "machine", "scopes", scopes)).getBytes(StandardCharsets.UTF_8));
        String canonical = timestamp + "\n" + nonce + "\ntest\n" + method + "\n" + BalanceHttpServer.PREFIX + path
                + "\n" + actor + "\n" + BalanceApiAuth.sha256(body.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var request = HttpRequest.newBuilder(url(server, path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").header("X-Balance-Timestamp", timestamp)
                .header("X-Balance-Nonce", nonce).header("X-Balance-Server", "test").header("X-Balance-Actor", actor)
                .header("X-Balance-Signature", HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8))))
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (requestId != null) { request.header("Idempotency-Key", requestId); }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
