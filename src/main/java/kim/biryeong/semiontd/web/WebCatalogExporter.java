package kim.biryeong.semiontd.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentDescriptions;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.trait.TraitRegistry;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;

public final class WebCatalogExporter {
    public static final int SCHEMA_VERSION = 3;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();
    private static final Gson HASH_GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static volatile String currentVersion;

    private WebCatalogExporter() {
    }

    public static synchronized CatalogDocument export(Path configDir) throws IOException {
        CatalogDocument document = snapshot(System.currentTimeMillis());
        return writeDocument(configDir, document);
    }

    private static CatalogDocument writeDocument(Path configDir, CatalogDocument document) throws IOException {
        if (configDir != null) {
            Path catalogDir = configDir.resolve("web_catalog");
            Path versionsDir = catalogDir.resolve("versions");
            Files.createDirectories(versionsDir);
            Path versionPath = versionsDir.resolve(document.versionHash() + ".json");
            if (Files.notExists(versionPath)) {
                writeAtomically(versionPath, GSON.toJson(document));
            }
            writeAtomically(catalogDir.resolve("current.json"), GSON.toJson(document));
        }
        currentVersion = document.versionHash();
        return document;
    }

    public static synchronized CatalogDocument snapshot(long generatedAtEpochMillis) {
        return snapshot(generatedAtEpochMillis, WaveConfig.defaultConfig(), EconomyConfig.defaultConfig(),
                SummonConfig.defaultConfig());
    }

    public static synchronized CatalogDocument export(
            Path configDir, WaveConfig waves, EconomyConfig economy, SummonConfig summonBalance
    ) throws IOException {
        return writeDocument(configDir, snapshot(System.currentTimeMillis(), waves, economy, summonBalance));
    }

    public static synchronized CatalogDocument snapshot(
            long generatedAtEpochMillis, WaveConfig waves, EconomyConfig economy, SummonConfig summonBalance
    ) {
        return snapshot(generatedAtEpochMillis, waves, economy, summonBalance, AugmentConfig.defaults());
    }

    public static synchronized CatalogDocument export(
            Path configDir, WaveConfig waves, EconomyConfig economy, SummonConfig summonBalance, AugmentConfig augments
    ) throws IOException {
        return writeDocument(configDir, snapshot(System.currentTimeMillis(), waves, economy, summonBalance, augments));
    }

