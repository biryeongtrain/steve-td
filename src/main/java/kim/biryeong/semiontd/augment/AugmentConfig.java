package kim.biryeong.semiontd.augment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Validated immutable match snapshot. File IO and retaining a last-good snapshot belong to the loader. */
public record AugmentConfig(boolean enabled, boolean publicPoolEnabled,
                            Map<String, Integer> rarityWeights,
                            Map<String, Map<String, Double>> parameters, Set<String> disabledIds) {
    private static final Map<String, Integer> DEFAULT_WEIGHTS = Map.of(
            "SSS", 3, "SSG", 14, "SGG", 23, "GGG", 18, "SGP", 22, "GGP", 11, "GPP", 7, "PPP", 2);
    private static final Map<String, Map<String, Double>> DEFAULT_PARAMETERS = defaultParameters();
    private static final AugmentConfig DEFAULT = loadBundledDefaults();

    public AugmentConfig {
        rarityWeights = Map.copyOf(rarityWeights == null ? DEFAULT_WEIGHTS : rarityWeights);
        Map<String, Map<String, Double>> copy = new TreeMap<>();
        DEFAULT_PARAMETERS.forEach((id, values) -> copy.put(id, new TreeMap<>(values)));
        if (parameters != null) {
            parameters.forEach((id, values) -> {
                String normalized = AugmentCatalog.effectId(id);
                Map<String, Double> defaults = DEFAULT_PARAMETERS.get(normalized);
                if (defaults == null) {throw new IllegalArgumentException("Unknown augment: " + id);}
                values.forEach((key, value) -> {
                    if (!defaults.containsKey(key)) {throw new IllegalArgumentException("Unknown augment parameter: " + id + "." + key);}
                    validateParameter(key, value);
                    copy.get(normalized).put(key, value);
                });
            });
        }
        copy.replaceAll((id, values) -> Map.copyOf(values));
        parameters = Map.copyOf(copy);
        disabledIds = disabledIds == null ? Set.of() : disabledIds.stream().map(AugmentCatalog::normalizeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        validateValues(rarityWeights, disabledIds);
    }

    public static AugmentConfig defaults() {
        return DEFAULT;
    }

    public boolean isEnabled(String cardId) {
        return AugmentCatalog.find(cardId).isPresent() && !disabledIds.contains(AugmentCatalog.normalizeId(cardId))
                && !disabledIds.contains(AugmentCatalog.effectId(cardId));
    }

    public double parameter(String cardId, String key, double fallback) {
        return parametersFor(cardId).getOrDefault(key, fallback);
    }

    public Map<String, Double> parametersFor(String cardId) {
        return parameters.getOrDefault(AugmentCatalog.effectId(cardId), Map.of());
    }

    public void validate() {
        validateValues(rarityWeights, disabledIds);
        parameters.values().forEach(values -> values.forEach(AugmentConfig::validateParameter));
    }

    public static AugmentConfig fromJson(JsonObject json) {
        return parseJson(json, DEFAULT);
    }

    private static AugmentConfig parseJson(JsonObject json, AugmentConfig baseline) {
        if (json == null) {throw new IllegalArgumentException("Augment config must be an object.");}
        Set<String> known = Set.of("enabled", "publicPoolEnabled", "rarityWeights", "parameters", "disabledIds");
        for (String key : json.keySet()) {
            if (!known.contains(key)) {throw new IllegalArgumentException("Unknown augment config key: " + key);}
        }
        boolean enabled = booleanValue(json, "enabled", baseline != null && baseline.enabled());
        boolean publicPool = booleanValue(json, "publicPoolEnabled", baseline != null && baseline.publicPoolEnabled());
        Map<String, Integer> weights = new TreeMap<>(baseline == null ? DEFAULT_WEIGHTS : baseline.rarityWeights());
        if (json.has("rarityWeights")) {
            for (var entry : json.getAsJsonObject("rarityWeights").entrySet()) {
                double value = number(entry.getValue());
                if (value != Math.rint(value) || value < 0 || value > 100) {
                    throw new IllegalArgumentException("Rarity weights must be integers in [0,100].");
                }
                weights.put(entry.getKey(), (int) value);
            }
        }
        Map<String, Map<String, Double>> parameters = new TreeMap<>(baseline == null ? Map.of() : baseline.parameters());
        if (json.has("parameters")) {
            Set<String> suppliedIds = new TreeSet<>();
            for (var card : json.getAsJsonObject("parameters").entrySet()) {
                String normalizedId = AugmentCatalog.effectId(card.getKey());
                Map<String, Double> values = new TreeMap<>(parameters.getOrDefault(normalizedId, Map.of()));
                for (var parameter : card.getValue().getAsJsonObject().entrySet()) {
                    values.put(parameter.getKey(), number(parameter.getValue()));
                }
                if (!suppliedIds.add(normalizedId)) {
                    throw new IllegalArgumentException("Duplicate augment parameter ID: " + card.getKey());
                }
                parameters.put(normalizedId, values);
            }
        }
        Set<String> disabled = new TreeSet<>(baseline == null ? Set.of() : baseline.disabledIds());
        if (json.has("disabledIds")) {
            disabled.clear();
            for (JsonElement value : json.getAsJsonArray("disabledIds")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("disabledIds must contain strings.");
                }
                disabled.add(value.getAsString());
            }
        }
        return new AugmentConfig(enabled, publicPool, weights, parameters, disabled);
    }

    private static AugmentConfig loadBundledDefaults() {
        try (var stream = AugmentConfig.class.getResourceAsStream("/semiontd/balance-defaults/augment_balance.json")) {
            if (stream == null) {return new AugmentConfig(false, false, DEFAULT_WEIGHTS, Map.of(), Set.of());}
            return parseJson(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject(), null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read bundled augment defaults.", exception);
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("publicPoolEnabled", publicPoolEnabled);
        JsonObject weights = new JsonObject();
        new TreeMap<>(rarityWeights).forEach(weights::addProperty);
        json.add("rarityWeights", weights);
        JsonObject cards = new JsonObject();
        new TreeMap<>(parameters).forEach((id, values) -> {
            JsonObject card = new JsonObject();
            new TreeMap<>(values).forEach(card::addProperty);
            cards.add(id, card);
        });
        json.add("parameters", cards);
        JsonArray disabled = new JsonArray();
        new TreeSet<>(disabledIds).forEach(disabled::add);
        json.add("disabledIds", disabled);
        return json;
    }

    /** Includes card identity, eligibility metadata, and the offer algorithm revision. */
    public String version() {
        JsonObject hashInput = toJson();
        hashInput.addProperty("offerRulesVersion", AugmentCatalog.OFFER_RULES_VERSION);
        JsonArray definitions = new JsonArray();
        AugmentCatalog.definitions().stream().sorted(java.util.Comparator.comparing(AugmentDefinition::id)).forEach(card -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", card.id());
            entry.addProperty("displayName", card.displayName());
            entry.addProperty("description", card.description());
            entry.addProperty("rarity", card.rarity().name());
            entry.addProperty("category", card.category().name());
            entry.addProperty("familyKey", card.familyKey());
            entry.addProperty("requiredJobId", card.requiredJobId());
            entry.addProperty("safe", card.safe());
            entry.addProperty("risky", card.risky());
            entry.addProperty("towerAugment", card.towerAugment());
            entry.addProperty("reserve", card.reserve());
            JsonArray rounds = new JsonArray();
            new TreeSet<>(card.milestoneRounds()).forEach(rounds::add);
            entry.add("milestoneRounds", rounds);
            JsonArray conflicts = new JsonArray();
            new TreeSet<>(card.conflicts()).forEach(conflicts::add);
            entry.add("conflicts", conflicts);
            definitions.add(entry);
        });
        hashInput.add("definitions", definitions);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(hashInput.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        if (!object.has(key)) {return fallback;}
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean.");
        }
        return value.getAsBoolean();
    }

    private static double number(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Augment parameters must be numbers.");
        }
        return value.getAsDouble();
    }

    private static void validateValues(Map<String, Integer> weights, Set<String> disabled) {
        if (!weights.keySet().equals(DEFAULT_WEIGHTS.keySet())
                || weights.values().stream().anyMatch(value -> value == null || value < 0 || value > 100)
                || weights.values().stream().mapToInt(Integer::intValue).sum() != 100) {
            throw new IllegalArgumentException("Known rarity combination weights must sum to 100.");
        }
        for (String id : disabled) {
            AugmentDefinition card = AugmentCatalog.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown augment: " + id));
            if (card.reserve()) {throw new IllegalArgumentException("Fallback rewards cannot be disabled.");}
        }
    }

    private static void validateParameter(String key, Double value) {
        ParameterLimits limits = parameterLimits(key);
        if (value == null || !Double.isFinite(value) || value < limits.min() || value > limits.max()
                || limits.integer() && value != Math.rint(value)) {
            throw new IllegalArgumentException("Invalid augment parameter: " + key);
        }
    }

    /** Shared by config validation and the balance editor; does not authorize unknown keys. */
    public static ParameterLimits parameterLimits(String key) {
        boolean fraction = Set.of("damageReduction", "longDamageReduction", "vanguardDamageReduction", "vanguardDamagePenalty",
                "otherDamagePenalty", "damageThreshold", "healthThreshold", "redirectRatio", "payoutMultiplier", "bodyMultiplier",
                "echoRatio", "bonusRatio", "healRatio", "repeatDamageRatio", "inheritRatio", "copyRatio", "childRatio",
                "statRatio", "healthRatio", "healthLossRatio", "healthCapRatio", "reviveHealthRatio", "intervalRatio",
                "refundRatio", "transferRatio", "decayReduction", "waitReduction", "slow", "chill", "statReversalChance").contains(key);
        boolean positiveInteger = key.endsWith("Ticks") || Set.of("maxStacks", "charges", "ticketCount", "repaymentCount",
                "targetCount", "maxCharges", "hatchWaves", "maxShells", "shellTargets", "maxHeals", "attacksPerCharge", "mineTargets", "scoreMultiplier",
                "attacks", "attacksRequired", "centers", "chainTargets", "chestsPerStack", "companions", "copiesPerSummon",
                "deathsPerCharge", "deathsPerSummon", "distinctSkills", "everyAttacks", "explosions", "extraShots", "extraTargets",
                "fastBeats", "growthRounds", "healTargets", "hitsRequired", "initialMana", "initialStacks", "killsRequired",
                "maxAdditional", "maxAllies", "maxAttempts", "maxCopies", "maxDepth", "maxGeneration", "maxNeighbors",
                "maxRelays", "maxShots", "maxSources", "maxTargets", "maxTowers", "maxExtraAttacks", "maxChainDepth",
                "normalBeats", "openingAttacks", "openingWater", "requiredKinds", "requiredLeaderKinds", "requiredLinks",
                "revivalCount", "roundCap", "roundReduction", "shots", "spawnCount", "stacks", "survivorCap",
                "targets", "targetsPerRelay", "tickets", "transfersPerCharge", "waterPerCharge", "yardRadius", "gaugePerVolley").contains(key);
        boolean positive = Set.of("echoRatio", "bodyMultiplier", "healthMultiplier", "attackMultiplier", "costMultiplier", "damagePerHitCap").contains(key);
        boolean integer = positiveInteger || Set.of("amount", "ticketValue", "advanceCap", "incomeBonus", "matchIncomeCap", "roundBonusCap", "neighborCount").contains(key);
        double min = positiveInteger || key.equals("emeraldPerShell") ? 1 : positive ? Double.MIN_VALUE : 0;
        return new ParameterLimits(min, fraction ? 1 : 1_000_000, integer);
    }

    public record ParameterLimits(double min, double max, boolean integer) {}

    private static Map<String, Map<String, Double>> defaultParameters() {
        Map<String, Map<String, Double>> cards = new TreeMap<>();
        AugmentCatalog.definitions().forEach(card -> cards.put(AugmentCatalog.effectId(card.id()), Map.of()));
        JobAugmentCatalog.entries().forEach(entry -> cards.put(entry.definition().id(), entry.parameters()));
        put(cards, "tactical_designation_1", "damageBonus", .35, "damageReduction", .20);
        put(cards, "tactical_designation_2", "damageBonus", .65, "damageReduction", .30);
        put(cards, "tactical_designation_3", "damageBonus", 1.0, "damageReduction", .40);
        put(cards, "triangle_formation", "damageBonus", .20, "damageReduction", .15, "radius", 4, "neighborCount", 2);
        put(cards, "engagement_plan", "quickDamageBonus", .35, "longDamageBonus", .25, "longDamageReduction", .15, "transitionTicks", 160);
        put(cards, "emergency_loan", "advanceMultiplier", 3, "advanceCap", 300, "debtMultiplier", 4.0 / 3.0, "repaymentCount", 4);
        put(cards, "folding_barricade_blueprint", "damagePerHitCap", 15);
        put(cards, "additional_payload", "costMultiplier", 1.25, "healthMultiplier", 1.65, "supportMultiplier", 1.65);
        put(cards, "twin_squadron", "damageBonus", .30);
        put(cards, "overheat_core", "damageBonus", 1.0, "penaltyPerStack", .06, "maxStacks", 5);
        put(cards, "frontline_specialization", "vanguardDamagePenalty", .25, "vanguardDamageReduction", .45,
                "artilleryDamageBonus", .70, "artilleryIncomingMultiplier", 1.25);
        put(cards, "forecast_offensive", "echoRatio", .50);
        put(cards, "support_performance", "targetCount", 3, "incomeBonus", 6, "matchIncomeCap", 24);
        put(cards, "battlefield_mastery", "bonusPerStack", .15, "maxStacks", 4, "damageThreshold", .40);
        put(cards, "biased_armor", "selectedMultiplier", .55, "oppositeMultiplier", 1.35);
        put(cards, "cash_settlement", "diamondMultiplier", 5);
        put(cards, "forbidden_blueprint", "ticketCount", 2, "ticketValue", 450, "payoutMultiplier", .90);
        put(cards, "low_pressure_high_yield", "bonusRatio", .40, "roundBonusCap", 20, "bodyMultiplier", .70);
        put(cards, "finishing_fire_1", "damageBonus", .40);
        put(cards, "finishing_fire_2", "damageBonus", .70);
        put(cards, "finishing_fire_3", "damageBonus", 1.10);
        put(cards, "independent_position", "damageBonus", .30, "damageReduction", .15, "radius", 4);
        put(cards, "winning_barrage", "damageBonus", .60, "charges", 3);
        put(cards, "decisive_delivery", "healthMultiplier", 2.0, "attackMultiplier", 1.80);
        put(cards, "domino_fire", "overkillRatio", .90, "damageCapRatio", .75, "radius", 4);
        put(cards, "one_man_show", "damageBonus", 1.75, "maxHealthBonus", .60, "otherDamagePenalty", .20);
        put(cards, "wartime_economy", "payoutMultiplier", .65, "damageBonus", .65, "maxHealthBonus", .40);
        put(cards, "giant_hunter_call", "minimumRange", 3, "maxHealthDamageRatio", .12, "bossMaxHealthDamageRatio", .03);
        put(cards, "capacitor_post_blueprint", "chargeTicks", 40, "maxCharges", 3, "chargeDamage", 110);
        put(cards, "starlight_cocoon_call", "hatchWaves", 2, "hatchedHealth", 800, "hatchedRange", 5, "hatchedDamage", 170, "hatchedIntervalTicks", 30);
        put(cards, "ordnance_factory_call", "emeraldPerShell", 100, "maxShells", 4, "shellDamage", 200, "shellRadius", 3,
                "shellTargets", 5, "shellIntervalTicks", 100);
        put(cards, "emergency_bell_blueprint", "healRatio", .40, "healCap", 150, "healthThreshold", .40, "maxHeals", 3, "checkTicks", 20);
        put(cards, "pulse_relay_blueprint", "attacksPerCharge", 5, "chargedDamageRatio", 1.0);
        put(cards, "barrier_core_call", "redirectRatio", .35);
        put(cards, "ambush_workshop_blueprint", "triggerRadius", 1.25, "damageRadius", 2, "mineDamage", 170, "mineTargets", 2, "checkTicks", 5);
        AugmentCatalog.definitions().stream().filter(AugmentDefinition::towerAugment).forEach(card -> {
            Map<String, Double> values = new TreeMap<>(cards.get(card.id()));
            values.put("healthPerTier", 1.0);
            values.put("powerPerTier", .5);
            values.put("areaPerTier", .15);
            cards.put(card.id(), Map.copyOf(values));
        });
        for (AugmentRarity rarity : AugmentRarity.values()) {
            String suffix = rarity.name().toLowerCase(java.util.Locale.ROOT);
            int index = rarity.ordinal();
            put(cards, "reserve_diamonds_" + suffix, "amount", new int[]{150, 300, 600}[index]);
            put(cards, "reserve_income_" + suffix, "amount", new int[]{15, 30, 60}[index]);
            put(cards, "reserve_production_" + suffix, "amount", new int[]{2, 3, 6}[index]);
        }
        return Map.copyOf(cards);
    }

    private static void put(Map<String, Map<String, Double>> cards, String id, Object... entries) {
        Map<String, Double> values = new TreeMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], ((Number) entries[i + 1]).doubleValue());
        }
        cards.put(AugmentCatalog.normalizeId(id), Map.copyOf(values));
    }
}
