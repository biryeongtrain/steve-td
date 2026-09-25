package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticates the private web-to-game hop; never accepts unsigned actor headers. */
public final class BalanceApiAuth {
    private static final int MAX_NONCES = 4096;
    private static final long WINDOW_SECONDS = 30;
    private final String serverId;
    private final byte[] secret;
    private final Clock clock;
    private final Map<String, Long> nonces = new LinkedHashMap<>();

    public record Actor(String id, String kind, Set<String> scopes) {
        public Actor { scopes = Set.copyOf(scopes); }
        public boolean allows(String scope) { return scopes.contains(scope); }
        public String scopeKey() { return String.join(" ", new TreeSet<>(scopes)); }
    }

    public static final class Denied extends RuntimeException {
        private final int status;
        public Denied(int status, String code) { super(code); this.status = status; }
        public int status() { return status; }
    }

    public BalanceApiAuth(String serverId, String secret, Clock clock) {
        if (serverId == null || !serverId.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid balance server ID");
        }
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Balance API secret requires at least 32 characters");
        }
        this.serverId = serverId;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public synchronized Actor authenticate(String timestamp, String nonce, String requestedServer,
                                            String method, String rawPath, String encodedActor,
                                            String signature, byte[] body) {
        if (!serverId.equals(requestedServer) || timestamp == null || !timestamp.matches("[0-9]{1,12}")
                || nonce == null || !nonce.matches("[a-fA-F0-9-]{36}")
                || encodedActor == null || !encodedActor.matches("[A-Za-z0-9_-]{1,4096}")
                || signature == null || !signature.matches("[a-fA-F0-9]{64}")) {
            throw new Denied(401, "invalid_signature");
        }
        long now = clock.instant().getEpochSecond();
        long sent = Long.parseLong(timestamp);
        if (Math.abs(now - sent) > WINDOW_SECONDS) { throw new Denied(401, "expired_signature"); }
        try { UUID.fromString(nonce); }
        catch (IllegalArgumentException exception) { throw new Denied(401, "invalid_signature"); }
        String canonical = timestamp + "\n" + nonce + "\n" + serverId + "\n" + method
                + "\n" + rawPath + "\n" + encodedActor + "\n" + sha256(body);
        if (!MessageDigest.isEqual(hmac(canonical), HexFormat.of().parseHex(signature))) {
            throw new Denied(401, "invalid_signature");
        }
        Actor actor = parseActor(encodedActor);
        if (!actor.allows("server:" + serverId)) { throw new Denied(403, "server_forbidden"); }
        nonces.entrySet().removeIf(entry -> entry.getValue() < now);
        if (nonces.containsKey(nonce)) { throw new Denied(409, "signature_replayed"); }
        if (nonces.size() >= MAX_NONCES) { throw new Denied(429, "signature_capacity"); }
        // Future-dated signatures remain replay protected until their final accepted second.
        nonces.put(nonce, sent + WINDOW_SECONDS);
        return actor;
    }

    private static Actor parseActor(String encoded) {
        try {
            JsonObject json = JsonParser.parseString(new String(Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.keySet().equals(Set.of("id", "kind", "scopes"))) { throw new IllegalArgumentException(); }
            if (!json.get("id").isJsonPrimitive() || !json.getAsJsonPrimitive("id").isString()
                    || !json.get("kind").isJsonPrimitive() || !json.getAsJsonPrimitive("kind").isString()) {
                throw new IllegalArgumentException();
            }
            String id = json.get("id").getAsString();
            String kind = json.get("kind").getAsString();
            if (id.isBlank() || id.length() > 128 || id.chars().anyMatch(Character::isISOControl)
                    || !Set.of("user", "machine").contains(kind)) { throw new IllegalArgumentException(); }
            var values = json.getAsJsonArray("scopes");
            if (values.isEmpty() || values.size() > 64) { throw new IllegalArgumentException(); }
            Set<String> scopes = new TreeSet<>();
            for (var value : values) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) { throw new IllegalArgumentException(); }
                String scope = value.getAsString();
                if (!scope.matches("[a-zA-Z][a-zA-Z0-9_-]*:[a-zA-Z0-9_-]+")) { throw new IllegalArgumentException(); }
                scopes.add(scope);
            }
            return new Actor(id, kind, scopes);
        } catch (RuntimeException exception) {
            throw new Denied(401, "invalid_actor");
        }
    }

    private byte[] hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC unavailable", exception);
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