    public static synchronized CatalogDocument snapshot(
            long generatedAtEpochMillis, WaveConfig waves, EconomyConfig economy, SummonConfig summonBalance,
            AugmentConfig augmentConfig
    ) {
        List<ProductionTowerCatalog.CatalogEntry> catalogEntries = ProductionTowerCatalog.all().stream()
                .sorted(Comparator.comparing(entry -> entry.type().id()))
                .toList();
        List<SemionJob> jobs = JobRegistry.all().stream()
                .sorted(Comparator.comparing(job -> job.id().toString()))
                .toList();

        Map<String, String> towerBuilders = new TreeMap<>();
        for (ProductionTowerCatalog.CatalogEntry entry : catalogEntries) {
            List<SemionJob> owners = jobs.stream()
                    .filter(job -> job.includesTowerInCatalog(entry.type()))
                    .toList();
            if (entry.availability() == ProductionTowerCatalog.Availability.AUGMENT) {
                if (!owners.isEmpty()) {
                    throw new IllegalStateException("Augment tower cannot belong to a builder: " + entry.type().id());
                }
                continue;
            }
            if (owners.size() != 1) {
                throw new IllegalStateException("Tower must belong to exactly one builder: "
                        + entry.type().id() + " owners=" + owners.stream().map(job -> job.id().toString()).toList());
            }
            towerBuilders.put(entry.type().id(), owners.getFirst().id().toString());
        }

        List<BuilderEntry> builders = jobs.stream()
                .map(job -> new BuilderEntry(
                        job.id().toString(),
                        job.displayName().getString(),
                        job.description().stream().map(component -> component.getString()).toList(),
                        towerBuilders.entrySet().stream()
                                .filter(entry -> entry.getValue().equals(job.id().toString()))
                                .map(Map.Entry::getKey)
                                .toList(),
                        JobRegistry.officialBuilders().contains(job) ? "OFFICIAL" : "CREATIVE",
                        JobRegistry.isEnabled(job)
                ))
                .filter(builder -> !builder.towerIds().isEmpty())
                .toList();

        List<TowerEntry> towers = catalogEntries.stream()
                .map(entry -> towerEntry(entry, towerBuilders.get(entry.type().id())))
                .toList();
        List<UpgradeEntry> upgrades = catalogEntries.stream()
                .flatMap(entry -> ProductionTowerCatalog.upgrades(entry.type()).stream()
                        .sorted(Comparator.comparing(option -> option.id()))
                        .map(option -> new UpgradeEntry(
                                entry.type().id(),
                                option.id(),
                                option.displayName(),
                                option.targetType().id(),
                                TowerBalanceRuntime.upgradeCost(entry.type(), option.id(), option.mineralCost())
                        )))
                .toList();
        List<TraitEntry> traits = TraitRegistry.all().stream()
                .sorted(Comparator.comparing(trait -> trait.id().toString()))
                .map(trait -> new TraitEntry(
                        trait.id().toString(),
                        trait.version(),
                        trait.displayName().getString(),
                        trait.description().stream().map(Component::getString).toList()
                ))
                .toList();
        List<SummonEntry> summons = SummonRegistry.all().stream()
                .sorted(Comparator.comparing(summon -> summon.id()))
                .map(summon -> new SummonEntry(summon.id(), summon.displayName()))
                .toList();
        Map<String, Map<String, Double>> abilities = sortedAbilities(TowerBalanceRuntime.current().abilities());
        List<AugmentEntry> augments = AugmentCatalog.definitions().stream()
                .sorted(Comparator.comparing(definition -> definition.id()))
                .map(definition -> new AugmentEntry(
                        definition.id(), definition.displayName(), definition.rarity().name(), definition.category().name(),
                        definition.familyKey(), definition.safe(), definition.risky(), definition.towerAugment(),
                        definition.reserve(), definition.milestoneRounds().stream().sorted().toList(),
                        definition.conflicts().stream().sorted().toList(), AugmentDescriptions.describe(definition, augmentConfig),
                        augmentConfig.enabled() && augmentConfig.isEnabled(definition.id())
                                && (definition.reserve() || augmentConfig.publicPoolEnabled()),
                        new TreeMap<>(augmentConfig.parameters().getOrDefault(definition.id(), Map.of()))
                ))
                .toList();
        CatalogHashInput hashInput = new CatalogHashInput(SCHEMA_VERSION, builders, towers, upgrades, traits, summons,
                abilities, waves, economy, summonBalance, augmentConfig.version(), augments, augmentConfig.toJson());
        String versionHash = sha256(HASH_GSON.toJson(canonicalJson(HASH_GSON.toJsonTree(hashInput))));
        return new CatalogDocument(
                SCHEMA_VERSION,
                versionHash,
                Math.max(0L, generatedAtEpochMillis),
                builders,
                towers,
                upgrades,
                traits,
                summons,
                abilities,
                waves,
                economy,
                summonBalance,
                augmentConfig.version(),
                augments,
                augmentConfig.toJson()
        );
    }

    public static Optional<String> currentVersion() {
        return Optional.ofNullable(currentVersion);
    }

    public static void clearCurrentVersion() {
        currentVersion = null;
    }

