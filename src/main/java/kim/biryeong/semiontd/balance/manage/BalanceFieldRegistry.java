package kim.biryeong.semiontd.balance.manage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.augment.AugmentCatalog;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.BalanceField;
import kim.biryeong.semiontd.web.WebCatalogExporter;

/** Known configuration paths only; ability values retain their native units and runtime validation. */
public final class BalanceFieldRegistry {
    private static final List<ApplyMode> MATCH = List.of(ApplyMode.NEXT_MATCH);
    private static final List<ApplyMode> PREPARE = List.of(ApplyMode.NEXT_PREPARE, ApplyMode.NEXT_MATCH);
    private static final List<ApplyMode> LIVE = List.of(ApplyMode.NOW, ApplyMode.NEXT_PREPARE, ApplyMode.NEXT_MATCH);
    private static final double MAX_INTEGER = Integer.MAX_VALUE;
    private static final double MAX_EXACT_INTEGER = 9_007_199_254_740_991.0;
    private final JsonObject defaults;
    private final Set<String> verifiedLiveTowerIds;
    private final Map<String, Metadata> metadata = new LinkedHashMap<>();

    public BalanceFieldRegistry(BalanceBundle defaults) {
        this(defaults, null, Set.of());
    }

    public BalanceFieldRegistry(BalanceBundle defaults, WebCatalogExporter.CatalogDocument catalog,
                                Set<String> verifiedLiveTowerIds) {
        this.defaults = defaults.toJson();
        this.verifiedLiveTowerIds = Set.copyOf(verifiedLiveTowerIds);
        if (catalog == null) {return;}
        Map<String, String> builders = new LinkedHashMap<>();
        catalog.builders().forEach(builder -> builders.put(builder.id(), builder.displayName()));
        catalog.towers().forEach(tower -> metadata.put("tower:" + tower.id(), new Metadata(tower.displayName(),
                String.join("\n", tower.description()), tower.builderId(), builders.get(tower.builderId()), tower.tier(), null)));
        catalog.augments().forEach(augment -> metadata.putIfAbsent("augment:" + AugmentCatalog.effectId(augment.id()), new Metadata(augment.displayName(),
                augment.description(), augment.requiredJobId(), builders.get(augment.requiredJobId()), null, augment.rarity())));
        catalog.traits().forEach(trait -> metadata.put("trait:" + trait.id().substring(trait.id().indexOf(':') + 1), new Metadata(trait.displayName(),
                String.join("\n", trait.description()), null, null, null, null)));
        catalog.summons().forEach(summon -> metadata.put("summon:" + summon.id(),
                new Metadata(summon.displayName(), "", null, null, null, null)));
    }

    public List<BalanceField> fields(BalanceBundle active, BalanceBundle scheduled) {
        return fields(active, scheduled, false);
    }

    public List<BalanceField> fields(BalanceBundle active, BalanceBundle scheduled, boolean idle) {
        Map<String, Double> values = flatten(active.toJson());
        Map<String, Double> defaultValues = flatten(defaults);
        Map<String, Double> scheduledValues = scheduled == null ? Map.of() : flatten(scheduled.toJson());
        List<BalanceField> fields = new ArrayList<>();
        values.forEach((id, value) -> {
            String domain = id.substring(0, id.indexOf(':'));
            List<String> path = path(id);
            String key = path.getLast();
            String entity = entityId(domain, path);
            Metadata names = metadata.getOrDefault(domain + ":" + entity, new Metadata(entity, "", null, null, null, null));
            Rule rule = defaultValues.containsKey(id) ? rule(domain, path, entity) : null;
            Double pending = scheduledValues.get(id);
            if (pending != null && Double.compare(pending, value) == 0) {pending = null;}
            fields.add(new BalanceField(id, domain, entity, names.name(), rule == null ? key : rule.label(), names.description(),
                    names.builderId(), names.builderName(), names.tier(), names.rarity(), rule == null ? "unknown" : rule.unit(),
                    value, defaultValues.get(id), pending, rule == null ? null : rule.min(), rule == null ? null : rule.max(),
                    rule == null ? null : rule.integer() ? 1.0 : null, rule != null,
                    rule == null ? List.of() : idle && rule.modes().contains(ApplyMode.NEXT_PREPARE) ? LIVE : rule.modes(),
                    rule != null && rule.integer() ? "integer" : "decimal",
                    rule != null && rule.integer() ? "nearest" : "none", List.of(entity),
                    "config:" + domain + id.substring(id.indexOf(':') + 1),
                    rule == null ? "의미 또는 적용 경로 미검증: 읽기 전용"
                            : rule.unit().equals("config") ? "설정 원값 그대로 입력합니다. 비율 0.2는 20%이며 단위를 자동 변환하지 않습니다. 기존 능력별 검증을 적용합니다. 이미 발동한 효과·보상·성장은 소급 재실행하지 않습니다."
                            : "설정 검증 및 명시적 필드 정책"));
        });
        return List.copyOf(fields);
    }

