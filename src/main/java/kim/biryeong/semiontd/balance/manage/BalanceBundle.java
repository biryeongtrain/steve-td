package kim.biryeong.semiontd.balance.manage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.MonsterScalingConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TraitBalanceConfig;
import kim.biryeong.semiontd.config.WaveConfig;

/** The seven immutable configuration snapshots managed together. Never changes global runtime state. */
public record BalanceBundle(TowerBalanceConfig tower, AugmentConfig augment, WaveConfig wave,
                            SummonConfig summon, TraitBalanceConfig trait, EconomyConfig economy,
                            MonsterScalingConfig monsterScaling) {
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    public BalanceBundle {
        Objects.requireNonNull(tower);
        Objects.requireNonNull(augment);
        Objects.requireNonNull(wave);
        Objects.requireNonNull(summon);
        Objects.requireNonNull(trait);
        Objects.requireNonNull(economy);
        Objects.requireNonNull(monsterScaling);
        tower.validateForRuntime();
        augment.validate();
        wave.validate();
    }

    public static BalanceBundle defaults() {
        return new BalanceBundle(TowerBalanceConfig.defaultConfig(), AugmentConfig.defaults(),
                WaveConfig.defaultConfig(), SummonConfig.defaultConfig(), TraitBalanceConfig.defaultConfig(),
                EconomyConfig.defaultConfig(), MonsterScalingConfig.defaultConfig());
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("tower", GSON.toJsonTree(tower));
        json.add("augment", augment.toJson());
        json.add("wave", GSON.toJsonTree(wave));
        json.add("summon", GSON.toJsonTree(summon));
        json.add("trait", GSON.toJsonTree(trait));
        json.add("economy", GSON.toJsonTree(economy));
        json.add("monsterScaling", GSON.toJsonTree(monsterScaling));
        return json;
    }

    public static BalanceBundle fromJson(JsonObject json) {
        if (!json.keySet().equals(java.util.Set.of("tower", "augment", "wave", "summon", "trait", "economy", "monsterScaling"))) {
            throw new IllegalArgumentException("Balance bundle must contain exactly seven domains.");
        }
        requireFinite(json);
        return new BalanceBundle(GSON.fromJson(json.get("tower"), TowerBalanceConfig.class),
                AugmentConfig.fromJson(json.getAsJsonObject("augment")),
                GSON.fromJson(json.get("wave"), WaveConfig.class),
                GSON.fromJson(json.get("summon"), SummonConfig.class),
                GSON.fromJson(json.get("trait"), TraitBalanceConfig.class),
                GSON.fromJson(json.get("economy"), EconomyConfig.class),
                GSON.fromJson(json.get("monsterScaling"), MonsterScalingConfig.class));
    }

    public String revision() {
        return digest(canonical(toJson()));
    }

    static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String canonical(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            new TreeMap<>(element.getAsJsonObject().asMap()).forEach((key, value) ->
                    sorted.add(key, com.google.gson.JsonParser.parseString(canonical(value))));
            return sorted.toString();
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(value -> array.add(com.google.gson.JsonParser.parseString(canonical(value))));
            return array.toString();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsBigDecimal().stripTrailingZeros().toPlainString();
        }
        return element.toString();
    }

    private static void requireFinite(JsonElement element) {
        if (element.isJsonObject()) {element.getAsJsonObject().asMap().values().forEach(BalanceBundle::requireFinite);}
        else if (element.isJsonArray()) {element.getAsJsonArray().forEach(BalanceBundle::requireFinite);}
        else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                && !Double.isFinite(element.getAsDouble())) {
            throw new IllegalArgumentException("Balance values must be finite.");
        }
    }
}
