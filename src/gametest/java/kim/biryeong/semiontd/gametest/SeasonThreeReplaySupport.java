package kim.biryeong.semiontd.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import net.minecraft.core.BlockPos;

/** Test-only adapters for the existing BuildAction and purchase APIs. */
final class SeasonThreeReplaySupport {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();

    private SeasonThreeReplaySupport() {}

    record Sample(String builderId, String matchId, String playerRef, String catalogVersion,
            int sourceFinalRound, List<Integer> attemptedRounds, JsonElement sourceTraits,
            JsonElement sourceMap, List<BuildAction> actions) {}

    record ActionResult(String status, long diamondsDelta, long emeraldsDelta, long incomeDelta) {
        boolean success() {return "SUCCESS".equals(status);}
        boolean retryable() {return "NOT_ENOUGH_MINERAL".equals(status) || "NOT_ENOUGH_GAS".equals(status);}
    }

    static List<Sample> samples() {
        String file = System.getProperty("semiontd.baseline.records");
        if (file == null || !Path.of(file).isAbsolute()) {
            throw new IllegalArgumentException("semiontd.baseline.records must be an absolute manifest path");
        }
        try {
            String builder = System.getProperty("semiontd.baseline.builder");
            return parseSamples(Files.readString(Path.of(file))).stream()
                    .filter(sample -> builder == null || builder.equals(sample.builderId())).toList();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read baseline manifest: " + file, failure);
        }
    }

    static List<Sample> parseSamples(String text) {
        JsonObject root = JSON.fromJson(text, JsonObject.class);
        if (root.get("schemaVersion").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported baseline manifest version");
        }
        List<Sample> result = new ArrayList<>();
        var builders = new HashSet<String>();
        for (JsonElement entry : root.getAsJsonArray("builders")) {
            JsonObject builder = entry.getAsJsonObject();
            String id = builder.get("builderId").getAsString();
            if (!builders.add(id)) {throw new IllegalArgumentException("Duplicate builder: " + id);}
            var players = new HashSet<String>();
            for (JsonElement value : builder.getAsJsonArray("records")) {
                JsonObject source = value.getAsJsonObject();
                String player = source.get("playerRef").getAsString();
                if (!players.add(player) || players.size() > 3) {
                    throw new IllegalArgumentException("Expected at most three distinct players for " + id);
                }
                List<Integer> rounds = new ArrayList<>();
                if (source.has("attemptedRounds") && !source.get("attemptedRounds").isJsonNull()) {
                    for (JsonElement round : source.getAsJsonArray("attemptedRounds")) {
                        rounds.add(round.getAsInt());
                    }
                }
                List<BuildAction> actions = new ArrayList<>();
                int previousRound = 0;
                for (JsonElement actionJson : source.getAsJsonArray("actions")) {
                    JsonObject raw = actionJson.getAsJsonObject();
                    int round = raw.get("round").getAsInt();
                    if (round < 1 || round < previousRound || raw.get("cost").getAsLong() < 0
                            || raw.get("incomeGain").getAsLong() < 0) {
                        throw new IllegalArgumentException("Invalid action order or economy in " + id);
                    }
                    BuildAction action = JSON.fromJson(raw, BuildAction.class);
                    if (action.subjectId().isBlank()) {throw new IllegalArgumentException("Missing action subject");}
                    actions.add(action);
                    previousRound = round;
                }
                result.add(new Sample(id, source.get("matchId").getAsString(), player,
                        source.get("catalogVersion").getAsString(), source.get("sourceFinalRound").getAsInt(),
                        List.copyOf(rounds), source.get("sourceTraits"), source.get("sourceMap"), List.copyOf(actions)));
            }
        }
        return List.copyOf(result);
    }

    static Optional<String> exclusion(String builderId) {
        return Optional.ofNullable(switch (builderId) {
            case "semion-td:demon_lord_towers" -> "UNRECORDED_PERMANENT_STAT_SELECTION_AND_MANUAL_MOVEMENT";
            case "semion-td:developer" -> "UNRECORDED_PATCH_AND_OPTIMIZATION_SELECTION";
            case "semion-td:hero_party" -> "UNRECORDED_WEAPON_AND_ARMOR_SELECTION";
            default -> null;
        });
    }

    static GridPosition position(PlayerLane lane, BuildAction action) {
        if (action.position() == null || !action.hasLaneRelativePosition()) {return null;}
        BlockPos min = lane.laneLayout().laneArea().min();
        GridPosition relative = action.position();
        return new GridPosition(Math.addExact(min.getX(), relative.x()), Math.addExact(min.getY(), relative.y()),
                Math.addExact(min.getZ(), relative.z()));
    }

    static GridPosition resolvedPosition(PlayerLane lane, BuildAction action) {
        GridPosition pos = position(lane, action);
        return pos == null ? null : TowerPlacementPositions.resolveGrid(lane,
                new BlockPos(pos.x(), pos.y(), pos.z())).orElse(null);
    }

