package kim.biryeong.semiontd.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers;

final class MagicSchoolBalanceMigration {
    private MagicSchoolBalanceMigration() {}

    static String migrate(String json, TowerBalanceConfig defaults) {
        var root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject object = root.getAsJsonObject();
        JsonObject abilities = child(object, "abilities");
        JsonObject global = child(abilities, MagicSchoolTowers.CONFIG_ID);
        if (number(global, "nerfBalanceVersion", 0) >= 1) return json;
        Map.ofEntries(
                Map.entry("spellPowerPerLevel", .06), Map.entry("darkArtsDefensePerLevel", .06),
                Map.entry("magicHistoryPerLevel", .30), Map.entry("spellPracticeCost", 100.0),
                Map.entry("deathEaterCost", 200.0), Map.entry("duelingPracticeHealthRatio", .10),
                Map.entry("duelingPracticeProficiencyRatio", .25), Map.entry("quidditchBaseReward", 10.0),
                Map.entry("quidditchRewardIncrease", 8.0), Map.entry("spellTransferMaxTargets", 8.0),
                Map.entry("spellTransferCooldownTicks", 60.0), Map.entry("potionsHealRatio", .15),
                Map.entry("explosiveBarrelsCost", 400.0), Map.entry("explosiveBarrelRadius", 2.5),
                Map.entry("explosiveBarrelDamageRatio", .60), Map.entry("spellTier4Cost", 450.0),
                Map.entry("spellTier5Cost", 600.0)
        ).forEach((key, former) -> replace(global, key, former, defaults.ability(MagicSchoolTowers.CONFIG_ID, key, former)));
        JsonObject towers = child(object, "towers");
        for (var type : MagicSchoolTowers.all().stream().filter(MagicSchoolTowers::isWizard).toList()) {
            String id = type.id();
            boolean freshman = MagicSchoolTowers.isFreshman(type);
            boolean house = MagicSchoolTowers.isHouseWizard(type);
            JsonObject values = child(abilities, id);
            replace(values, "maxProficiency", freshman ? 100 : house ? 250 : 400,
                    defaults.ability(id, "maxProficiency", 100));
            for (String key : List.of("proficiencyDamagePerPoint", "proficiencyHealthPerPoint")) {
                if (!values.has(key)) {
                    double former = number(global, key, .0015);
                    values.addProperty(key, former == .0015 ? defaults.ability(id, key, former) : former);
                }
            }
            if (!freshman && towers.has(id)) {
                JsonObject stats = child(towers, id);
                double formerHealth = house ? (id.contains("hufflepuff") ? 440 : 400)
                        : (id.contains("hufflepuff") ? 660 : 600);
                double formerDamage = house ? (id.contains("slytherin") ? 66 : 60)
                        : (id.contains("slytherin") ? 99 : 90);
                replace(stats, "maxHealth", formerHealth, defaults.towers().get(id).maxHealth());
                replace(stats, "damage", formerDamage, defaults.towers().get(id).damage());
                replace(stats, "range", 8, 10);
            }
        }
        Map.of(
                "protego", Map.of("damageReduction", .5),
                "wingardium_leviosa", Map.of("liftDamageMultiplier", .35),
                "expulso", Map.of("radius", 1.5),
                "lumos", Map.of("damageMultiplier", .5, "magicVulnerability", .15),
                "episkey", Map.of("healingMultiplier", 1.5, "recipientCooldownTicks", 80.0),
                "bombarda", Map.of("radius", 2.5, "secondaryMultiplier", .75),
                "protego_maxima", Map.of("damageReduction", .65),
                "expecto_patronum", Map.of("secondaryMultiplier", .3),
                "lumos_maxima", Map.of("radius", 3.0),
                "rennervate", Map.of("damageBonus", .2)
        ).forEach((spell, formerValues) -> {
            String id = "magic_school_spell_" + spell;
            JsonObject values = child(abilities, id);
            formerValues.forEach((key, former) -> replace(values, key, former, defaults.ability(id, key, former)));
            if (spell.equals("lumos")) values.remove("attackSpeedBonus");
        });
        global.addProperty("nerfBalanceVersion", 1);
        return object.toString();
    }

    static String migrateProtection(String json) {
        var root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return json;
        JsonObject abilities = child(root.getAsJsonObject(), "abilities");
        JsonObject global = child(abilities, MagicSchoolTowers.CONFIG_ID);
        if (number(global, "protectionBalanceVersion", 0) >= 1) return json;
        replace(child(abilities, "magic_school_spell_protego"), "damageReduction", .4, .2);
        replace(child(abilities, "magic_school_spell_protego_maxima"), "damageReduction", .5, .3);
        global.addProperty("protectionBalanceVersion", 1);
        return root.toString();
    }

    static void migrateAugments(JsonObject root) {
        JsonObject parameters = child(root, "parameters");
        String id = "semiontd:job_magic_school_g2";
        if (!parameters.has(id) && parameters.has("job_magic_school_g2")) id = "job_magic_school_g2";
        JsonObject graduate = child(parameters, id);
        if (number(graduate, "balanceVersion", 0) >= 1) return;
        replace(graduate, "proficiencyCapBonus", 100, 250);
        graduate.addProperty("balanceVersion", 1);
    }

    private static JsonObject child(JsonObject parent, String key) {
        if (!parent.has(key)) parent.add(key, new JsonObject());
        return parent.getAsJsonObject(key);
    }

    private static double number(JsonObject object, String key, double fallback) {
        if (!object.has(key)) return fallback;
        var value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid Magic School setting: " + key);
        }
        return value.getAsDouble();
    }

    private static void replace(JsonObject object, String key, double former, double updated) {
        if (object.has(key) && number(object, key, former) == former) object.addProperty(key, updated);
    }
}
