package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class EngineerTowers {
    public static final TowerType COPPER_GOLEM = TowerType.builder("engineer_copper_golem", "구리 골렘")
            .mineralCost(30)
            .maxHealth(1.0)
            .range(0.0)
            .damage(0.0)
            .attackIntervalTicks(20)
            .aggroPriority(-100)
            .visual(EntityVisual.vanilla("friendsandfoes:copper_golem"))
            .description(List.of(
                    "<gray>웨이브 중 <aqua>발판</aqua>을 우선순위와 거리 순으로 순회합니다.</gray>",
                    "<green>공격하지 않고 무적이며 타워 슬롯을 사용하지 않습니다.</green>"
            ))
            .build();

    public static final TowerType REDSTONE_DUST = circuitType(
            "engineer_redstone_dust", "레드스톤 가루", 15, Blocks.REDSTONE_WIRE,
            List.of(
                    "<gray>실제 바닐라 <red>레드스톤</red> 신호를 전달하며 슬롯을 사용하지 않습니다.</gray>",
                    "<gold>8다이아로 방향형 중계기로 강화할 수 있습니다.</gold>"
            )
    );

    private static final Map<Direction, TowerType> REPEATERS = createRepeaters();
    private static final Map<PlateKind, TowerType> PLATES = createPlates();
    private static final Map<TrapKind, List<TowerType>> TRAPS = createTraps();
    private static final List<TowerType> ALL = createAll();

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private EngineerTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isEngineerTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isGolem(TowerType type) {
        return type != null && COPPER_GOLEM.id().equals(type.id());
    }

    public static boolean isDust(TowerType type) {
        return type != null && REDSTONE_DUST.id().equals(type.id());
    }

    public static Optional<Direction> repeaterDirection(TowerType type) {
        return findKey(REPEATERS, type);
    }

    public static TowerType repeater(Direction direction) {
        return REPEATERS.get(direction);
    }

    public static Map<Direction, TowerType> repeaters() {
        return REPEATERS;
    }

    public static Optional<PlateKind> plateKind(TowerType type) {
        return findKey(PLATES, type);
    }

    public static TowerType plate(PlateKind kind) {
        return PLATES.get(kind);
    }

    public static Optional<TrapKind> trapKind(TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        return TRAPS.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(candidate -> candidate.id().equals(type.id())))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public static int trapTier(TowerType type) {
        return trapKind(type)
                .map(kind -> TRAPS.get(kind).indexOf(findById(TRAPS.get(kind), type)) + 1)
                .orElse(0);
    }

    public static TowerType trap(TrapKind kind, int tier) {
        return TRAPS.get(kind).get(Math.max(1, Math.min(3, tier)) - 1);
    }

    public static Map<TrapKind, List<TowerType>> traps() {
        return TRAPS;
    }

    public static boolean isSlotFree(TowerType type) {
        return isGolem(type) || isDust(type) || repeaterDirection(type).isPresent();
    }

    private static Map<Direction, TowerType> createRepeaters() {
        LinkedHashMap<Direction, TowerType> result = new LinkedHashMap<>();
        for (Direction direction : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            result.put(direction, circuitType(
                    "engineer_repeater_" + direction.getName(),
                    "중계기 · " + directionName(direction),
                    15,
                    Blocks.REPEATER,
                    List.of(
                            "<gray><red>레드스톤</red> 신호를 <gold>바라보는 방향</gold>으로 지연 전달합니다.</gray>",
                            "<green>0다이아 강화로 90도 회전하며 슬롯을 사용하지 않습니다.</green>"
                    )
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<PlateKind, TowerType> createPlates() {
        EnumMap<PlateKind, TowerType> result = new EnumMap<>(PlateKind.class);
        for (PlateKind kind : PlateKind.values()) {
            result.put(kind, circuitType(
                    "engineer_plate_" + kind.id,
                    kind.displayName,
                    8,
                    kind.block,
                    List.of(
                            "<gray>구리 골렘이 밟아 회로에 <gold>전력</gold>을 공급하는 <aqua>발판</aqua>입니다.</gray>",
                            "<green>선택 우선순위: " + kind.priority + ". 밟은 뒤 "
                                    + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "plateCooldownTicks", "seconds")
                                    + " 동안 다시 선택되지 않습니다.</green>"
                    )
            ));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<TrapKind, List<TowerType>> createTraps() {
        EnumMap<TrapKind, List<TowerType>> result = new EnumMap<>(TrapKind.class);
        for (TrapKind kind : TrapKind.values()) {
            ArrayList<TowerType> tiers = new ArrayList<>();
            for (int tier = 1; tier <= 3; tier++) {
                tiers.add(trapType(kind, tier));
            }
            result.put(kind, List.copyOf(tiers));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<TowerType> createAll() {
        ArrayList<TowerType> result = new ArrayList<>();
        result.add(COPPER_GOLEM);
        result.add(REDSTONE_DUST);
        result.addAll(REPEATERS.values());
        result.addAll(PLATES.values());
        TRAPS.values().forEach(result::addAll);
        return List.copyOf(result);
    }

    private static TowerType circuitType(String id, String name, long cost, Block block, List<String> description) {
        return TowerType.builder(id, name)
                .mineralCost(cost)
                .maxHealth(1.0)
                .range(0.0)
                .damage(0.0)
                .attackIntervalTicks(20)
                .aggroPriority(-100)
                .visual(BlockDisplayVisual.builder(block.defaultBlockState()).build())
                .description(description)
                .build();
    }

    private static TowerType trapType(TrapKind kind, int tier) {
        long cost = switch (kind) {
            case DOOR -> tier == 1 ? 40 : 0;
            case TNT -> tier == 1 ? 65 : 0;
            case DISPENSER -> tier == 1 ? 45 : 0;
            case PISTON -> tier == 1 ? 80 : 0;
            case SLIME -> tier == 1 ? 50 : 0;
        };
        double health = kind == TrapKind.DOOR
                ? new double[]{220, 500, 850}[tier - 1]
                : new double[]{150, 300, 500}[tier - 1];
        String ability = "ability." + id(kind, tier) + ".";
        return TowerType.builder(id(kind, tier), kind.displayName)
                .mineralCost(cost)
                .maxHealth(health)
                .range(0.0)
                .damage(0.0)
                .attackIntervalTicks(20)
                .aggroPriority(kind == TrapKind.DOOR ? 200 : -100)
                .visual(BlockDisplayVisual.builder(kind.block.defaultBlockState()).build())
                .description(trapDescription(kind, ability))
                .build();
    }

    private static List<String> trapDescription(TrapKind kind, String ability) {
        String powered = "<gray><red>레드스톤</red> 상승 신호를 받으면 <gold>"
                + placeholder(
                        "ability." + EngineerBalance.GLOBAL_ID + ".",
                        kind == TrapKind.DOOR ? "doorActiveTicks" : "activeTicks",
                        "seconds"
                )
                + "</gold> 동안 작동하는 <yellow>함정</yellow>입니다.</gray>";
        return switch (kind) {
            case DOOR -> List.of(powered, "<green>반경 " + placeholder(ability, "radius", "blocks") + "의 몬스터를 유인합니다.</green>");
            case TNT -> List.of(powered, "<red>" + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "tntFuseTicks", "seconds")
                    + " 뒤 최대 " + placeholder(ability, "maxTargets", "integer") + "기에게 피해 "
                    + placeholder(ability, "damage", "integer") + "을 줍니다. 라운드당 한 번만 폭발합니다.</red>",
                    plateDamageDescription());
            case DISPENSER -> List.of(
                    powered,
                    "<green>사거리 " + placeholder(ability, "range", "blocks") + "에서 피해 "
                            + placeholder(ability, "damage", "integer") + "을 반복 발사합니다.</green>",
                    "<gold>신호를 보낸 발판까지의 회로 거리 1칸당 피해가 "
                            + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "dispenserDamagePerPlateBlock", "percent")
                            + " 증가하며 최대 "
                            + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "dispenserMaxPlateDistance", "integer")
                            + "칸까지 적용됩니다.</gold>",
                    plateDamageDescription()
            );
            case PISTON -> List.of(powered, "<aqua>반경 " + placeholder(ability, "radius", "blocks") + "의 최대 "
                    + placeholder(ability, "maxTargets", "integer") + "기를 라인 시작점으로 되돌립니다. 같은 적은 "
                    + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "pistonImmunityTicks", "seconds")
                    + " 동안 면역입니다.</aqua>");
            case SLIME -> List.of(powered, "<green>반경 " + placeholder(ability, "radius", "blocks") + "의 이동속도를 " + placeholder(ability, "slow", "percent") + " 낮춥니다.</green>");
        };
    }

    private static String placeholder(String prefix, String key, String format) {
        return "{" + prefix + key + ":" + format + "}";
    }

    private static String plateDamageDescription() {
        return "<aqua>발판 등급이 오를 때마다 피해가 "
                + placeholder("ability." + EngineerBalance.GLOBAL_ID + ".", "plateDamageBonusPerTier", "percent")
                + " 증가합니다.</aqua>";
    }

    private static String id(TrapKind kind, int tier) {
        return "engineer_" + kind.id + "_t" + tier;
    }

    private static <K> Optional<K> findKey(Map<K, TowerType> map, TowerType type) {
        if (type == null) {
            return Optional.empty();
        }
        return map.entrySet().stream()
                .filter(entry -> entry.getValue().id().equals(type.id()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    private static TowerType findById(List<TowerType> types, TowerType type) {
        return types.stream().filter(candidate -> candidate.id().equals(type.id())).findFirst().orElse(null);
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case NORTH -> "북";
            case EAST -> "동";
            case SOUTH -> "남";
            case WEST -> "서";
            default -> direction.getName();
        };
    }

    public enum PlateKind {
        WOOD("wood", "나무 발판", Blocks.OAK_PRESSURE_PLATE, 1),
        STONE("stone", "돌 발판", Blocks.STONE_PRESSURE_PLATE, 2),
        IRON("iron", "철 발판", Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, 3),
        GOLD("gold", "금 발판", Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, 4);

        private final String id;
        private final String displayName;
        private final Block block;
        private final int priority;

        PlateKind(String id, String displayName, Block block, int priority) {
            this.id = id;
            this.displayName = displayName;
            this.block = block;
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }

        public Optional<PlateKind> next() {
            return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
        }
    }

    public enum TrapKind {
        DOOR("door", "철문", Blocks.IRON_DOOR),
        TNT("tnt", "TNT", Blocks.TNT),
        DISPENSER("dispenser", "발사기", Blocks.DISPENSER),
        PISTON("piston", "피스톤", Blocks.PISTON),
        SLIME("slime", "슬라임 함정", Blocks.SLIME_BLOCK);

        private final String id;
        private final String displayName;
        private final Block block;

        TrapKind(String id, String displayName, Block block) {
            this.id = id;
            this.displayName = displayName;
            this.block = block;
        }
    }
}
