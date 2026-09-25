package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.summon.IncomeSummons;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** One server-wide balance runtime; every mutation runs in the server's lifecycle task. */
public final class BalanceGameRuntime implements BalanceChangeService.Runtime {
    private final MinecraftServer server;
    private final SemionGameManager manager;
    private String legacyConfigRevision;
    private String revision;
    private String manualConfigConflict;
    private volatile SemionGameManager.PreparedBalanceCatalog pendingCatalog;
    private volatile String catalogStatus = "DISABLED";

    public BalanceGameRuntime(MinecraftServer server, SemionGameManager manager) {
        this.server = server;
        this.manager = manager;
        revision = manager.captureBalanceBundle().revision();
        legacyConfigRevision = revision;
    }

    public static Set<String> verifiedLiveTowerIds() {
        return Set.of(AnimalTowers.T1_PIG_TOWER.id(), AnimalTowers.T2_PIG_TOWER.id(),
                AnimalTowers.T3_PIG_TOWER.id(), AnimalTowers.T4_PIG_LEADER_TOWER.id(),
                AnimalTowers.T1_WOLF_TOWER.id(), AnimalTowers.T2_WOLF_DPS_TOWER.id(),
                AnimalTowers.T3_WOLF_DPS_TOWER.id(), AnimalTowers.T4_WOLF_LEADER_TOWER.id(),
                AnimalTowers.T1_RABBIT_TOWER.id(), AnimalTowers.T2_RABBIT_TOWER.id(),
                AnimalTowers.T3_RABBIT_TOWER.id(), AnimalTowers.T4_RABBIT_LEADER_TOWER.id(),
                AnimalTowers.T1_FOX_TOWER.id(), AnimalTowers.T2_FOX_TOWER.id(),
                AnimalTowers.T3_FOX_TOWER.id(), AnimalTowers.T4_FOX_LEADER_TOWER.id());
    }

    public String revision() {
        return revision;
    }

