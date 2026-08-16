package kim.biryeong.semiontd.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobRegistryTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        JobRegistry.configureAvailability(JobAvailabilityConfig.defaultConfig());
    }

    @Test
    void disablingAJobKeepsItRegisteredButBlocksSelection() {
        JobAvailabilityConfig disabled = JobAvailabilityConfig.defaultConfig()
                .withEnabled(NetherTowerJob.ID, false)
                .withEnabled(JobRegistry.defaultJob().id(), false);

        JobRegistry.configureAvailability(disabled);

        assertTrue(JobRegistry.find(NetherTowerJob.ID).isPresent());
        assertEquals(24, JobRegistry.all().size());
        assertTrue(JobRegistry.officialBuilders().stream().anyMatch(job -> job.id().equals(NetherTowerJob.ID)));
        assertTrue(JobRegistry.isEnabled(JobRegistry.defaultJob()));
        assertFalse(JobRegistry.isEnabled(NetherTowerJob.ID));
    }

    @Test
    void separatesOfficialAndCreativeBuildersWithoutChangingRegistryOrder() {
        assertEquals(List.of(
                VillagerTowerJob.ID,
                VillagerAdvTowerJob.ID,
                UndeadTowerJob.ID,
                AnimalTowerJob.ID,
                WarlockTowerJob.ID,
                LegionTowerJob.ID,
                ResonanceTowerJob.ID,
                IllagerTowerJob.ID,
                NetherTowerJob.ID,
                OceanTowerJob.ID,
                AncientCityTowerJob.ID,
                HeroPartyTowerJob.ID
        ), JobRegistry.officialBuilders().stream().map(SemionJob::id).toList());
        assertEquals(List.of(
                EndTowerJob.ID,
                AdversaryTowerJob.ID,
                MageTowerJob.ID,
                EngineerTowerJob.ID,
                InsectTowerJob.ID,
                FutureAgencyTowerJob.ID,
                QueenTowerJob.ID,
                AtlantisTowerJob.ID,
                PlantTowerJob.ID,
                ArmyTowerJob.ID,
                ThunderTowerJob.ID
        ), JobRegistry.creativeBuilders().stream().map(SemionJob::id).toList());
        assertEquals(24, JobRegistry.all().size());
        assertTrue(JobRegistry.officialBuilders().stream().noneMatch(JobRegistry.defaultJob()::equals));
        assertTrue(JobRegistry.creativeBuilders().stream().noneMatch(JobRegistry.defaultJob()::equals));
    }

    @Test
    void builderDescriptionsLeadNewPlayersFromStartToOperation() {
        List<SemionJob> builders = Stream.concat(
                JobRegistry.officialBuilders().stream(),
                JobRegistry.creativeBuilders().stream()
        ).toList();
        Set<String> optionalLabels = Set.of("주의 ", "연계 ", "성장 ");

        assertEquals(23, builders.size());
        for (SemionJob builder : builders) {
            List<String> lines = builder.description().stream().map(line -> line.getString()).toList();
            assertTrue(lines.size() >= 2 && lines.size() <= 3, builder.id() + " 설명은 2~3줄이어야 합니다.");
            assertTrue(lines.get(0).startsWith("시작 "), builder.id() + " 설명은 시작 행동부터 알려야 합니다.");
            assertTrue(lines.get(1).startsWith("운영 "), builder.id() + " 설명은 운영 방법을 이어서 알려야 합니다.");
            assertTrue(lines.stream().noneMatch(String::isBlank), builder.id() + " 설명에 빈 줄이 없어야 합니다.");
            if (lines.size() == 3) {
                assertTrue(
                        optionalLabels.stream().anyMatch(lines.get(2)::startsWith),
                        builder.id() + " 세 번째 줄은 주의, 연계, 성장 중 하나여야 합니다."
                );
            }
        }
    }

    @Test
    void placementDistancesInDescriptionsFollowRuntimeBalance() {
        TowerBalanceConfig defaults = TowerBalanceConfig.defaultConfig();
        Map<String, Map<String, Double>> abilities = new LinkedHashMap<>(defaults.abilities());
        overrideAbility(abilities, ResonanceTowers.FOCUS_CRYSTAL.id(), "linkRange", 2.0);
        overrideAbility(abilities, OceanTowers.T1_WATER.id(), "supplyRadius", 3.0);
        overrideAbility(abilities, InsectTowers.SPAWNER.id(), "reviveRadius", 4.0);
        TowerBalanceRuntime.apply(new TowerBalanceConfig(defaults.towers(), defaults.upgradeCosts(), abilities));

        assertTrue(new ResonanceTowerJob().description().getFirst().getString().contains("2칸"));
        assertTrue(new OceanTowerJob().description().getFirst().getString().contains("3칸"));
        assertTrue(new InsectTowerJob().description().getFirst().getString().contains("4칸"));
    }

    private static void overrideAbility(
            Map<String, Map<String, Double>> abilities,
            String towerId,
            String key,
            double value
    ) {
        Map<String, Double> towerAbilities = new LinkedHashMap<>(abilities.get(towerId));
        towerAbilities.put(key, value);
        abilities.put(towerId, towerAbilities);
    }
}
