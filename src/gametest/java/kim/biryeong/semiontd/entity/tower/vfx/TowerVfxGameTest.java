package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kim.biryeong.gcbserver.packet.s2c.GCBParticleS2CPacket;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import kim.biryeong.semiontd.tower.body.BodyTowers;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.demonlord.DemonLordSkill;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.tower.gamble.GambleTowers;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.illager.IllagerTower;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.nether.NetherTower;
import kim.biryeong.semiontd.tower.nether.NetherTowerState;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.tower.plant.PlantVfx;
import kim.biryeong.semiontd.tower.queen.QueenTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.thunder.ThunderTowers;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class TowerVfxGameTest {
    @GameTest
    public void attackRangeChoosesExpectedHitParticle(GameTestHelper context) {
        if (TowerVfxService.visualKind(3.0) != TowerVfxService.AttackVisualKind.MELEE
                || TowerVfxService.visualKind(3.01) != TowerVfxService.AttackVisualKind.RANGED) {
            throw new AssertionError("Attack range should select melee at 3.0 and ranged above 3.0");
        }
        context.succeed();
    }

    @GameTest
    public void builderFamiliesChooseDistinctPalettes(GameTestHelper context) {
        assertPalette(VillagerTowers.T1_CAT_TOWER, BuilderPalette.VILLAGER);
        assertPalette(VillagerTowers.ADV_T1_CAT_TOWER, BuilderPalette.VILLAGER_ADV);
        assertPalette(UndeadTowers.T1_ZOMBIE_TOWER, BuilderPalette.UNDEAD);
        assertPalette(AnimalTowers.T1_PIG_TOWER, BuilderPalette.ANIMAL);
        assertPalette(WarlockTowers.BASE_WARLOCK_TOWER, BuilderPalette.WARLOCK);
        assertPalette(LegionTowers.T1_CHICKEN, BuilderPalette.LEGION);
        assertPalette(ResonanceTowers.FOCUS_CRYSTAL, BuilderPalette.RESONANCE);
        assertPalette(IllagerTowers.T1_VINDICATOR, BuilderPalette.ILLAGER);
        assertPalette(NetherTowers.T1_STRIDER, BuilderPalette.NETHER);
        for (TowerType type : EndTowers.all()) {
            assertPalette(type, BuilderPalette.END);
        }
        assertPalette(OceanTowers.T1_WATER, BuilderPalette.OCEAN);
        assertPalette(AncientCityTowers.CATALYST_T1, BuilderPalette.ANCIENT_CITY);
        assertPalette(AdversaryTowers.FOX, BuilderPalette.ADVERSARY);
        assertPalette(FutureAgencyTowers.agent(kim.biryeong.semiontd.tower.futureagency.FutureAgencyRole.COMBAT, 5),
                BuilderPalette.FUTURE_AGENCY);
        assertPalette(PlantTowers.T1_MEADOW_TOWER, BuilderPalette.PLANT);
        assertPalette(QueenTowers.QUEEN, BuilderPalette.QUEEN);
        assertPalette(QueenTowers.RANDOM_CARD_SOLDIER, BuilderPalette.QUEEN);
        assertPalette(EngineerTowers.trap(EngineerTowers.TrapKind.DISPENSER, 3), BuilderPalette.ENGINEER);
        assertPalette(MageTowers.WIZARD, BuilderPalette.MAGE);
        assertPalette(HeroPartyTowers.HERO, BuilderPalette.HERO_PARTY);
        assertPalette(HeroPartyTowers.companion(kim.biryeong.semiontd.tower.hero.HeroCompanionRole.ARCHER, 3),
                BuilderPalette.HERO_PARTY);
        assertPalette(InsectTowers.SILVERFISH, BuilderPalette.INSECT);
        assertPalette(InsectTowers.SPAWNER, BuilderPalette.INSECT);
        assertPalette(ArmyTowers.RECRUIT, BuilderPalette.ARMY);
        assertPalette(ArmyTowers.GUARD, BuilderPalette.ARMY);
        assertPalette(ThunderTowers.SQUIRREL_T3, BuilderPalette.THUNDER);
        assertPalette(DemonLordTowers.tower(DemonLordSkill.WAVE_OF_MALICE, 1), BuilderPalette.DEMON_LORD);
        assertPalette(GambleTowers.DICE_T1, BuilderPalette.GAMBLE);
        assertPalette(GambleTowers.GAMBLER, BuilderPalette.GAMBLE);
        assertPalette(GambleTowers.SPECTATOR_T3, BuilderPalette.GAMBLE);
        assertPalette(BodyTowers.HEART_T1, BuilderPalette.BODY);
        assertPalette(SuccubusTowers.SUCCUBUS, BuilderPalette.SUCCUBUS);
        context.succeed();
    }

    @GameTest
    public void endFamilyRoutesDedicatedPaletteIntoSharedAreaVfx(GameTestHelper context) {
        if (BuilderPalette.END == BuilderPalette.DEFAULT
                || !BuilderPalette.END.gcbRayParticle().equals("minecraft:portal")
                || !BuilderPalette.END.gcbAccentParticle().equals("minecraft:dragon_breath")) {
            throw new AssertionError("End must keep a dedicated portal and dragon-breath palette.");
        }
        assertDustColor(BuilderPalette.END.rayParticle(), 0xB52CFF, "End primary");
        assertDustColor(BuilderPalette.END.accentParticle(), 0x6F2DBD, "End accent");
        for (TowerType type : EndTowers.all()) {
            assertPalette(type, BuilderPalette.END);
        }

        List<AreaEffectVfxEvent> observed = new ArrayList<>();
        TowerVfxService.setAreaEffectTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("end-area-palette-vfx".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            EndTower runtimeTower = new EndTower(
                    EndTowers.T3_END_CRYSTAL_TOWER,
                    owner,
                    TeamId.RED,
                    1,
                    new GridPosition(2, 2, 2)
            );
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);

            TowerVfxService.showAreaEffect(
                    tower,
                    ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "test_end_splash"),
                    AreaVfxStyles.DRAGON_BREATH,
                    new Vec3(5.0, 2.0, 2.0),
                    3.0,
                    List.of(new Vec3(5.0, 2.0, 2.0)),
                    1,
                    1,
                    0
            );

            if (observed.size() != 1) {
                throw new AssertionError("Expected one End area VFX event, got " + observed.size());
            }
            var visual = observed.getFirst().visual();
            if (!visual.styleId().equals(AreaVfxStyles.DRAGON_BREATH)
                    || !visual.palette().equals(BuilderPalette.END.areaPalette())) {
                throw new AssertionError("End area VFX must carry the dedicated End palette: " + visual);
            }
            context.succeed();
        } finally {
            TowerVfxService.setAreaEffectTestObserver(null);
        }
    }

    @GameTest
    public void netherDeathTransitionsToZombieAndEmitsVfx(GameTestHelper context) {
        List<Vec3> observed = new ArrayList<>();
        TowerVfxService.setNetherTransitionTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("nether-transition-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            NetherTower runtimeTower = new NetherTower(NetherTowers.T1_STRIDER, owner, TeamId.RED, 1, new GridPosition(2, 2, 2));
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);

            runtimeTower.onDamaged(tower, null, runtimeTower.currentMaxHealth(), runtimeTower.currentMaxHealth(), 0.0);

            if (runtimeTower.state() != NetherTowerState.ZOMBIE) {
                throw new AssertionError("Lethal damage should transition a nether tower to zombie state");
            }
            if (observed.size() != 1 || observed.getFirst().distanceTo(tower.position()) > 2.0) {
                throw new AssertionError("Zombie transition should emit one VFX event at the tower: " + observed);
            }
            context.succeed();
        } finally {
            TowerVfxService.setNetherTransitionTestObserver(null);
        }
    }

    @GameTest
    public void illagerRaidActivationEmitsVfxAtTower(GameTestHelper context) {
        List<Vec3> observed = new ArrayList<>();
        TowerVfxService.setIllagerRaidActivationTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("illager-raid-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            GridPosition position = new GridPosition(2, 2, 2);
            IllagerTower runtimeTower = new IllagerTower(IllagerTowers.T1_VINDICATOR, owner, TeamId.RED, 1, position, position);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);

            TowerVfxService.showIllagerRaidActivation(tower);

            if (observed.size() != 1 || observed.getFirst().distanceTo(tower.position()) > 2.0) {
                throw new AssertionError("Illager raid activation should emit one VFX event at the tower: " + observed);
            }
            context.succeed();
        } finally {
            TowerVfxService.setIllagerRaidActivationTestObserver(null);
        }
    }

    @GameTest
    public void warlockSacrificeEmitsVfxFromVictimToWarlock(GameTestHelper context) {
        List<Vec3> sacrificedCenters = new ArrayList<>();
        List<Vec3> warlockCenters = new ArrayList<>();
        TowerVfxService.setWarlockSacrificeTestObserver((sacrificed, warlock) -> {
            sacrificedCenters.add(sacrificed);
            warlockCenters.add(warlock);
        });
        try {
            UUID owner = UUID.nameUUIDFromBytes("warlock-sacrifice-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            TestTower runtimeTower = new TestTower(WarlockTowers.BASE_WARLOCK_TOWER, owner);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);
            Vec3 sacrificedCenter = new Vec3(5.0, 3.0, 2.0);

            TowerVfxService.showWarlockSacrifice(tower, sacrificedCenter);

            if (sacrificedCenters.size() != 1
                    || sacrificedCenters.getFirst().distanceTo(sacrificedCenter) > 1.0E-6
                    || warlockCenters.getFirst().distanceTo(tower.position()) > 2.0) {
                throw new AssertionError("Warlock sacrifice should emit one victim-to-warlock VFX event");
            }
            context.succeed();
        } finally {
            TowerVfxService.setWarlockSacrificeTestObserver(null);
        }
    }

    @GameTest
    public void transcendenceBatchesAllAffectedTowerCenters(GameTestHelper context) {
        List<List<Vec3>> observed = new ArrayList<>();
        TowerVfxService.setTranscendenceTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("transcendence-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SemionTowerEntity first = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            first.setPos(2.0, 2.0, 2.0);
            first.configure(new TestTower(WarlockTowers.BASE_WARLOCK_TOWER, owner), null);
            SemionTowerEntity second = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            second.setPos(5.0, 2.0, 2.0);
            second.configure(new TestTower(WarlockTowers.BASE_WARLOCK_TOWER, owner), null);

            TowerVfxService.showTranscendence(List.of(first, second));

            if (observed.size() != 1
                    || observed.getFirst().size() != 2
                    || Math.abs(observed.getFirst().get(0).x - 2.0) > 1.0E-6
                    || Math.abs(observed.getFirst().get(1).x - 5.0) > 1.0E-6) {
                throw new AssertionError("Transcendence should enqueue one batched VFX event for every affected tower");
            }
            context.succeed();
        } finally {
            TowerVfxService.setTranscendenceTestObserver(null);
        }
    }

    @GameTest
    public void magicDamageEmitsHitVfxAtTarget(GameTestHelper context) {
        List<Vec3> observed = new ArrayList<>();
        TowerVfxService.setMagicHitTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("magic-hit-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(new TestTower(WarlockTowers.BASE_WARLOCK_TOWER, owner), null);
            SemionMonsterEntity target = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            target.setPos(5.0, 2.0, 2.0);

            TowerVfxService.showMagicHit(tower, target);

            if (observed.size() != 1 || observed.getFirst().distanceTo(TowerVfxService.targetCenter(target)) > 1.0E-6) {
                throw new AssertionError("Magic damage should emit one VFX event at the target");
            }
            context.succeed();
        } finally {
            TowerVfxService.setMagicHitTestObserver(null);
        }
    }

    @GameTest
    public void bodyHeartbeatEmitsVfxAtHeartTower(GameTestHelper context) {
        List<Vec3> observed = new ArrayList<>();
        TowerVfxService.setBodyHeartbeatTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("body-heartbeat-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(new TestTower(BodyTowers.HEART_T1, owner), null);

            TowerVfxService.showBodyHeartbeat(tower);

            if (observed.size() != 1 || observed.getFirst().distanceTo(tower.position()) > 2.0) {
                throw new AssertionError("Body heartbeat should emit one VFX event at the heart tower: " + observed);
            }
            context.succeed();
        } finally {
            TowerVfxService.setBodyHeartbeatTestObserver(null);
        }
    }

    @GameTest
    public void bodyEyeLaserExtendsStraightToFullRange(GameTestHelper context) {
        List<Vec3> starts = new ArrayList<>();
        List<Vec3> ends = new ArrayList<>();
        TowerVfxService.setBodyEyeLaserTestObserver((start, end) -> {
            starts.add(start);
            ends.add(end);
        });
        try {
            UUID owner = UUID.nameUUIDFromBytes("body-eye-laser-vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(new TestTower(BodyTowers.EYE_T1, owner), null);

            TowerVfxService.showBodyEyeLaser(tower, new Vec3(0.0, 0.0, 2.0), 12.0);

            if (starts.size() != 1
                    || Math.abs(starts.getFirst().distanceTo(ends.getFirst()) - 12.0) > 1.0E-6
                    || Math.abs(starts.getFirst().x - ends.getFirst().x) > 1.0E-6
                    || ends.getFirst().z <= starts.getFirst().z) {
                throw new AssertionError("Body eye laser should be one straight full-range line");
            }
            context.succeed();
        } finally {
            TowerVfxService.setBodyEyeLaserTestObserver(null);
        }
    }

    @GameTest
    public void bodyDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("heartbeat", "eye_laser")) {
            String command = "semiontd-debug vfx body " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void transcendenceDebugCommandParses(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var parsed = dispatcher.parse(
                "semiontd-debug vfx transcendence",
                context.getLevel().getServer().createCommandSourceStack()
        );
        if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
            throw new AssertionError("Expected /semiontd-debug vfx transcendence to parse completely");
        }
        context.succeed();
    }

    @GameTest
    public void magicHitDebugCommandParses(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var parsed = dispatcher.parse(
                "semiontd-debug vfx magic_hit",
                context.getLevel().getServer().createCommandSourceStack()
        );
        if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
            throw new AssertionError("Expected /semiontd-debug vfx magic_hit to parse completely");
        }
        context.succeed();
    }

    @GameTest
    public void oceanDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String command : List.of(
                "semiontd-debug vfx ocean_supply",
                "semiontd-debug vfx ocean_dehydrated"
        )) {
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void ancientCityDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("growth", "catalyst", "sensor", "shriek", "warden")) {
            String command = "semiontd-debug vfx ancient_city " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void adversaryDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("breeze", "golden", "shield", "support", "firework", "mace", "sculk")) {
            String command = "semiontd-debug vfx adversary " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void atlantisDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("zone", "burst")) {
            String command = "semiontd-debug vfx atlantis " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void futureAgencyDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("carry", "suppression")) {
            String command = "semiontd-debug vfx future_agency " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void queenDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("shrink", "giant")) {
            String command = "semiontd-debug vfx queen " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void engineerDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("power", "tnt")) {
            String command = "semiontd-debug vfx engineer " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void mageDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("spell", "prophecy")) {
            String command = "semiontd-debug vfx mage " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void insectDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("radius", "revive")) {
            String command = "semiontd-debug vfx insect " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void thunderDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("arc", "discharge")) {
            String command = "semiontd-debug vfx thunder " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void succubusDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("stack", "sleep", "smoke", "wake", "absorb")) {
            String command = "semiontd-debug vfx succubus " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void plantLobDebugCommandParses(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        var parsed = dispatcher.parse(
                "semiontd-debug vfx plant lob",
                context.getLevel().getServer().createCommandSourceStack()
        );
        if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
            throw new AssertionError("Expected /semiontd-debug vfx plant lob to parse completely");
        }
        context.succeed();
    }

    @GameTest
    public void plantLobStyleFlowsThroughSharedAreaVfxService(GameTestHelper context) {
        List<AreaEffectVfxEvent> observed = new ArrayList<>();
        TowerVfxService.setAreaEffectTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("plant-lob-vfx".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            TestTower runtimeTower = new TestTower(PlantTowers.T3_PODZOL_PITCHER_TOWER, owner);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);

            TowerVfxService.showAreaEffect(
                    tower,
                    ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "test_plant_lob"),
                    PlantVfx.LOBBED_SPLASH,
                    new Vec3(8.0, 2.0, 2.0),
                    4.0,
                    List.of(),
                    1,
                    1,
                    0
            );

            if (observed.size() != 1
                    || !observed.getFirst().visual().styleId().equals(PlantVfx.LOBBED_SPLASH)
                    || Math.abs(observed.getFirst().visual().radius() - 4.0) > 1.0E-6) {
                throw new AssertionError("Plant lob VFX must use the shared area service with its runtime radius");
            }
            context.succeed();
        } finally {
            TowerVfxService.setAreaEffectTestObserver(null);
        }
    }

    @GameTest
    public void armyDebugCommandsParse(GameTestHelper context) {
        var dispatcher = context.getLevel().getServer().getCommands().getDispatcher();
        for (String effect : List.of("promotion", "command", "barrage", "discharge")) {
            String command = "semiontd-debug vfx army " + effect;
            var parsed = dispatcher.parse(command, context.getLevel().getServer().createCommandSourceStack());
            if (parsed.getContext().getNodes().isEmpty() || parsed.getReader().canRead()) {
                throw new AssertionError("Expected /" + command + " to parse completely");
            }
        }
        context.succeed();
    }

    @GameTest
    public void rayDensityKeepsEveryAttackVisibleAfterSoftBudgetIsExhausted(GameTestHelper context) {
        if (TowerVfxService.preferredRayPointCount(100.0) != 64
                || TowerVfxService.adaptiveRayPointCount(100.0, 0) != 12) {
            throw new AssertionError("Ray density should retain the twelve-point essential ray");
        }
        context.succeed();
    }

    @GameTest
    public void areaCaptureUsesRuntimeRadiusAndSamplesAtMostFourHits(GameTestHelper context) {
        List<AreaEffectVfxEvent> observed = new ArrayList<>();
        TowerVfxService.setAreaEffectTestObserver(observed::add);
        try {
            UUID owner = UUID.nameUUIDFromBytes("vfx-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            TestTower runtimeTower = new TestTower(VillagerTowers.T1_CAT_TOWER, owner);
            SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
            tower.setPos(2.0, 2.0, 2.0);
            tower.configure(runtimeTower, null);

            List<Vec3> hits = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                hits.add(new Vec3(5.0 + index * 0.2, 2.0, 2.0));
            }
            TowerVfxService.showAreaEffect(
                    tower,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "test_area"),
                    AreaVfxStyles.SPLASH,
                    new Vec3(5.0, 2.0, 2.0),
                    3.25,
                    hits,
                    6,
                    6,
                    2
            );

            if (observed.size() != 1) {
                throw new AssertionError("Expected one area-damage event");
            }
            var visual = observed.getFirst().visual();
            if (!visual.styleId().equals(AreaVfxStyles.SPLASH)
                    || Math.abs(visual.radius() - 3.25) > 1.0E-6
                    || visual.appliedCount() != 6
                    || visual.killedCount() != 2
                    || visual.sampledAppliedPositions().size() != 4) {
                throw new AssertionError("Area event should retain runtime radius, counts, and four hit samples: " + visual);
            }
            context.succeed();
        } finally {
            TowerVfxService.setAreaEffectTestObserver(null);
        }
    }

    @GameTest
    public void areaEffectApiRejectsInvalidRadiusAndWorkerThreadCalls(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("area-api-validation-owner".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TestTower runtimeTower = new TestTower(VillagerTowers.T1_CAT_TOWER, owner);
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.setPos(2.0, 2.0, 2.0);
        tower.configure(runtimeTower, null);
        ResourceLocation effectId = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "test_validation");

        try {
            new MonsterAreaEffectRequest(effectId, tower, tower.position(), 0.0, Set.of(), null, AreaVfxSpec.none());
            throw new AssertionError("Zero-radius area requests should be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }

        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                effectId,
                tower,
                tower.position(),
                1.0,
                Set.of(),
                null,
                AreaVfxSpec.none()
        );
        try {
            Throwable failure = CompletableFuture.supplyAsync(() -> {
                        try {
                            SemionTdApi.areaEffects().applyToMonsters(request, ignored -> AreaEffectOutcome.UNCHANGED);
                            return null;
                        } catch (Throwable throwable) {
                            return throwable;
                        }
                    })
                    .get(3, TimeUnit.SECONDS);
            if (!(failure instanceof IllegalStateException)) {
                throw new AssertionError("Worker-thread area calls should fail with IllegalStateException", failure);
            }
            context.succeed();
        } catch (Exception exception) {
            throw new AssertionError("Area API thread validation did not complete", exception);
        }
    }

    @GameTest
    public void apelCollectorRunsOffThreadWithoutWorldAccess(GameTestHelper context) {
        try {
            List<Vec3> points = CompletableFuture.supplyAsync(() ->
                            TowerVfxService.collectLinePoints(Vec3.ZERO, new Vec3(4.0, 0.0, 0.0), 16))
                    .get(3, TimeUnit.SECONDS);
            if (points.size() != 16 || points.getLast().x <= points.getFirst().x) {
                throw new AssertionError("APEL collector should calculate the requested line points off-thread");
            }
            context.succeed();
        } catch (Exception exception) {
            throw new AssertionError("APEL collector failed off-thread", exception);
        }
    }

    @GameTest
    public void spectatorPolicyRequiresMatchWorldAndSixtyFourBlockRadius(GameTestHelper context) {
        if (!TowerVfxService.shouldIncludeSpectator(true, true, 64.0 * 64.0)
                || TowerVfxService.shouldIncludeSpectator(false, true, 1.0)
                || TowerVfxService.shouldIncludeSpectator(true, false, 1.0)
                || TowerVfxService.shouldIncludeSpectator(true, true, 64.1 * 64.1)) {
            throw new AssertionError("Spectator policy should require match membership, same world, and 64-block radius");
        }
        context.succeed();
    }

    @GameTest
    public void gcbCirclePayloadEncodesRuntimeRadiusAndCenter(GameTestHelper context) {
        GCBParticleS2CPacket packet = new GCBParticleS2CPacket(
                "minecraft:flame",
                GCBParticleS2CPacket.Vec.ZERO,
                1,
                0.0,
                false,
                "",
                new GCBParticleS2CPacket.Circle(
                        GCBParticleS2CPacket.Vec.UNIT_Y,
                        GCBParticleS2CPacket.Vec.ZERO,
                        48,
                        360.0,
                        new GCBParticleS2CPacket.ShapeOptions(
                                3.5,
                                0.12,
                                GCBParticleS2CPacket.Vec.UNIT_X,
                                GCBParticleS2CPacket.Vec.UNIT_Y,
                                GCBParticleS2CPacket.Vec.UNIT_Z,
                                null
                        ),
                        new GCBParticleS2CPacket.Vec(2.0, 4.0, 6.0)
                )
        );
        String encoded = packet.encode();
        if (!encoded.contains("circle") || !encoded.contains("3.5") || !encoded.contains("2.0,4.0,6.0")) {
            throw new AssertionError("GCB payload should retain shape, radius, and center: " + encoded);
        }
        context.succeed();
    }

    private static void assertPalette(TowerType type, BuilderPalette expected) {
        BuilderPalette actual = TowerVfxService.paletteFor(type);
        if (actual != expected) {
            throw new AssertionError("Expected " + type.id() + " to use " + expected + " but got " + actual);
        }
    }

    private static void assertDustColor(DustParticleOptions particle, int expectedRgb, String label) {
        var actual = particle.getColor();
        float expectedRed = ((expectedRgb >>> 16) & 0xFF) / 255.0F;
        float expectedGreen = ((expectedRgb >>> 8) & 0xFF) / 255.0F;
        float expectedBlue = (expectedRgb & 0xFF) / 255.0F;
        float tolerance = 1.0E-6F;
        if (Math.abs(actual.x - expectedRed) > tolerance
                || Math.abs(actual.y - expectedGreen) > tolerance
                || Math.abs(actual.z - expectedBlue) > tolerance) {
            throw new AssertionError(label + " expected #" + String.format("%06X", expectedRgb)
                    + " but got " + actual);
        }
    }

    private static final class TestTower extends Tower {
        private TestTower(TowerType type, UUID owner) {
            super(type, owner, TeamId.RED, 1, new GridPosition(2, 2, 2));
        }

        @Override
        public boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