    private static TowerEntry towerEntry(ProductionTowerCatalog.CatalogEntry entry, String builderId) {
        TowerType type = entry.type();
        EntityVisual visual = type.visual();
        return new TowerEntry(
                type.id(),
                type.displayName(),
                builderId,
                entry.tier(),
                type.category().name(),
                type.mineralCost(),
                type.maxHealth(),
                type.range(),
                type.damage(),
                type.attackIntervalTicks(),
                type.aggroPriority(),
                type.description().stream().map(line -> SemionText.mini(line).getString()).toList(),
                new VisualEntry(
                        visual.entityTypeId(),
                        visual.blockbenchModelId(),
                        visual.scale(),
                        jsonSafeProperties(visual.properties())
                ),
                entry.availability().name(),
                entry.augmentId()
        );
    }

    private static Map<String, Object> jsonSafeProperties(Map<String, Object> properties) {
        TreeMap<String, Object> values = new TreeMap<>();
        properties.forEach((key, value) -> values.put(key, jsonSafeValue(value)));
        return values;
    }

    private static Object jsonSafeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(WebCatalogExporter::jsonSafeValue).orElse(null);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, nested) -> values.put(String.valueOf(key), jsonSafeValue(nested)));
            return values;
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.ArrayList<Object> values = new java.util.ArrayList<>();
            iterable.forEach(nested -> values.add(jsonSafeValue(nested)));
            return values;
        }
        return value.toString();
    }

    private static Map<String, Map<String, Double>> sortedAbilities(Map<String, Map<String, Double>> abilities) {
        TreeMap<String, Map<String, Double>> sorted = new TreeMap<>();
        abilities.forEach((group, values) -> sorted.put(group, new TreeMap<>(values)));
        return sorted;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonElement canonicalJson(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.add(entry.getKey(), canonicalJson(entry.getValue())));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray ordered = new JsonArray();
            value.getAsJsonArray().forEach(element -> ordered.add(canonicalJson(element)));
            return ordered;
        }
        return value;
    }

    private static void writeAtomically(Path target, String contents) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record CatalogHashInput(
            int schemaVersion,
            List<BuilderEntry> builders,
            List<TowerEntry> towers,
            List<UpgradeEntry> upgrades,
            List<TraitEntry> traits,
            List<SummonEntry> summons,
            Map<String, Map<String, Double>> abilities,
            WaveConfig waves,
            EconomyConfig economy,
            SummonConfig summonBalance,
            String augmentVersion,
            List<AugmentEntry> augments,
            JsonObject augmentRules
    ) {
    }

    public record CatalogDocument(
            int schemaVersion,
            String versionHash,
            long generatedAtEpochMillis,
            List<BuilderEntry> builders,
            List<TowerEntry> towers,
            List<UpgradeEntry> upgrades,
            List<TraitEntry> traits,
            List<SummonEntry> summons,
            Map<String, Map<String, Double>> abilities,
            WaveConfig waves,
            EconomyConfig economy,
            SummonConfig summonBalance,
            String augmentVersion,
            List<AugmentEntry> augments,
            JsonObject augmentRules
    ) {
    }

    public record BuilderEntry(String id, String displayName, List<String> description, List<String> towerIds,
            String builderOrigin, Boolean builderEnabled) {
    }

    public record AugmentEntry(
            String id, String displayName, String rarity, String category, String familyKey,
            boolean safe, boolean risky, boolean towerAugment, boolean reserve,
            List<Integer> milestoneRounds, List<String> conflicts, String description,
            boolean enabled, Map<String, Double> parameters
    ) {
    }

    public record TowerEntry(
            String id,
            String displayName,
            String builderId,
            int tier,
            String category,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            List<String> description,
            VisualEntry visual,
            String availability,
            String augmentId
    ) {
    }

    public record UpgradeEntry(String fromTowerId, String id, String displayName, String toTowerId, long mineralCost) {
    }

    public record TraitEntry(String id, int version, String displayName, List<String> description) {
    }

    public record SummonEntry(String id, String displayName) {
    }

    public record VisualEntry(String entityTypeId, String blockbenchModelId, double scale, Map<String, Object> properties) {
    }
}
