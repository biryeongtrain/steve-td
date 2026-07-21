package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.PigTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.resonance.ResonanceTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.undead.UndeadMeleeSkeletonTower;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.AntiTankerCatTower;
import kim.biryeong.semiontd.tower.villager.VillagerSplashTower;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TowerRuntimeDetailsTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("tower-runtime-details".getBytes(StandardCharsets.UTF_8));
    private static final GridPosition POSITION = new GridPosition(0, 0, 0);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void applyDefaultBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @AfterEach
    void resetTowerBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void resonanceTowerShowsEarnedMoobloomEffects() {
        ResonanceTower focus = resonance(ResonanceTowers.FOCUS_CORE, POSITION);
        ResonanceService.refresh(List.of(
                focus,
                resonance(ResonanceTowers.WAVE_CRYSTAL, new GridPosition(1, 0, 0)),
                resonance(ResonanceTowers.FROST_CRYSTAL, new GridPosition(-1, 0, 0)),
                resonance(ResonanceTowers.AMPLIFY_CRYSTAL, new GridPosition(0, 0, 1)),
                resonance(ResonanceTowers.WAVE_PRISM, new GridPosition(1, 0, -1)),
                resonance(ResonanceTowers.FROST_PRISM, new GridPosition(-1, 0, -1)),
                resonance(ResonanceTowers.AMPLIFY_PRISM, new GridPosition(0, 0, -1))
        ));

        List<String> lines = SemionDialogService.towerRuntimeDetailLines(focus);

        assertContains(lines, "무블룸 공명");
        assertContains(lines, "Lv 3");
        assertContains(lines, "링크 6");
        assertContains(lines, "받는 오라");
        assertContains(lines, "공속 +50.0%");
    }

    @Test
    void animalTowerShowsHerdStackCount() throws Exception {
        PigTower pig = new PigTower(AnimalTowers.T2_PIG_TOWER, OWNER, TeamId.RED, 1, POSITION);
        setFieldFromHierarchy(pig, "currentStacks", 2);

        List<String> lines = SemionDialogService.towerRuntimeDetailLines(pig);

        assertContains(lines, "무리 스택");
        assertContains(lines, "2/2");
    }

    @Test
    void villagerTowersShowSurvivalAndDeathStacks() throws Exception {
        VillagerSplashTower librarian = new VillagerSplashTower(VillagerTowers.T2_LIBRARIAN_TOWER, OWNER, TeamId.RED, 1, POSITION);
        setFieldFromHierarchy(librarian, "survivalBouns", 2);
        AntiTankerCatTower cat = new AntiTankerCatTower(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, OWNER, TeamId.RED, 1, POSITION);
        cat.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        cat.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        VillagerThornTower golem = new VillagerThornTower(VillagerTowers.T2_GOLEM_TOWER, OWNER, TeamId.RED, 1, POSITION);
        setFieldFromHierarchy(golem, "survivalBonus", 1);

        assertContains(SemionDialogService.towerRuntimeDetailLines(librarian), "생존 스택 2/");
        assertContains(SemionDialogService.towerRuntimeDetailLines(cat), "사망 스택");
        assertContains(SemionDialogService.towerRuntimeDetailLines(cat), "공격력 +");
        assertContains(SemionDialogService.towerRuntimeDetailLines(golem), "생존 스택 1/");
    }

    @Test
    void undeadDeathStackTowerShowsDeathStackCountAndBonus() {
        UndeadMeleeSkeletonTower skeleton = new UndeadMeleeSkeletonTower(UndeadTowers.T2_MELEE_TOWER, OWNER, TeamId.RED, 1, POSITION);
        skeleton.onNearbyMonsterDeath(null, null, new Vec3(0.5, 1.0, 0.5));
        skeleton.onNearbyTowerDeath(null, new AntiTankerCatTower(VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, OWNER, TeamId.RED, 1, POSITION));

        List<String> lines = SemionDialogService.towerRuntimeDetailLines(skeleton);

        assertContains(lines, "사망 스택 2/");
        assertContains(lines, "공격력 +");
        assertContains(lines, "체력 +");
    }

    private static ResonanceTower resonance(kim.biryeong.semiontd.tower.TowerType type, GridPosition position) {
        return new ResonanceTower(type, OWNER, TeamId.RED, 1, position, position);
    }

    private static void assertContains(List<String> lines, String expected) {
        assertTrue(lines.stream().anyMatch(line -> line.contains(expected)),
                () -> "Expected a runtime detail line containing '" + expected + "' but got " + lines);
    }

    private static void setFieldFromHierarchy(Tower tower, String name, Object value) throws Exception {
        Class<?> type = tower.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(tower, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
