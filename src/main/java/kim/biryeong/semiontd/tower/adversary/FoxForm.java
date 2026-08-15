package kim.biryeong.semiontd.tower.adversary;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum FoxForm {
    BASE(
            "막대기 여우",
            FoxRole.BASE,
            null,
            0,
            Items.STICK,
            300.0,
            3.0,
            16.0,
            10,
            0.0,
            null
    ),
    BREEZE(
            "질풍 여우",
            FoxRole.RAPID,
            FoxRoute.RAPID,
            1,
            Items.BREEZE_ROD,
            550.0,
            7.0,
            26.0,
            4,
            0.0,
            EvolutionRecipe.visible(RivalKind.BREEZE, 12)
    ),
    GOLDEN_FANG(
            "황금 송곳니",
            FoxRole.RAPID,
            FoxRoute.RAPID,
            2,
            Items.GOLDEN_SWORD,
            750.0,
            5.0,
            30.0,
            3,
            0.10,
            EvolutionRecipe.visible(RivalKind.BREEZE, 50)
    ),
    SHIELD_BEARER(
            "방패 투사",
            FoxRole.RAPID,
            FoxRoute.RAPID,
            2,
            Items.SHIELD,
            1050.0,
            3.5,
            60.0,
            7,
            0.20,
            EvolutionRecipe.visible(RivalKind.BREEZE, 30, RivalKind.POLAR_BEAR, 20)
    ),
    BELL_KEEPER(
            "종지기 여우",
            FoxRole.TEAM_CONTROL,
            FoxRoute.TEAM_CONTROL,
            1,
            Items.BELL,
            650.0,
            5.0,
            60.0,
            7,
            0.0,
            EvolutionRecipe.visible(RivalKind.PHANTOM, 14)
    ),
    BEACON_KEEPER(
            "봉화지기",
            FoxRole.TEAM_CONTROL,
            FoxRoute.TEAM_CONTROL,
            2,
            Items.BEACON,
            850.0,
            4.0,
            72.0,
            6,
            0.30,
            EvolutionRecipe.visible(RivalKind.PHANTOM, 50, RivalKind.POLAR_BEAR, 25)
    ),
    OMINOUS_HEXER(
            "불길한 주술사",
            FoxRole.TEAM_CONTROL,
            FoxRoute.TEAM_CONTROL,
            2,
            Items.OMINOUS_BOTTLE,
            700.0,
            8.0,
            72.0,
            6,
            0.12,
            EvolutionRecipe.visible(RivalKind.PHANTOM, 50, RivalKind.CREEPER, 30)
    ),
    TRACKER(
            "추적자 여우",
            FoxRole.TARGET_SPECIALIST,
            FoxRoute.TARGET_SPECIALIST,
            1,
            Items.COMPASS,
            550.0,
            8.0,
            52.0,
            7,
            0.0,
            EvolutionRecipe.visible(RivalKind.CREEPER, 16)
    ),
    FIREWORK_PIERCER(
            "폭죽 관통수",
            FoxRole.TARGET_SPECIALIST,
            FoxRoute.TARGET_SPECIALIST,
            2,
            Items.FIREWORK_ROCKET,
            650.0,
            10.0,
            56.0,
            5,
            0.0,
            EvolutionRecipe.visible(RivalKind.CREEPER, 60, RivalKind.BREEZE, 30)
    ),
    BIG_GAME_TRACKER(
            "거물 추적자",
            FoxRole.TARGET_SPECIALIST,
            FoxRoute.TARGET_SPECIALIST,
            2,
            Items.SPYGLASS,
            750.0,
            11.0,
            96.0,
            8,
            0.0,
            EvolutionRecipe.visible(RivalKind.CREEPER, 60, RivalKind.POLAR_BEAR, 30)
    ),
    ECHO_FOX(
            "메아리 여우",
            FoxRole.HIGH_CEILING,
            FoxRoute.HIGH_CEILING,
            1,
            Items.ECHO_SHARD,
            700.0,
            7.0,
            76.0,
            8,
            0.0,
            EvolutionRecipe.visible(RivalKind.POLAR_BEAR, 18)
    ),
    MACE_EXECUTIONER(
            "천벌의 집행자",
            FoxRole.HIGH_CEILING,
            FoxRoute.HIGH_CEILING,
            2,
            Items.MACE,
            900.0,
            4.5,
            AdversaryBalance.MACE_STRIKE_DAMAGE,
            AdversaryBalance.MACE_STRIKE_INTERVAL_TICKS,
            0.0,
            EvolutionRecipe.visible(RivalKind.POLAR_BEAR, 80, RivalKind.BREEZE, 40)
    ),
    SCULK_CORE(
            "스컬크 재앙핵",
            FoxRole.HIGH_CEILING,
            FoxRoute.HIGH_CEILING,
            2,
            Items.SCULK_CATALYST,
            800.0,
            13.0,
            AdversaryBalance.SCULK_DETONATION_DAMAGE,
            AdversaryBalance.SCULK_ATTACK_INTERVAL_TICKS,
            0.0,
            EvolutionRecipe.visible(RivalKind.POLAR_BEAR, 100, RivalKind.PHANTOM, 50, RivalKind.CREEPER, 40)
    );

    private final String displayName;
    private final FoxRole role;
    private final FoxRoute route;
    private final int stage;
    private final Item heldItem;
    private final double defaultMaxHealth;
    private final double defaultRange;
    private final double defaultDamage;
    private final int defaultAttackIntervalTicks;
    private final double defaultDamageReduction;
    private final EvolutionRecipe defaultRecipe;

    FoxForm(
            String displayName,
            FoxRole role,
            FoxRoute route,
            int stage,
            Item heldItem,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            double damageReduction,
            EvolutionRecipe recipe
    ) {
        this.displayName = displayName;
        this.role = role;
        this.route = route;
        this.stage = stage;
        this.heldItem = heldItem;
        this.defaultMaxHealth = maxHealth;
        this.defaultRange = range;
        this.defaultDamage = damage;
        this.defaultAttackIntervalTicks = attackIntervalTicks;
        this.defaultDamageReduction = damageReduction;
        this.defaultRecipe = recipe;
    }

    public String displayName() {
        return displayName;
    }

    public FoxRole role() {
        return role;
    }

    public Optional<FoxRoute> route() {
        return Optional.ofNullable(route);
    }

    public int stage() {
        return stage;
    }

    public Item heldItem() {
        return heldItem;
    }

    public double maxHealth() {
        return AdversaryBalance.formValue(this, "maxHealth", defaultMaxHealth);
    }

    public double range() {
        return AdversaryBalance.formValue(this, "range", defaultRange);
    }

    public double damage() {
        return AdversaryBalance.formValue(this, "damage", defaultDamage);
    }

    public int attackIntervalTicks() {
        return Math.max(1, AdversaryBalance.formInt(
                this,
                "attackIntervalTicks",
                defaultAttackIntervalTicks
        ));
    }

    public double damageReduction() {
        return Math.max(0.0, Math.min(1.0, AdversaryBalance.formValue(
                this,
                "damageReduction",
                defaultDamageReduction
        )));
    }

    public Optional<EvolutionRecipe> recipe() {
        if (defaultRecipe == null) {
            return Optional.empty();
        }
        EnumMap<RivalKind, Integer> requirements = new EnumMap<>(RivalKind.class);
        for (RivalKind kind : RivalKind.values()) {
            int configured = AdversaryBalance.evolutionRequirement(
                    this,
                    kind,
                    defaultRecipe.required(kind)
            );
            if (configured > 0) {
                requirements.put(kind, configured);
            }
        }
        return requirements.isEmpty()
                ? Optional.of(defaultRecipe)
                : Optional.of(new EvolutionRecipe(requirements, defaultRecipe.hidden()));
    }

    public boolean isBase() {
        return stage == 0;
    }

    public boolean isIntermediate() {
        return stage == 1;
    }

    public boolean isFinal() {
        return stage == 2;
    }

    public boolean usesSpecialAttack() {
        return this == MACE_EXECUTIONER || this == SCULK_CORE;
    }

    public FoxForm parentForm() {
        return switch (this) {
            case GOLDEN_FANG, SHIELD_BEARER -> BREEZE;
            case BEACON_KEEPER, OMINOUS_HEXER -> BELL_KEEPER;
            case FIREWORK_PIERCER, BIG_GAME_TRACKER -> TRACKER;
            case MACE_EXECUTIONER, SCULK_CORE -> ECHO_FOX;
            case BREEZE, BELL_KEEPER, TRACKER, ECHO_FOX -> BASE;
            case BASE -> BASE;
        };
    }

    public static FoxForm intermediateFor(FoxRoute route) {
        return switch (route) {
            case RAPID -> BREEZE;
            case TEAM_CONTROL -> BELL_KEEPER;
            case TARGET_SPECIALIST -> TRACKER;
            case HIGH_CEILING -> ECHO_FOX;
        };
    }

    public static List<FoxForm> finalsFor(FoxRoute route) {
        return switch (route) {
            case RAPID -> List.of(GOLDEN_FANG, SHIELD_BEARER);
            case TEAM_CONTROL -> List.of(BEACON_KEEPER, OMINOUS_HEXER);
            case TARGET_SPECIALIST -> List.of(FIREWORK_PIERCER, BIG_GAME_TRACKER);
            case HIGH_CEILING -> List.of(MACE_EXECUTIONER, SCULK_CORE);
        };
    }
}
