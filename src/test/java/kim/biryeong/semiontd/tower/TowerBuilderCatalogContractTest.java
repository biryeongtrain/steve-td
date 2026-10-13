package kim.biryeong.semiontd.tower;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class TowerBuilderCatalogContractTest {
    private static final UUID OWNER = UUID.nameUUIDFromBytes("builder-catalog-contract".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final GridPosition ORIGINAL = new GridPosition(7,64,9);
    private static final GridPosition CURRENT = new GridPosition(21,64,23);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static Stream<SemionJob> builders() {
        JobRegistry.registerBuiltIns();
        return JobRegistry.all().stream().filter(job -> job != JobRegistry.defaultJob());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> builderCases() {
        return builders().map(job -> org.junit.jupiter.params.provider.Arguments.of(job.id().toString(), job));
    }

    @AfterEach
    void reset() {
        kim.biryeong.semiontd.tower.blueprint.BlueprintStates.clear(OWNER);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    @ParameterizedTest(name = "{0}: factories, ownership, upgrade edges and positions")
    @MethodSource("builderCases")
    void everyBuilderPreservesCatalogContracts(String builderId, SemionJob job) throws Exception {
        String path = System.getenv("SEMIONTD_TEST_TOWER_BALANCE");
        TowerBalanceConfig config = path == null ? TowerBalanceConfig.defaultConfig()
                : new Gson().fromJson(Files.readString(Path.of(path)),TowerBalanceConfig.class);
        config.validateForRuntime();
        ProductionTowerCatalogs.reloadBuiltIns(config);
        if (job instanceof kim.biryeong.semiontd.job.BlueprintTowerJob) {
            var creation = kim.biryeong.semiontd.tower.blueprint.BlueprintStates.create(OWNER, "Contract",
                    new kim.biryeong.semiontd.tower.blueprint.BlueprintStats(120,12,20,7,25,
                            kim.biryeong.semiontd.entity.monster.DamageType.PHYSICAL),
                    kim.biryeong.semiontd.tower.blueprint.BlueprintVisuals.options().getFirst().sourceTowerId());
            assertTrue(creation.success(),creation.message());
        }
        List<ProductionTowerCatalog.CatalogEntry> entries = ProductionTowerCatalog.all().stream()
                .filter(e -> e.availability() == ProductionTowerCatalog.Availability.JOB)
                .filter(e -> job.includesTowerInCatalog(e.type())).toList();
        assertFalse(entries.isEmpty(),job.id().toString());
        for (var entry : entries) {
            String id=entry.type().id();
            assertEquals(1,builders().filter(j->j.includesTowerInCatalog(entry.type())).count(),id);
            Tower tower=entry.create(OWNER,TeamId.BLUE,2,ORIGINAL,CURRENT);
            assertEquals(id,tower.type().id(),id);
            assertEquals(OWNER,tower.ownerPlayer(),id);
            assertEquals(TeamId.BLUE,tower.teamId(),id);
            assertEquals(2,tower.laneId(),id);
            assertEquals(ORIGINAL,tower.originalPosition(),id);
            assertEquals(CURRENT,tower.position(),id);
            assertTrue(Double.isFinite(tower.maxHealth()) && tower.maxHealth()>0,id);
            assertTrue(entry.type().description().stream().noneMatch(s->s.contains("{ability.")),id);
            for(var option:ProductionTowerCatalog.upgrades(entry.type())) {
                var target=ProductionTowerCatalog.find(option.targetType().id()).orElseThrow();
                assertTrue(job.includesTowerInCatalog(target.type()),id+" -> "+target.type().id());
                assertEquals(config.upgradeCost(id,option.id(),option.mineralCost()),option.mineralCost(),id);
                Tower upgraded=target.create(OWNER,TeamId.BLUE,2,tower.originalPosition(),tower.position());
                upgraded.copyFrom(tower,option.mineralCost());
                assertEquals(ORIGINAL,upgraded.originalPosition(),id);
                assertEquals(CURRENT,upgraded.position(),id);
                assertEquals(tower.paidMineralCost()+option.mineralCost(),upgraded.paidMineralCost(),id);
            }
        }
        System.out.println("BUILDER_CONTRACT "+job.id()+" towers="+entries.size()+" config="+(path==null?"defaults":"active-copy"));
    }
}