    static Map<String, Double> flatten(JsonObject bundle) {
        Map<String, Double> result = new java.util.TreeMap<>();
        bundle.entrySet().forEach(entry -> walk(entry.getValue(), entry.getKey() + ":", result));
        return result;
    }

    private static void walk(JsonElement value, String id, Map<String, Double> output) {
        if (value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(entry -> walk(entry.getValue(), id + "/" + escape(entry.getKey()), output));
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++) {walk(value.getAsJsonArray().get(i), id + "/" + i, output);}
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {output.put(id, value.getAsDouble());}
    }

    static void set(JsonObject bundle, String id, double value) {
        JsonElement node = bundle.get(id.substring(0, id.indexOf(':')));
        List<String> path = path(id);
        for (int i = 0; i < path.size() - 1; i++) {
            node = node.isJsonArray() ? node.getAsJsonArray().get(Integer.parseInt(path.get(i))) : node.getAsJsonObject().get(path.get(i));
        }
        if (node.isJsonArray()) {node.getAsJsonArray().set(Integer.parseInt(path.getLast()), new JsonPrimitive(value));}
        else {node.getAsJsonObject().addProperty(path.getLast(), value);}
    }

    private static List<String> path(String id) {
        return java.util.Arrays.stream(id.substring(id.indexOf(':') + 2).split("/", -1))
                .map(part -> part.replace("~1", "/").replace("~0", "~")).toList();
    }

    private static String escape(String part) {return part.replace("~", "~0").replace("/", "~1");}

    private static String entityId(String domain, List<String> path) {
        if (path.size() >= 2 && Set.of("towers", "abilities", "parameters", "traits", "summons").contains(path.getFirst())) {return path.get(1);}
        if (domain.equals("tower") && path.getFirst().equals("upgradeCosts")) {return path.getLast().split("->", 2)[0];}
        return path.size() == 1 ? domain : String.join("/", path.subList(0, path.size() - 1));
    }

