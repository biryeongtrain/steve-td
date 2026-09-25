package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import kim.biryeong.semiontd.balance.manage.BalanceApiAuth.Actor;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;

/** Loopback-only transport. The authenticated web server is the sole external entry point. */
public final class BalanceHttpServer implements AutoCloseable {
    public static final String PREFIX = "/internal/balance/v1";
    static final Set<String> DOMAINS = Set.of("tower", "augment", "wave", "summon", "trait", "economy", "monsterScaling");
    private static final int MAX_BODY = 262_144;
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final BalanceChangeService service;
    private final BalanceApiAuth auth;

    public BalanceHttpServer(int port, BalanceChangeService service, BalanceApiAuth auth) throws IOException {
        this.service = service;
        this.auth = auth;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "semion-balance-http");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext(PREFIX, this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            try {
                byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
                if (bytes.length > MAX_BODY) { throw new BalanceException(413, "요청이 너무 큽니다."); }
                String rawPath = exchange.getRequestURI().getRawPath();
                if (exchange.getRequestURI().getRawQuery() != null) { rawPath += "?" + exchange.getRequestURI().getRawQuery(); }
                var headers = exchange.getRequestHeaders();
                Actor actor = auth.authenticate(singleHeader(exchange, "X-Balance-Timestamp"),
                        singleHeader(exchange, "X-Balance-Nonce"), singleHeader(exchange, "X-Balance-Server"),
                        exchange.getRequestMethod(), rawPath, singleHeader(exchange, "X-Balance-Actor"),
                        singleHeader(exchange, "X-Balance-Signature"), bytes);
                if (bytes.length > 0 && !"application/json".equalsIgnoreCase(
                        headers.getFirst("Content-Type") == null ? "" : headers.getFirst("Content-Type").split(";", 2)[0].trim())) {
                    throw new BalanceException(415, "JSON 요청이 필요합니다.");
                }
                Object response = route(exchange, actor, bytes);
                respond(exchange, 200, response);
            } catch (BalanceApiAuth.Denied exception) {
                respond(exchange, exception.status(), Map.of("error", exception.getMessage()));
            } catch (BalanceException exception) {
                respond(exchange, exception.status(), Map.of("error", exception.getMessage()));
            } catch (IllegalArgumentException | com.google.gson.JsonParseException exception) {
                respond(exchange, 400, Map.of("error", "잘못된 요청입니다."));
            } catch (RuntimeException exception) {
                respond(exchange, 500, Map.of("error", "밸런스 요청을 처리하지 못했습니다. 요청 상태를 확인하세요."));
            }
        }
    }

    private Object route(HttpExchange exchange, Actor actor, byte[] bytes) {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        if (!path.startsWith(PREFIX + "/")) { throw new BalanceException(404, "경로가 없습니다."); }
        String route = path.substring(PREFIX.length());
        if (method.equals("GET")) {
            require(actor, "balance:read");
            if (bytes.length != 0) { throw new BalanceException(400, "GET 본문은 지원하지 않습니다."); }
            if (route.equals("/state")) {
                BalanceState state = service.state();
                return state.pending() == null || visible(actor, state.pending()) ? state
                        : new BalanceState(state.serverId(), state.online(), state.revision(), state.catalogVersion(),
                        state.game(), null, state.updatedAt(), "권한 범위 밖의 예약이 있습니다.", state.fieldsVersion());
            }
            if (route.equals("/fields")) { return fields(exchange, actor); }
            if (route.equals("/history")) {
                return Map.of("deployments", service.history().stream().filter(value -> visible(actor, value)).toList());
            }
            if (route.startsWith("/revisions/")) {
                requireAllDomains(actor);
                String revision = route.substring("/revisions/".length());
                if (!revision.matches("[a-f0-9]{64}")) { throw new BalanceException(400, "잘못된 버전입니다."); }
                return service.revision(revision).toJson();
            }
            if (route.startsWith("/deployments/")) {
                BalanceDeployment deployment = service.deployment(uuid(route.substring("/deployments/".length())));
                requireVisible(actor, deployment);
                return deployment;
            }
        }
        if (method.equals("DELETE") && route.startsWith("/deployments/")) {
            require(actor, "balance:apply");
            if (bytes.length != 0) { throw new BalanceException(400, "취소 요청 본문은 지원하지 않습니다."); }
            String id = uuid(route.substring("/deployments/".length()));
            BalanceDeployment deployment = service.deployment(id);
            requireVisible(actor, deployment);
            require(actor, "mode:" + deployment.applyMode());
            return service.cancel(id, actor.id());
        }
        if (method.equals("POST")) {
            JsonObject body = object(bytes);
            if (route.equals("/validations") || route.equals("/deployments")) {
                boolean deploy = route.equals("/deployments");
                require(actor, deploy ? "balance:apply" : "balance:edit");
                BalancePatch patch = patch(body, deploy);
                require(actor, "mode:" + patch.applyMode());
                for (BalanceChange change : patch.changes()) { requireField(actor, change.fieldId()); }
                if (!deploy) { return service.validate(patch, actor.id(), actor.scopeKey()); }
                String key = requestKey(exchange, body);
                return service.submit(key, patch, string(body, "validationHash"), actor.id(), actor.kind(), actor.scopeKey());
            }
            if (route.equals("/rollbacks")) {
                require(actor, "balance:apply");
                requireAllDomains(actor);
                keys(body, Set.of("revision", "baseRevision", "applyMode", "reason", "sourceReference", "requestId", "notifyPlayers"));
                ApplyMode mode = ApplyMode.valueOf(string(body, "applyMode"));
                require(actor, "mode:" + mode);
                return service.rollback(requestKey(exchange, body), string(body, "revision"), string(body, "baseRevision"),
                        mode, string(body, "reason"), actor.id(), actor.kind(), actor.scopeKey(), notifyPlayers(body));
            }
        }
        throw new BalanceException(404, "경로가 없습니다.");
    }

    private Object fields(HttpExchange exchange, Actor actor) {
        Map<String, String> query = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw != null) {
            if (raw.length() > 2048) { throw new BalanceException(400, "검색 조건이 너무 깁니다."); }
            for (String part : raw.split("&")) {
                String[] pair = part.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                if (!Set.of("domain", "builderId", "tier", "rarity", "q", "cursor", "limit").contains(key)
                        || query.put(key, pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "") != null) {
                    throw new BalanceException(400, "잘못된 검색 조건입니다.");
                }
            }
        }
        String domain = query.get("domain");
        if (domain != null) { require(actor, "domain:" + domain); }
        String q = query.getOrDefault("q", "").toLowerCase(java.util.Locale.ROOT);
        var listing = service.fieldListing();
        List<BalanceField> filtered = listing.fields().stream()
                .filter(field -> actor.allows("domain:" + field.domain()))
                .filter(field -> domain == null || field.domain().equals(domain))
                .filter(field -> !query.containsKey("builderId") || query.get("builderId").equals(field.builderId()))
                .filter(field -> !query.containsKey("tier") || query.get("tier").equals(String.valueOf(field.tier())))
                .filter(field -> !query.containsKey("rarity") || query.get("rarity").equals(field.rarity()))
                .filter(field -> q.isEmpty() || (field.id() + " " + field.label() + " " + field.entityName())
                        .toLowerCase(java.util.Locale.ROOT).contains(q)).toList();
        int offset = Integer.parseInt(query.getOrDefault("cursor", "0"));
        int limit = Integer.parseInt(query.getOrDefault("limit", "200"));
        if (offset < 0 || offset > filtered.size() || limit < 1 || limit > 500) {
            throw new BalanceException(400, "잘못된 페이지 범위입니다.");
        }
        int end = Math.min(filtered.size(), offset + limit);
        Map<String, Object> result = new HashMap<>();
        result.put("fields", filtered.subList(offset, end));
        result.put("revision", listing.revision());
        result.put("fieldsVersion", listing.fieldsVersion());
        result.put("total", filtered.size());
        result.put("nextCursor", end < filtered.size() ? String.valueOf(end) : null);
        return result;
    }

    private static BalancePatch patch(JsonObject body, boolean deploy) {
        Set<String> allowed = new java.util.HashSet<>(Set.of("baseRevision", "applyMode", "reason", "sourceReference", "changes", "notifyPlayers"));
        if (deploy) { allowed.addAll(Set.of("validationHash", "requestId")); }
        keys(body, allowed);
        if (!body.has("changes") || !body.get("changes").isJsonArray()
                || body.getAsJsonArray("changes").isEmpty() || body.getAsJsonArray("changes").size() > 1000) {
            throw new BalanceException(422, "변경 항목은 1개 이상 1000개 이하여야 합니다.");
        }
        List<BalanceChange> changes = new ArrayList<>();
        for (JsonElement element : body.getAsJsonArray("changes")) {
            if (!element.isJsonObject()) { throw new BalanceException(400, "잘못된 변경 항목입니다."); }
            JsonObject change = element.getAsJsonObject();
            keys(change, Set.of("fieldId", "expectedValue", "value"));
            changes.add(new BalanceChange(string(change, "fieldId"), number(change, "expectedValue"), number(change, "value")));
        }
        return new BalancePatch(string(body, "baseRevision"), ApplyMode.valueOf(string(body, "applyMode")),
                string(body, "reason"), body.has("sourceReference") && !body.get("sourceReference").isJsonNull()
                ? string(body, "sourceReference") : null, changes, notifyPlayers(body));
    }

    private static Boolean notifyPlayers(JsonObject body) {
        if (!body.has("notifyPlayers")) {return null;}
        if (!body.get("notifyPlayers").isJsonPrimitive() || !body.getAsJsonPrimitive("notifyPlayers").isBoolean()) {
            throw new BalanceException(400, "notifyPlayers는 참 또는 거짓이어야 합니다.");
        }
        return body.get("notifyPlayers").getAsBoolean();
    }

    private static JsonObject object(byte[] bytes) {
        JsonElement value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!value.isJsonObject()) { throw new BalanceException(400, "JSON 객체가 필요합니다."); }
        return value.getAsJsonObject();
    }
    private static void keys(JsonObject value, Set<String> allowed) {
        if (!allowed.containsAll(value.keySet())) { throw new BalanceException(400, "알 수 없는 요청 필드입니다."); }
    }
    private static String string(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isString()) {
            throw new BalanceException(400, "문자열 필드가 필요합니다: " + key);
        }
        return value.get(key).getAsString();
    }
    private static double number(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonPrimitive() || !value.getAsJsonPrimitive(key).isNumber()
                || !Double.isFinite(value.get(key).getAsDouble())) { throw new BalanceException(400, "유한한 숫자가 필요합니다."); }
        return value.get(key).getAsDouble();
    }
    private static String uuid(String value) {
        if (value == null || !value.matches("[a-fA-F0-9-]{36}")) { throw new BalanceException(400, "요청 ID는 UUID여야 합니다."); }
        UUID.fromString(value);
        return value;
    }
    private static String requestKey(HttpExchange exchange, JsonObject body) {
        String id = uuid(string(body, "requestId"));
        if (!id.equals(singleHeader(exchange, "Idempotency-Key"))) { throw new BalanceException(400, "요청 ID와 중복 방지 키가 다릅니다."); }
        return id;
    }
    private static String singleHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        return values != null && values.size() == 1 ? values.getFirst() : null;
    }
    private static void require(Actor actor, String scope) {
        if (!actor.allows(scope)) { throw new BalanceException(403, "권한 범위 밖의 요청입니다."); }
    }
    private static void requireField(Actor actor, String fieldId) {
        int colon = fieldId.indexOf(":/");
        if (colon < 1 || !DOMAINS.contains(fieldId.substring(0, colon))) { throw new BalanceException(422, "잘못된 필드 ID입니다."); }
        require(actor, "domain:" + fieldId.substring(0, colon));
    }
    private static boolean visible(Actor actor, BalanceDeployment deployment) {
        return deployment.changes().stream().allMatch(change -> actor.allows("domain:" + change.fieldId().split(":", 2)[0]));
    }
    private static void requireVisible(Actor actor, BalanceDeployment deployment) {
        if (!visible(actor, deployment)) { throw new BalanceException(403, "권한 범위 밖의 요청입니다."); }
    }
    private static void requireAllDomains(Actor actor) { DOMAINS.forEach(domain -> require(actor, "domain:" + domain)); }
    private static void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] json = BalanceBundle.GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, json.length);
        exchange.getResponseBody().write(json);
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
