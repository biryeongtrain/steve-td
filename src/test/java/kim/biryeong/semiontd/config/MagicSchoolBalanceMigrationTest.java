package kim.biryeong.semiontd.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import kim.biryeong.semiontd.augment.AugmentConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class MagicSchoolBalanceMigrationTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void oldDefaultsMigrateThroughTheRealLoaderAndDoNotOverwriteLaterChanges() throws Exception {
        Path path = directory.resolve("tower_balance.json");
        Files.writeString(path, """
                {"towers":{"magic_school_gryffindor_t2":{"maxHealth":400,"damage":60,"range":8},
                  "magic_school_hufflepuff_t2":{"maxHealth":440,"damage":60},
                  "magic_school_slytherin_t3":{"maxHealth":600,"damage":99,"range":8}},
                 "abilities":{"magic_school_global":{"baseStatsVersion":1,"proficiencyVersion":2,
                  "curriculumBalanceVersion":2,"combatBalanceVersion":1,"spellPowerPerLevel":0.06,
                  "darkArtsDefensePerLevel":0.06,"magicHistoryPerLevel":0.3,"deathEaterCost":200,
                  "spellTier4Cost":450,"spellTier5Cost":600,"proficiencyDamagePerPoint":0.0015,
                  "proficiencyHealthPerPoint":0.0015},
                  "magic_school_gryffindor_t2":{"maxProficiency":250},
                  "magic_school_slytherin_t3":{"maxProficiency":400},
                  "magic_school_spell_lumos":{"damageMultiplier":0.5,"magicVulnerability":0.15,"attackSpeedBonus":1},
                  "magic_school_spell_episkey":{"healingMultiplier":1.5,"recipientCooldownTicks":80},
                  "magic_school_spell_bombarda":{"radius":2.5,"secondaryMultiplier":0.75}}}
                """);
        var loaded = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(340, loaded.towers().get("magic_school_gryffindor_t2").maxHealth());
        assertEquals(50, loaded.towers().get("magic_school_gryffindor_t2").damage());
        assertEquals(10, loaded.towers().get("magic_school_gryffindor_t2").range());
        assertEquals(374, loaded.towers().get("magic_school_hufflepuff_t2").maxHealth());
        assertEquals(88, loaded.towers().get("magic_school_slytherin_t3").damage());
        assertEquals(300, loaded.ability("magic_school_gryffindor_t2", "maxProficiency", -1));
        assertEquals(1000, loaded.ability("magic_school_slytherin_t3", "maxProficiency", -1));
        assertEquals(.0008, loaded.ability("magic_school_gryffindor_t2", "proficiencyDamagePerPoint", -1));
        assertEquals(.0005, loaded.ability("magic_school_slytherin_t3", "proficiencyHealthPerPoint", -1));
        assertEquals(.04, loaded.ability("magic_school_global", "spellPowerPerLevel", -1));
        assertEquals(.03, loaded.ability("magic_school_global", "darkArtsDefensePerLevel", -1));
        assertEquals(.25, loaded.ability("magic_school_global", "magicHistoryPerLevel", -1));
        assertEquals(.8, loaded.ability("magic_school_spell_lumos", "damageMultiplier", -1));
        assertEquals(.12, loaded.ability("magic_school_spell_lumos", "magicVulnerability", -1));
        assertEquals(2, loaded.ability("magic_school_spell_lumos", "radius", -1));
        assertFalse(loaded.abilities().get("magic_school_spell_lumos").containsKey("attackSpeedBonus"));
        assertEquals(.75, loaded.ability("magic_school_spell_episkey", "healingMultiplier", -1));
        assertEquals(60, loaded.ability("magic_school_spell_episkey", "recipientCooldownTicks", -1));
        assertEquals(2.4, loaded.ability("magic_school_spell_bombarda", "radius", -1));
        assertEquals(.7, loaded.ability("magic_school_spell_bombarda", "secondaryMultiplier", -1));
        String saved = Files.readString(path);
        SemionConfigLoader.load(directory, LoggerFactory.getLogger("test"));
        assertEquals(saved, Files.readString(path));
        var object = JsonParser.parseString(saved).getAsJsonObject();
        object.getAsJsonObject("towers").getAsJsonObject("magic_school_gryffindor_t2").addProperty("maxHealth", 400);
        object.getAsJsonObject("abilities").getAsJsonObject("magic_school_gryffindor_t2").addProperty("maxProficiency", 250);
        Files.writeString(path, object.toString());
        var custom = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(400, custom.towers().get("magic_school_gryffindor_t2").maxHealth());
        assertEquals(250, custom.ability("magic_school_gryffindor_t2", "maxProficiency", -1));
    }

    @Test void customStatsAndLegacyGlobalGrowthRemainAuthoritative() throws Exception {
        Files.writeString(directory.resolve("tower_balance.json"), """
                {"towers":{"magic_school_hufflepuff_t2":{"maxHealth":555,"damage":77,"range":12}},
                 "abilities":{"magic_school_global":{"baseStatsVersion":1,"proficiencyVersion":2,"proficiencyDamagePerPoint":0.002,
                  "proficiencyHealthPerPoint":0.003,"spellPowerPerLevel":0.07},
                  "magic_school_hufflepuff_t2":{"maxProficiency":777},
                  "magic_school_slytherin_t3":{"proficiencyDamagePerPoint":0.004},
                  "magic_school_spell_bombarda":{"radius":3.3,"secondaryMultiplier":0.9}}}
                """);
        var loaded = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(555, loaded.towers().get("magic_school_hufflepuff_t2").maxHealth());
        assertEquals(12, loaded.towers().get("magic_school_hufflepuff_t2").range());
        assertEquals(777, loaded.ability("magic_school_hufflepuff_t2", "maxProficiency", -1));
        assertEquals(.002, loaded.ability("magic_school_hufflepuff_t2", "proficiencyDamagePerPoint", -1));
        assertEquals(.003, loaded.ability("magic_school_hufflepuff_t2", "proficiencyHealthPerPoint", -1));
        assertEquals(.004, loaded.ability("magic_school_slytherin_t3", "proficiencyDamagePerPoint", -1));
        assertEquals(.07, loaded.ability("magic_school_global", "spellPowerPerLevel", -1));
        assertEquals(3.3, loaded.ability("magic_school_spell_bombarda", "radius", -1));
    }

    @Test void newProtegoDefaultDoesNotOverwriteSavedProtectionSettings() throws Exception {
        Path path = directory.resolve("tower_balance.json");
        var fresh = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(.3, fresh.ability("magic_school_spell_protego", "damageReduction", -1));
        for (double configured : new double[]{.2, .37}) {
            var saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            saved.getAsJsonObject("abilities").getAsJsonObject("magic_school_spell_protego")
                    .addProperty("damageReduction", configured);
            Files.writeString(path, saved.toString());
            for (int reload = 0; reload < 2; reload++) {
                var loaded = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
                assertEquals(configured, loaded.ability("magic_school_spell_protego", "damageReduction", -1));
                assertEquals(.3, loaded.ability("magic_school_spell_protego_maxima", "damageReduction", -1));
            }
        }
    }

    @Test void protectionDefaultsMigrateOnceAndCustomValuesRemainAuthoritative() throws Exception {
        Path path = directory.resolve("tower_balance.json");
        Files.writeString(path, """
                {"abilities":{"magic_school_global":{"nerfBalanceVersion":1},
                  "magic_school_spell_protego":{"damageReduction":0.4},
                  "magic_school_spell_protego_maxima":{"damageReduction":0.5}}}
                """);
        var first = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(.20, first.ability("magic_school_spell_protego", "damageReduction", -1));
        assertEquals(.30, first.ability("magic_school_spell_protego_maxima", "damageReduction", -1));
        assertEquals(.05, first.ability("magic_school_spell_protego_maxima", "auraReduction", -1));
        var saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        saved.getAsJsonObject("abilities").getAsJsonObject("magic_school_spell_protego").addProperty("damageReduction", .4);
        saved.getAsJsonObject("abilities").getAsJsonObject("magic_school_spell_protego_maxima").addProperty("damageReduction", .65);
        Files.writeString(path, saved.toString());
        var reloaded = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(.4, reloaded.ability("magic_school_spell_protego", "damageReduction", -1));
        assertEquals(.65, reloaded.ability("magic_school_spell_protego_maxima", "damageReduction", -1));
        saved.getAsJsonObject("abilities").getAsJsonObject("magic_school_global").remove("protectionBalanceVersion");
        saved.getAsJsonObject("abilities").getAsJsonObject("magic_school_spell_protego").addProperty("damageReduction", .35);
        Files.writeString(path, saved.toString());
        var custom = SemionConfigLoader.load(directory, LoggerFactory.getLogger("test")).towerBalance();
        assertEquals(.35, custom.ability("magic_school_spell_protego", "damageReduction", -1));
        assertEquals(.65, custom.ability("magic_school_spell_protego_maxima", "damageReduction", -1));
    }

    @Test void graduateCapMigratesOnceAndPrismRewardBackfills() throws Exception {
        Path path = directory.resolve("augment_balance.json");
        Files.writeString(path, """
                {"parameters":{"job_magic_school_g2":{"proficiencyCapBonus":100},"job_magic_school_p":{}}}
                """);
        var loaded = SemionConfigLoader.loadOrCreateAugments(path, AugmentConfig.defaults(), LoggerFactory.getLogger("test"));
        assertEquals(250, loaded.parameter("job_magic_school_g2", "proficiencyCapBonus", -1));
        assertEquals(100, loaded.parameter("job_magic_school_p", "diamondReward", -1));
        var object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        object.getAsJsonObject("parameters").getAsJsonObject("semiontd:job_magic_school_g2").addProperty("proficiencyCapBonus", 100);
        object.getAsJsonObject("parameters").getAsJsonObject("semiontd:job_magic_school_p").addProperty("diamondReward", 75);
        Files.writeString(path, object.toString());
        var reloaded = SemionConfigLoader.loadOrCreateAugments(path, loaded, LoggerFactory.getLogger("test"));
        assertEquals(100, reloaded.parameter("job_magic_school_g2", "proficiencyCapBonus", -1));
        assertEquals(75, reloaded.parameter("job_magic_school_p", "diamondReward", -1));
    }
}