    private Rule rule(String domain, List<String> path, String entity) {
        String key = path.getLast();
        if (domain.equals("tower") && path.size() == 3 && path.getFirst().equals("abilities")) {
            double min = key.equals("aggroPriority") && entity.startsWith("hero_party_weapon_") ? Integer.MIN_VALUE : 0;
            return decimal("능력 · " + key, "config", min, null, LIVE);
        }
        if (domain.equals("tower") && path.getFirst().equals("villagerAdv")) {
            return key.equals("effectDurationTicks")
                    ? integer("능력 · " + key, "ticks", 1, MAX_INTEGER, LIVE)
                    : decimal("능력 · " + key, "config", 0, null, LIVE);
        }
        if (domain.equals("tower") && path.size() == 2 && path.getFirst().equals("illusionCloneQueue")) {
            return integer("복제 생성 · " + key, key.equals("spreadTicks") ? "ticks" : "count", 1, MAX_INTEGER, LIVE);
        }
        if (domain.equals("tower") && path.size() == 3 && path.getFirst().equals("towers")) {
            boolean verified = verifiedLiveTowerIds.contains(entity);
            return switch (key) {
                case "damage" -> decimal("공격력", "damage", 0, null, verified ? LIVE : MATCH);
                case "range" -> decimal("사거리", "blocks", 0, null, verified ? LIVE : MATCH);
                case "aggroPriority" -> integer("어그로 우선순위", "priority", Integer.MIN_VALUE, MAX_INTEGER, verified ? LIVE : MATCH);
                case "maxHealth" -> decimal("최대 체력", "health", 1, null, verified ? PREPARE : MATCH);
                case "attackIntervalTicks" -> integer("공격 주기", "ticks", 1, MAX_INTEGER, verified ? PREPARE : MATCH);
                case "mineralCost" -> integer("설치 비용", "diamond", 0, MAX_EXACT_INTEGER, verified ? PREPARE : MATCH);
                default -> null;
            };
        }
        if (domain.equals("tower") && path.size() == 2 && path.getFirst().equals("upgradeCosts")) {
            return integer("승급 비용", "diamond", 0, MAX_EXACT_INTEGER, verifiedLiveTowerIds.contains(entity) ? PREPARE : MATCH);
        }
        if (domain.equals("augment") && path.size() == 2 && path.getFirst().equals("rarityWeights")) {
            return integer("등급 조합 확률", "percent", 0, 100, MATCH);
        }
        if (domain.equals("augment") && path.size() == 3 && path.getFirst().equals("parameters")) {
            return augmentParameter(entity, key);
        }
        if (domain.equals("summon") && path.size() == 3 && path.getFirst().equals("summons")) {
            return switch (key) {
                case "emeraldCost" -> integer("소환 비용", "emerald", 0, MAX_EXACT_INTEGER, MATCH);
                case "incomeGain" -> integer("인컴 증가량", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
                case "diamondReward" -> integer("처치 보상", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
                case "maxHealth" -> decimal("최대 체력", "health", Double.MIN_VALUE, null, MATCH);
                case "attackDamage" -> decimal("공격력", "damage", 0, null, MATCH);
                case "armor" -> decimal("방어력", "armor", 0, null, MATCH);
                case "resistance" -> decimal("마법 저항력", "resistance", 0, null, MATCH);
                default -> null;
            };
        }
        if (domain.equals("monsterScaling") && path.size() == 1) {
            return switch (key) {
                case "survivalDelayTicks", "laneBreachDelayTicks" -> integer("성장 대기 시간", "ticks", 0, MAX_INTEGER, MATCH);
                case "intervalTicks" -> integer("성장 간격", "ticks", 1, MAX_INTEGER, MATCH);
                case "healthGrowthPercentPerInterval", "attackDamageGrowthPercentPerInterval" -> decimal("구간별 성장률", "percent", 0, null, MATCH);
                default -> null;
            };
        }
        if (domain.equals("economy")) {
            return switch (String.join("/", path)) {
                case "startingDiamond", "startingIncome" -> integer("시작 다이아/인컴", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
                case "startingEmerald" -> integer("시작 에메랄드", "emerald", 0, MAX_EXACT_INTEGER, MATCH);
                case "emeraldCap/base", "emeraldCap/flatBonus" -> integer("에메랄드 한도", "emerald", 0, MAX_EXACT_INTEGER, MATCH);
                case "emeraldProduction/initialEmeraldPerSec", "emeraldProduction/emeraldPerSecIncrease" -> integer("초당 에메랄드", "emerald/second", 0, MAX_EXACT_INTEGER, MATCH);
                case "teamTransfer/receiveCooldownRounds" -> integer("수령 대기 라운드", "rounds", 0, MAX_INTEGER, MATCH);
                case "teamTransfer/maxDiamondPerRound" -> integer("라운드당 전달 한도", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
                case "killReward/crossLaneFinalDefenseWaveMultiplier", "killReward/finalDefenseProgressThreshold" -> decimal("보상 배율/진행도", "ratio", 0, 1.0, MATCH);
                default -> null;
            };
        }
        if (domain.equals("trait") && path.size() == 3 && path.getFirst().equals("traits")) {
            return traitParameter(entity, key);
        }
        if (domain.equals("wave") && path.contains("lanes") && path.size() >= 6
                && path.get(path.size() - 2).matches("[0-9]+")) {
            return switch (key) {
                case "count" -> integer("편성 수", "count", 0, MAX_INTEGER, MATCH);
                case "health" -> decimal("체력", "health", Double.MIN_VALUE, null, MATCH);
                case "attackDamage" -> decimal("공격력", "damage", 0, null, MATCH);
                case "armor" -> decimal("방어력", "armor", 0, null, MATCH);
                case "mineralReward" -> integer("처치 보상", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
                case "attackRange" -> decimal("공격 사거리", "blocks", 0, null, MATCH);
                case "attackIntervalTicks" -> integer("공격 주기", "ticks", 1, MAX_INTEGER, MATCH);
                default -> null;
            };
        }
        return null;
    }

    private static Rule traitParameter(String entity, String key) {
        return switch (key) {
            case "startingDiamond" -> integer("시작 다이아", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
            case "flatDiamond" -> integer("정기 지급 다이아", "diamond", 0, MAX_EXACT_INTEGER, MATCH);
            case "towerLimitBonus" -> integer("추가 타워 한도", "count", 0, MAX_INTEGER, MATCH);
            case "firstPayoutRound" -> integer("첫 지급 라운드", "rounds", 1, MAX_INTEGER, MATCH);
            case "refundRateAfterWave" -> decimal("전투 후 판매 환급률", "ratio", 0, 1.0, MATCH);
            case "healthRatioThreshold" -> decimal("대상 체력 비율 기준", "ratio", 0, 1.0, MATCH);
            case "durationSeconds" -> decimal("지속 시간", "seconds", entity.equals("ignite") ? 0.05 : 0, MAX_INTEGER / 20.0, MATCH);
            case "activationDelaySeconds" -> decimal("발동 대기 시간", "seconds", 0, MAX_INTEGER / 20.0, MATCH);
            case "intervalSeconds", "tickIntervalSeconds" -> decimal("발동 간격", "seconds", 0.05, MAX_INTEGER / 20.0, MATCH);
            case "currentHealthThreshold" -> decimal("대상 현재 체력 기준", "health", 0, null, MATCH);
            case "flatDamagePerTick" -> decimal("회당 고정 피해", "damage", 0, null, MATCH);
            case "incomeRatio" -> decimal("인컴 비례 지급률", "ratio", 0, null, MATCH);
            case "teamIncomeRatio" -> decimal("팀 인컴 비례 지급률", "ratio", 0, null, MATCH);
            case "attackDamageBonus" -> decimal("인컴 공격력 증가율", "ratio", 0, null, MATCH);
            case "attackSpeedBonus" -> decimal("공격 속도 증가율", "ratio", 0, null, MATCH);
            case "damageBonus", "outgoingDamageBonus" -> decimal("주는 피해 증가율", "ratio", 0, null, MATCH);
            case "incomingDamageBonus" -> decimal("받는 피해 증가율", "ratio", 0, null, MATCH);
            case "maxHealthBonus" -> decimal("최대 체력 증가율", "ratio", 0, null, MATCH);
            case "CoreMaxHealthBonus" -> decimal("핵심 타워 최대 체력 증가율", "ratio", 0, null, MATCH);
            case "damageBonusPerTower" -> decimal("같은 타워당 피해 증가율", "ratio", 0, null, MATCH);
            case "damageBonusPerType" -> decimal("타워 종류당 피해 증가율", "ratio", 0, null, MATCH);
            case "attackDamageRatioPerRound" -> decimal("라운드당 공격력 비례 피해", "ratio", 0, null, MATCH);
            default -> null;
        };
    }

    private static Rule augmentParameter(String entity, String key) {
        var limits = AugmentConfig.parameterLimits(key);
        String unit = augmentUnit(entity, key);
        if (unit == null) return null;
        boolean integer = limits.integer() || Set.of("castInterval", "manaCost", "diamondReward", "secondCoreCost").contains(key);
        double min = key.equals("castInterval") ? 1 : limits.min();
        return new Rule(parameterLabel(key), unit, min, limits.max(), integer, MATCH);
    }

    private static String augmentUnit(String entity, String key) {
        if (key.endsWith("Ticks")) return "ticks";
        return switch (key) {
            case "amount" -> entity.contains("reserve_production_") ? "emerald/second" : "diamond";
            case "advanceCap", "incomeBonus", "matchIncomeCap", "roundBonusCap", "ticketValue", "diamondReward", "secondCoreCost" -> "diamond";
            case "emeraldPerShell" -> "emerald";
            case "initialMana", "manaCost" -> "mana";
            case "openingWater", "waterPerCharge" -> "water";
            case "allFailedScoreLoss" -> "score";
            case "gaugePerVolley" -> "gauge";
            case "generationBonus" -> "power";
            case "growthRounds", "hatchWaves", "roundReduction" -> "rounds";
            case "radius", "radiusBonus", "damageRadius", "triggerRadius", "shellRadius", "jackpotRadius", "hatchedRange",
                    "minimumRange", "distance", "length", "width", "linkRange", "yardRadius" -> "blocks";
            case "chargeDamage", "hatchedDamage", "mineDamage", "shellDamage", "damagePerHitCap" -> "damage";
            case "hatchedHealth", "healCap" -> "health";
            case "advanceMultiplier", "artilleryIncomingMultiplier", "attackMultiplier", "auraMultiplier", "bodyMultiplier",
                    "capMultiplier", "chillMultiplier", "costMultiplier", "damageCap", "debtMultiplier", "diamondMultiplier",
                    "durationMultiplier", "effectMultiplier", "failureScoreMultiplier", "healthMultiplier", "oppositeMultiplier",
                    "payoutMultiplier", "productionMultiplier", "scoreMultiplier", "selectedMultiplier", "statMultiplier",
                    "supplyMultiplier", "supportMultiplier", "tierExperienceMultiplier" -> "multiplier";
            case "areaPerTier", "artilleryDamageBonus", "attackSpeedBonus", "bonusPerStack", "bonusRatio", "bossMaxHealthDamageRatio",
                    "chargedDamageRatio", "childRatio", "chill", "copyRatio", "damageBonus", "damageCapRatio", "damageRatio",
                    "damageReduction", "damageThreshold", "decayReduction", "dreamBonus", "echoRatio", "experienceRatio", "followupRatio",
                    "growthBonus", "healRatio", "healthBonus", "healthCapRatio", "healthDamageRatio", "healthLossRatio", "healthPerTier",
                    "healthRatio", "healthThreshold", "inheritRatio", "intervalRatio", "jackpotDamageRatio", "longDamageBonus",
                    "longDamageReduction", "markDamageBonus", "maxHealthBonus", "maxHealthDamageRatio", "oppositeEffectRatio",
                    "otherDamagePenalty", "overflowRatio", "overkillRatio", "penaltyPerStack", "powerPerTier", "quickDamageBonus",
                    "redirectRatio", "refundRatio", "repeatDamageRatio", "reviveHealthRatio", "rewardBonus", "scalePerClone",
                    "secondaryRatio", "slow", "statBonus", "statRatio", "statReversalChance", "transferRatio", "upgradeCostBonus", "vanguardDamagePenalty",
                    "vanguardDamageReduction", "waitReduction", "weaponDamageBonus" -> "ratio";
            case "castInterval", "neighborCount" -> "count";
            default -> AugmentConfig.parameterLimits(key).integer() ? "count" : null;
        };
    }

    private static String parameterLabel(String key) {
        return switch (key) {
            case "damageBonus" -> "추가 피해 비율";
            case "damageReduction" -> "받는 피해 감소율";
            case "maxHealthBonus", "healthBonus" -> "최대 체력 증가율";
            case "damageRatio" -> "공격력 비례 피해";
            case "healRatio" -> "회복 비율";
            case "statReversalChance" -> "실패 능력치 반전 확률";
            case "amount" -> "보상 수량";
            case "radius" -> "효과 반경";
            case "maxTargets", "targetCount", "targets" -> "최대 대상 수";
            case "maxStacks" -> "최대 중첩 수";
            case "damagePerHitCap" -> "한 번에 받는 피해 상한";
            case "durationTicks", "markDurationTicks" -> "지속 시간";
            case "intervalTicks" -> "발동 간격";
            case "cooldownTicks" -> "재사용 대기 시간";
            default -> key;
        };
    }

    private static Rule integer(String label, String unit, double min, double max, List<ApplyMode> modes) {
        return new Rule(label, unit, min, max, true, modes);
    }

    private static Rule decimal(String label, String unit, double min, Double max, List<ApplyMode> modes) {
        return new Rule(label, unit, min, max, false, modes);
    }

    private record Rule(String label, String unit, double min, Double max, boolean integer, List<ApplyMode> modes) {}
    private record Metadata(String name, String description, String builderId, String builderName, Integer tier, String rarity) {}
}
