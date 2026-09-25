package kim.biryeong.semiontd.report;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.DoubleSupplier;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.ui.SemionDialogService;
import kim.biryeong.semiontd.web.WebCatalogExporter;
import net.fabricmc.loader.api.FabricLoader;

/** Called only on the server thread. Explicit value capture, never entity/reflection serialization. */
public final class BugReportSnapshot {
    private static final Gson GSON = new Gson();
    private BugReportSnapshot() {}

    public static JsonObject capture(SemionGame game) {
        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("schemaVersion", 1);
        snapshot.addProperty("modVersion", FabricLoader.getInstance().getModContainer("semion-td")
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown"));
        snapshot.addProperty("catalogVersion", WebCatalogExporter.currentVersion().orElse(null));
        snapshot.addProperty("damageSemantics", "Displayed attack before target-specific modifiers and defense; not guaranteed hit damage.");
        snapshot.addProperty("hasGame", game != null);
        JsonArray towers = new JsonArray();
        JsonArray players = new JsonArray();
        snapshot.add("towers", towers);
        snapshot.add("players", players);
        if (game == null) return snapshot;

        snapshot.addProperty("matchId", game.matchId() == null ? null : Long.toString(game.matchId().value()));
        snapshot.addProperty("mode", game.matchMode().name());
        snapshot.addProperty("sandbox", game.isSandboxMode());
        snapshot.addProperty("tutorial", game.isTutorialMode());
        snapshot.addProperty("round", game.currentRound());
        snapshot.addProperty("phase", game.phase().name());
        snapshot.addProperty("tick", game.currentTick());
        snapshot.addProperty("phaseTicks", game.phaseTicks());
        snapshot.addProperty("startBalanceRevision", game.startBalanceRevision());
        var patches = game.balancePatchEvents();
        snapshot.addProperty("effectiveBalanceRevision", patches.isEmpty() ? game.startBalanceRevision() : patches.getLast().effectiveRevision());
        snapshot.add("balancePatchEvents", GSON.toJsonTree(patches));
        for (var player : game.players().values()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", player.uuid().toString());
            value.addProperty("name", player.name());
            value.addProperty("team", player.teamId().name());
            value.addProperty("lane", player.laneId());
            value.addProperty("jobId", player.job().map(job -> job.id().toString()).orElse(null));
            value.add("economy", GSON.toJsonTree(player.economy()));
            value.add("traits", GSON.toJsonTree(player.traitLoadoutSnapshot()));
            value.add("augments", GSON.toJsonTree(player.augments().selections()));
            players.add(value);
        }
        JsonArray lanes = new JsonArray();
        snapshot.add("lanes", lanes);
        for (var team : game.teams().values()) {
            for (var lane : team.laneGroup().lanes()) {
                JsonObject value = new JsonObject();
                value.addProperty("team", team.id().name());
                value.addProperty("lane", lane.laneId());
                value.addProperty("eliminated", team.eliminated());
                value.addProperty("dimension", lane.arenaWorld() == null ? null : lane.arenaWorld().dimension().location().toString());
                value.addProperty("waveTemplateId", lane.waveTemplateId());
                value.addProperty("activeMonsters", lane.activeMonsters().size());
                value.addProperty("queuedSummons", lane.queuedSummonCount());
                value.addProperty("leakedCount", lane.leakedCountThisRound());
                lanes.add(value);
                for (Tower tower : lane.towers()) towers.add(tower(tower, lane));
            }
        }
        return snapshot;
    }

    public static JsonObject tower(Tower tower, PlayerLane lane) {
        JsonObject value = new JsonObject();
        JsonArray errors = new JsonArray();
        value.add("captureErrors", errors);
        value.addProperty("logicalId", tower.logicalId().toString());
        value.addProperty("typeId", tower.type().id());
        value.addProperty("name", tower.type().displayName());
        value.addProperty("ownerId", tower.ownerPlayer().toString());
        value.addProperty("team", tower.teamId().name());
        value.addProperty("lane", tower.laneId());
        value.add("position", GSON.toJsonTree(tower.position()));
        value.add("originalPosition", GSON.toJsonTree(tower.originalPosition()));
        value.addProperty("paidDiamond", tower.paidMineralCost());
        value.addProperty("temporaryCopy", tower.isTemporaryCopy());
        value.addProperty("copySourceId", tower.temporaryCopySourceId() == null ? null : tower.temporaryCopySourceId().toString());
        value.addProperty("baseAttack", tower.type().damage());
        value.addProperty("baseMaxHealth", tower.type().maxHealth());
        value.addProperty("damageType", tower.primaryDamageType().name());
        value.add("state", GSON.toJsonTree(tower.diagnosticState()));
        var entity = tower instanceof EntityBackedTower backed ? backed.runtimeEntity(lane).orElse(null) : null;
        value.addProperty("entityPresent", entity != null);
        value.addProperty("alive", entity == null ? tower.health() > 0 : entity.isAlive());
        number(value, errors, "health", () -> entity == null ? tower.health() : entity.getHealth());
        number(value, errors, "maxHealth", () -> entity == null ? tower.currentMaxHealth() : entity.getMaxHealth());
        number(value, errors, "attack", () -> SemionDialogService.currentTowerPrimaryDamage(tower, entity));
        number(value, errors, "attackIntervalTicks", () -> entity == null ? tower.adjustAttackInterval(tower.type().attackIntervalTicks()) : entity.attackIntervalTicks());
        number(value, errors, "range", () -> entity == null ? tower.adjustAttackRange(tower.type().range()) : entity.attackRange());
        number(value, errors, "permanentAttackBonus", tower::permanentFlatDamageBonus);
        number(value, errors, "permanentHealthBonus", tower::permanentMaxHealthBonus);
        number(value, errors, "roundDamageDealt", tower::roundDamageDealt);
        number(value, errors, "roundDamageTaken", tower::roundDamageTaken);
        value.add("effects", GSON.toJsonTree(entity == null ? List.of() : entity.effectSnapshot()));
        if (entity != null) {
            value.addProperty("entityId", entity.getUUID().toString());
            value.addProperty("x", entity.getX()); value.addProperty("y", entity.getY()); value.addProperty("z", entity.getZ());
        }
        try { value.add("runtimeDetails", GSON.toJsonTree(SemionDialogService.towerRuntimeDetailLines(tower))); }
        catch (RuntimeException exception) { errors.add("runtimeDetails"); }
        return value;
    }

    private static void number(JsonObject value, JsonArray errors, String key, DoubleSupplier read) {
        try {
            double result = read.getAsDouble();
            if (Double.isFinite(result)) { value.addProperty(key, result); return; }
        } catch (RuntimeException exception) { /* Keep the rest of the evidence and name the missing field. */ }
        value.add(key, JsonNull.INSTANCE);
        errors.add(key);
    }
}