    @Override
    public void broadcastApplied(BalanceDtos.BalanceDeployment deployment) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[밸런스] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("밸런스가 업데이트되었습니다. (" + deployment.changes().size() + "개 항목)")
                        .withStyle(ChatFormatting.WHITE)), false);
    }

    public String writeBlocked() {
        return manualConfigConflict;
    }

    public void checkManualConfigConflict(BalanceBundle loaded) {
        manualConfigConflict = loaded.revision().equals(legacyConfigRevision) ? null
                : "Managed balance differs from legacy JSON files; review/import the changes before applying another patch.";
    }

    public void configureLegacyBaseline(String baselineRevision, BalanceBundle loadedLegacy) {
        legacyConfigRevision = java.util.Objects.requireNonNull(baselineRevision);
        checkManualConfigConflict(loadedLegacy);
    }

    public void bootstrap(BalanceBundle active, String activeRevision) {
        if (!manager.nextBalanceMatchSafe()) throw new IllegalStateException("Cannot bootstrap during a match");
        apply(active, ApplyMode.NEXT_MATCH, "bootstrap", activeRevision);
    }

    @Override
    public BalanceChangeService.RuntimeView view() {
        requireServerThread();
        SemionGame game = manager.activeGame().filter(current -> current.rosterLocked() && current.phase() != RoundPhase.ENDED).orElse(null);
        return new BalanceChangeService.RuntimeView(game == null ? null : game.matchId().toString(),
                game == null ? 0 : game.currentRound(), game == null ? "WAITING" : game.phase().name(),
                WebCatalogExporter.currentVersion().orElse(null), manager.nextBalanceMatchSafe());
    }

    @Override
    public String apply(BalanceBundle candidate, ApplyMode mode, String requestId, String nextRevision) {
        requireServerThread();
        if (manager.hasPracticeGames()) {
            throw new IllegalStateException("Balance changes require all sandbox and tutorial sessions to finish");
        }
        if (mode == ApplyMode.NEXT_MATCH && !manager.nextBalanceMatchSafe()) {
            throw new IllegalStateException("Next-match balance cannot change a running match");
        }
        // Reparse all seven domains before any mutation, including record constructor validation.
        BalanceBundle next = BalanceBundle.fromJson(candidate.toJson());
        BalanceBundle previous = manager.captureBalanceBundle();
        ApplyMode validationMode = mode == ApplyMode.NOW && manager.nextBalanceMatchSafe() ? ApplyMode.NEXT_PREPARE : mode;
        Set<String> changedTowers = changedLiveTowerIds(previous, next, validationMode);
        var previousCatalog = ProductionTowerCatalog.snapshot();
        var nextCatalog = ProductionTowerCatalog.stageBalance(next.tower());
        var previousSummons = SummonRegistry.all();
        var nextSummons = IncomeSummons.build(next.summon());
        List<TowerRefresh> towers = new ArrayList<>();
        SemionGame game = manager.activeGame().filter(current -> current.rosterLocked() && current.phase() != RoundPhase.ENDED).orElse(null);
        if (mode != ApplyMode.NEXT_MATCH && game != null) {
            for (var team : game.teams().values()) {
                for (PlayerLane lane : team.laneGroup().lanes()) {
                    for (Tower tower : lane.towers()) {
                        if (!tower.isTemporaryCopy() && changedTowers.contains(tower.type().id())) {
                            towers.add(new TowerRefresh(tower, lane, tower.captureBalanceState()));
                        }
                    }
                }
            }
        }
        try {
            TowerBalanceRuntime.apply(next.tower());
            TraitBalanceRuntime.apply(next.trait());
            ProductionTowerCatalog.install(nextCatalog);
            SummonRegistry.reload(nextSummons);
            manager.installBalanceBundle(next);
            for (TowerRefresh refresh : towers) {
                refresh.tower().refreshType(nextCatalog.entries().get(refresh.tower().type().id()).type(), refresh.lane());
                if (refresh.tower() instanceof EntityBackedTower entityBacked) {
                    var entity = entityBacked.runtimeEntity(refresh.lane()).orElse(null);
                    if (entity != null) {
                        entity.refreshMaxHealthEffects(false);
                    } else {
                        refresh.tower().syncMaxHealth(refresh.tower().effectBaseMaxHealth(), false);
                    }
                    refresh.tower().syncHealth(refresh.snapshot().health());
                    if (entity != null) entity.syncTowerState(refresh.tower());
                }
            }
        } catch (RuntimeException failure) {
            try {
                TowerBalanceRuntime.apply(previous.tower());
                TraitBalanceRuntime.apply(previous.trait());
                ProductionTowerCatalog.install(previousCatalog);
                SummonRegistry.reload(previousSummons);
                manager.installBalanceBundle(previous);
                for (TowerRefresh refresh : towers) refresh.tower().restoreBalanceState(refresh.snapshot(), refresh.lane());
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                throw new BalanceChangeService.FatalRollbackException("Balance rollback failed; further writes are blocked", failure);
            }
            throw failure;
        }
        String previousRevision = revision;
        revision = nextRevision;
        if (game != null) game.recordBalancePatch(requestId, previousRevision, nextRevision, mode.name());
        try {
            pendingCatalog = manager.prepareBalanceCatalog();
            catalogStatus = pendingCatalog == null ? "DISABLED" : "PENDING";
        } catch (RuntimeException exportFailure) {
            pendingCatalog = null;
            catalogStatus = "FAILED";
        }
        return catalogStatus;
    }

    @Override
    public String afterApplied() {
        var catalog = pendingCatalog;
        pendingCatalog = null;
        if (catalog != null) catalogStatus = catalog.publish();
        return catalogStatus;
    }

    private static Set<String> changedLiveTowerIds(BalanceBundle previous, BalanceBundle next, ApplyMode mode) {
        if (mode == ApplyMode.NEXT_MATCH) return Set.of();
        JsonObject before = previous.toJson();
        JsonObject after = next.toJson();
        if (!BalanceFieldRegistry.flatten(before).keySet().equals(BalanceFieldRegistry.flatten(after).keySet())) {
            throw new IllegalArgumentException("Tower schema changes require NEXT_MATCH");
        }
        for (String domain : before.keySet()) {
            if (!domain.equals("tower") && !before.get(domain).equals(after.get(domain))) {
                throw new IllegalArgumentException("Only tower stats and abilities can change in an active match");
            }
        }
        JsonObject oldTower = before.getAsJsonObject("tower");
        JsonObject newTower = after.getAsJsonObject("tower");
        JsonObject oldStats = oldTower.remove("towers").getAsJsonObject();
        JsonObject newStats = newTower.remove("towers").getAsJsonObject();
        JsonObject oldCosts = oldTower.remove("upgradeCosts").getAsJsonObject();
        JsonObject newCosts = newTower.remove("upgradeCosts").getAsJsonObject();
        boolean abilitiesChanged = false;
        for (String key : List.of("abilities", "villagerAdv", "illusionCloneQueue")) {
            abilitiesChanged |= !oldTower.remove(key).equals(newTower.remove(key));
        }
        if (!oldTower.equals(newTower) || !oldStats.keySet().equals(newStats.keySet())
                || !oldCosts.keySet().equals(newCosts.keySet())) {
            throw new IllegalArgumentException("Tower schema changes require NEXT_MATCH");
        }
        Set<String> verified = verifiedLiveTowerIds();
        Set<String> changed = new HashSet<>();
        for (String id : oldStats.keySet()) {
            if (oldStats.get(id).equals(newStats.get(id))) continue;
            if (!verified.contains(id)) throw new IllegalArgumentException("Tower has no live-refresh proof: " + id);
            JsonObject a = oldStats.getAsJsonObject(id);
            JsonObject b = newStats.getAsJsonObject(id);
            for (String field : a.keySet()) {
                if (mode == ApplyMode.NOW && !Set.of("damage", "range", "aggroPriority").contains(field)
                        && !a.get(field).equals(b.get(field))) {
                    throw new IllegalArgumentException(field + " requires NEXT_PREPARE or NEXT_MATCH");
                }
            }
            changed.add(id);
        }
        for (String edge : oldCosts.keySet()) {
            if (oldCosts.get(edge).equals(newCosts.get(edge))) continue;
            String fromId = edge.contains("->") ? edge.substring(0, edge.indexOf("->")) : edge;
            if (mode == ApplyMode.NOW || !verified.contains(fromId)) {
                throw new IllegalArgumentException("Upgrade cost requires a verified NEXT_PREPARE edge: " + edge);
            }
        }
        // Shared ability groups can affect other towers. Use the existing reload refresh path,
        // retaining temporary-copy snapshots and the transactional health/type rollback above.
        if (abilitiesChanged) changed.addAll(oldStats.keySet());
        return Set.copyOf(changed);
    }

    private void requireServerThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Balance runtime requires the server thread");
    }

    private record TowerRefresh(Tower tower, PlayerLane lane, Tower.BalanceSnapshot snapshot) {
    }
}
