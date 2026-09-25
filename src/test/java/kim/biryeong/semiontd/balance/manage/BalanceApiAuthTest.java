package kim.biryeong.semiontd.balance.manage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class BalanceApiAuthTest {
    private static final String SECRET = "local-test-only-key-with-at-least-32-characters";
    private static final String TIMESTAMP = "1800000000";
    private static final String PATH = "/internal/balance/v1/fields?domain=tower";
    private static final String ACTOR = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"id\":\"github:123\",\"kind\":\"user\",\"scopes\":[\"server:test\",\"balance:read\",\"domain:tower\"]}"
                    .getBytes(StandardCharsets.UTF_8));

    private BalanceApiAuth auth() {
        return new BalanceApiAuth("test", SECRET,
                Clock.fixed(Instant.ofEpochSecond(Long.parseLong(TIMESTAMP)), ZoneOffset.UTC));
    }

    @Test
    void signedActorAndScopesAreAuthenticatedAndNonceCannotReplay() throws Exception {
        var auth = auth();
        String nonce = UUID.randomUUID().toString();
        String signature = sign(TIMESTAMP, nonce, PATH, ACTOR, new byte[0]);
        var actor = auth.authenticate(TIMESTAMP, nonce, "test", "GET", PATH, ACTOR, signature, new byte[0]);
        assertEquals("github:123", actor.id());
        assertTrue(actor.allows("balance:read"));
        assertEquals("balance:read domain:tower server:test", actor.scopeKey());
        assertEquals(409, assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                TIMESTAMP, nonce, "test", "GET", PATH, ACTOR, signature, new byte[0])).status());
    }

    @Test
    void signatureBindsBodyQueryServerAndActorAndRejectsExpiredRequests() throws Exception {
        String nonce = UUID.randomUUID().toString();
        String signature = sign(TIMESTAMP, nonce, PATH, ACTOR, new byte[0]);
        var auth = auth();
        for (String path : new String[] { PATH + "&limit=1000", "/internal/balance/v1/state" }) {
            assertEquals(401, assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                    TIMESTAMP, nonce, "test", "GET", path, ACTOR, signature, new byte[0])).status());
        }
        assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                TIMESTAMP, nonce, "production", "GET", PATH, ACTOR, signature, new byte[0]));
        assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                TIMESTAMP, nonce, "test", "GET", PATH, ACTOR, signature, "{}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                TIMESTAMP, nonce, "test", "GET", PATH, ACTOR + "x", signature, new byte[0]));
        String expired = Long.toString(Long.parseLong(TIMESTAMP) - 31);
        String expiredSignature = sign(expired, nonce, PATH, ACTOR, new byte[0]);
        assertEquals("expired_signature", assertThrows(BalanceApiAuth.Denied.class, () -> auth.authenticate(
                expired, nonce, "test", "GET", PATH, ACTOR, expiredSignature, new byte[0])).getMessage());
        // Failed tampering must not consume the valid nonce.
        assertEquals("github:123", auth.authenticate(TIMESTAMP, nonce, "test", "GET", PATH, ACTOR, signature, new byte[0]).id());
    }

    @Test
    void evenSignedMalformedActorCannotBeUsed() throws Exception {
        String actor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"id\":\"forged\",\"kind\":\"user\",\"scopes\":[\"server:other\"]}".getBytes(StandardCharsets.UTF_8));
        String nonce = UUID.randomUUID().toString();
        String signature = sign(TIMESTAMP, nonce, PATH, actor, new byte[0]);
        assertEquals(403, assertThrows(BalanceApiAuth.Denied.class, () -> auth().authenticate(
                TIMESTAMP, nonce, "test", "GET", PATH, actor, signature, new byte[0])).status());
        assertThrows(IllegalArgumentException.class, () -> new BalanceApiAuth("test", "short", Clock.systemUTC()));
    }

    private static String sign(String timestamp, String nonce, String path, String actor, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "\n" + nonce + "\ntest\nGET\n" + path + "\n"
                + actor + "\n" + BalanceApiAuth.sha256(body)).getBytes(StandardCharsets.UTF_8)));
    }
}
