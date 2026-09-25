package kim.biryeong.semiontd.balance.manage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.server.MinecraftServer;

/** Opt-in listener; an existing managed store stays authoritative even with the listener disabled. */
public final class BalanceManagementBootstrap implements AutoCloseable {
    private final BalanceChangeService service;
    private final BalanceHttpServer http;

    private BalanceManagementBootstrap(BalanceChangeService service, BalanceHttpServer http) {
        this.service = service;
        this.http = http;
    }

    public static BalanceManagementBootstrap start(MinecraftServer server, SemionGameManager manager, Path configDir) {
        Map<String, String> env = System.getenv();
        boolean enabled = "true".equalsIgnoreCase(env.get("SEMION_BALANCE_API_ENABLED"));
        Path directory = configDir.resolve("balance-management");
        if (!enabled && !Files.exists(directory)) { return null; }
        String serverId = env.getOrDefault("SEMION_BALANCE_SERVER_ID", "local");
        BalanceApiAuth auth = enabled ? new BalanceApiAuth(serverId, env.get("SEMION_BALANCE_API_SECRET"), Clock.systemUTC()) : null;
        int port = enabled ? Integer.parseInt(env.getOrDefault("SEMION_BALANCE_API_PORT", "8091")) : 0;
        if (enabled && (port < 1024 || port > 65535)) { throw new IllegalStateException("Invalid balance management port"); }
        BalanceBundle initial = manager.captureBalanceBundle();
        BalanceGameRuntime runtime = new BalanceGameRuntime(server, manager);
        var catalog = WebCatalogExporter.snapshot(System.currentTimeMillis(), initial.wave(), initial.economy(), initial.summon(), initial.augment());
        BalanceFieldRegistry fields = new BalanceFieldRegistry(BalanceBundle.defaults(), catalog, BalanceGameRuntime.verifiedLiveTowerIds());
        BalanceChangeService service = new BalanceChangeService(directory, serverId, initial, fields, runtime);
        BalanceHttpServer http = null;
        try {
            if (service.state().writeBlocked() != null) {
                throw new IllegalStateException("Managed balance recovery failed; refusing to use legacy JSON fallback");
            }
            runtime.configureLegacyBaseline(service.legacyRevision(), initial);
            runtime.bootstrap(service.currentBundle(), service.currentRevision());
            // Startup waits before accepting requests; regular match ticks never do this disk write.
            CompletableFuture.supplyAsync(runtime::afterApplied).join();
            manager.attachManagedBalance(runtime, service::onBoundary);
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            if (enabled) {
                http = new BalanceHttpServer(port, service, auth);
                http.start();
            }
            return new BalanceManagementBootstrap(service, http);
        } catch (IOException | RuntimeException exception) {
            if (http != null) { http.close(); }
            service.close();
            throw new IllegalStateException("Balance management startup failed", exception);
        }
    }

    @Override public void close() {
        if (http != null) { http.close(); }
        service.close();
    }
}