    static ActionResult execute(SemionGame game, SemionPlayer player, BuildAction action, int slotLimit) {
        long diamonds = player.economy().diamond();
        long emeralds = player.economy().emerald();
        long income = player.economy().income();
        String status = apply(game, player, action, slotLimit);
        return new ActionResult(status, player.economy().diamond() - diamonds,
                player.economy().emerald() - emeralds, player.economy().income() - income);
    }

    private static String apply(SemionGame game, SemionPlayer player, BuildAction action, int slotLimit) {
        if (game.phase() != RoundPhase.PREPARE_AND_SUMMON) {return "INVALID_PHASE";}
        if (game.players().get(player.uuid()) != player) {return "PLAYER_NOT_IN_GAME";}
        var team = game.teams().get(player.teamId());
        if (team == null || team.eliminated()) {return "PLAYER_TEAM_ELIMINATED";}
        PlayerLane lane = game.playerLane(player.uuid()).orElse(null);
        if (lane == null) {return "UNKNOWN_LANE";}
        if (action.type() == BuildActionType.SUMMON) {
            return game.summonMonster(player.uuid(), action.subjectId()).type().name();
        }
        if (action.type() == BuildActionType.EMERALD_PRODUCTION_UPGRADE) {
            int next;
            try {next = Integer.parseInt(action.subjectId());}
            catch (NumberFormatException failure) {return "UNKNOWN_PRODUCTION_UPGRADE";}
            int count = player.economy().emeraldProductionUpgradeCount();
            if (next != count + 1) {return "PRODUCTION_SEQUENCE_MISMATCH";}
            var config = game.economyConfig().gasProduction();
            if (count >= config.maxUpgradeCount()) {return "PRODUCTION_LIMIT_REACHED";}
            if (game.upgradeGasProduction(player.uuid())) {return "SUCCESS";}
            return config.upgradeCurrency().spendsDiamond() ? "NOT_ENOUGH_MINERAL" : "NOT_ENOUGH_GAS";
        }
        GridPosition pos = position(lane, action);
        if (pos == null) {return "UNRESOLVED_SOURCE_POSITION";}
        int used = game.towerCapacityUsed(player.uuid());
        if (action.type() == BuildActionType.TOWER_PLACE) {
            var entry = ProductionTowerCatalog.find(action.subjectId()).orElse(null);
            if (entry == null) {return "UNKNOWN_TOWER";}
            if (used + slotCost(entry.type()) > slotLimit) {return "COMMON_SLOT_LIMIT_REACHED";}
            return ProductionTowerService.placeTower(game, player.uuid(), new BlockPos(pos.x(), pos.y(), pos.z()),
                    action.subjectId()).name();
        }
        GridPosition target = resolvedPosition(lane, action);
        Tower tower = target == null ? null : lane.towerAt(target);
        if (tower == null) {return "NO_TOWER_AT_POSITION";}
        if (action.type() == BuildActionType.TOWER_UPGRADE) {
            var upgrade = ProductionTowerCatalog.upgrade(tower.type(), action.subjectId()).orElse(null);
            if (upgrade == null) {return "UNKNOWN_UPGRADE";}
            if (used - TowerCapacity.slotCost(tower) + slotCost(upgrade.targetType()) > slotLimit) {
                return "COMMON_SLOT_LIMIT_REACHED";
            }
            return ProductionTowerService.upgradeTower(game, player.uuid(), target, action.subjectId()).name();
        }
        if (!tower.type().id().equals(action.subjectId())) {return "SALE_TARGET_TYPE_MISMATCH";}
        return ProductionTowerService.sellTower(game, player.uuid(), target).result().name();
    }

    private static int slotCost(TowerType type) {
        return EngineerTowers.isSlotFree(type) ? 0 : TowerCapacity.slotCost(type);
    }

    static List<Monster> observedRoundMonsters(PlayerLane lane) {
        try {
            var field = PlayerLane.class.getDeclaredField("roundMonsters");
            field.setAccessible(true);
            // Existing retained ledger includes entities killed between two fixture ticks.
            Map<?, ?> monsters = (Map<?, ?>) field.get(lane);
            return monsters.values().stream().map(Monster.class::cast).filter(Monster::hasMinecraftEntity).toList();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect the existing round monster ledger", failure);
        }
    }

    static String canonicalHash(Object value) {
        return hash(sortedObjectKeys(JSON.toJsonTree(value)));
    }

    private static JsonElement sortedObjectKeys(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().keySet().stream().sorted()
                    .forEach(key -> sorted.add(key, sortedObjectKeys(value.getAsJsonObject().get(key))));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray sorted = new JsonArray();
            value.getAsJsonArray().forEach(item -> sorted.add(sortedObjectKeys(item)));
            return sorted;
        }
        return value;
    }

    static String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJson(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
