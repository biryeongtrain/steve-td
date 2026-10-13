package kim.biryeong.semiontd.tower.magicschool;

import static kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowerIntegrationTest.requireClose;

import java.util.*;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class MagicSchoolAugmentCombatTest implements kim.biryeong.semiontd.gametest.RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void openMenusRefreshAugmentUnlocksAndTheCurriculumCannotSellCurses(GameTestHelper context) {
        var player = context.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        var game = MagicSchoolTowerIntegrationTest.game(context, owner, UUID.randomUUID());
        try {
            var lane = game.playerLane(owner).orElseThrow();
            var plot = GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3)));
            var school = (HogwartsTower) MagicSchoolTowerIntegrationTest.add(lane, MagicSchoolTowers.HOGWARTS, plot);
            var wizard = (MagicSchoolWizardTower) MagicSchoolTowerIntegrationTest.add(lane, MagicSchoolTowers.BRAVE_ARCHWIZARD,
                    new GridPosition(plot.x() + 1, plot.y(), plot.z()));
            game.players().get(owner).economy().addDiamond(350);
            MagicSchoolCurriculum.purchase(owner, MagicSchoolCurriculum.Upgrade.ADVANCED_SPELLS, game.players().get(owner).economy());
            var curriculum = new CurriculumGui(player, game, school);
            var spells = new SpellGui(player, game, wizard);
            var economy = game.players().get(owner).economy();
            long diamonds = economy.diamond();
            curriculum.click(49, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ContainerInput.PICKUP);
            check(economy.diamond() == diamonds && MagicSchoolCurriculum.canUpgradeThisRound(owner, game.currentRound()),
                    "An empty curriculum slot cannot spend currency or consume an upgrade.");
            check(curriculum.getGuiElement(49) == null || curriculum.getGuiElement(49).getItemStack().isEmpty(), "The curse curriculum slot must be empty.");
            lane.assignAugmentSnapshot(snapshot(MagicSchoolAugments.MUGGLE_WAND, MagicSchoolAugments.GRADUATE_SCHOOL,
                    MagicSchoolAugments.UNFORGIVABLE_CURSES));
            curriculum.onTick();
            spells.onTick();
            check(curriculum.getGuiElement(49) == null || curriculum.getGuiElement(49).getItemStack().isEmpty(), "Prism selection must not restore the removed button.");
            check(lore(spells, 46).contains("클릭: 주문 지정"), "Prism curses must still unlock in the wizard spell menu.");
            check(lore(spells, 2).contains("클릭: 주문 지정"), "An already open spell menu must refresh silver eligibility.");
            spells.click(2, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ContainerInput.PICKUP);
            check(wizard.selectedSpell() == MagicSchoolSpell.MUGGLE_WAND, "The visible Muggle button must actually equip the spell.");
            curriculum.click(53, eu.pb4.sgui.api.ClickType.MOUSE_LEFT, net.minecraft.world.inventory.ContainerInput.PICKUP);
            requireClose(0, wizard.proficiency(), "The removed mastery slot must not grant proficiency.");
            wizard.gainProficiency(10000, lane);
            requireClose(1250, wizard.proficiency(), "Ordinary mastery gains must still honor the graduate cap.");
        } finally { game.close(); }
        context.succeed();
    }

    private static String lore(eu.pb4.sgui.api.gui.SimpleGui gui, int slot) {
        return gui.getGuiElement(slot).getItemStack().get(net.minecraft.core.component.DataComponents.LORE).lines().stream()
                .map(net.minecraft.network.chat.Component::getString).collect(java.util.stream.Collectors.joining(" "));
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void borrowedDecoysDoNotPreventDefeatAfterTheLastFightingTowerDies(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var owner = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK);
            var recipient = f.lane("villager_towers");
            var wizard = f.wizard(owner, MagicSchoolTowers.FRESHMAN, 0);
            var nativeTower = f.generic(recipient, 100, 0);
            var enemy = f.enemy(recipient, 1);
            var corpse = f.enemy(recipient, 2);
            MagicSchoolCurriculum.purchase(owner.ownerPlayer(), MagicSchoolCurriculum.Upgrade.TRANSFIGURATION,
                    f.players.get(owner.ownerPlayer()).economy());
            f.start();
            f.tick();
            corpse.runtimeMonster().syncHealth(0);
            MagicSchoolTransfiguration.onKill(wizard, wizard.runtimeEntity(owner).orElseThrow(), corpse);
            check(recipient.reinforcingTowers().stream().anyMatch(MagicSchoolBarrelTower.class::isInstance), "A borrowed decoy must exist for this regression.");
            recipient.killTower(nativeTower);
            owner.killTower(wizard);
            f.tick();
            check(recipient.laneDefenseBroken() && enemy.runtimeMonster().inFinalDefenseCombat(),
                    "Temporary decoys cannot keep an otherwise wiped supported lane alive.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void incomingBroomSelectsHighestMaximumThenCurrentHealthAndReturnsAfterClear(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var donor = f.lane("villager_towers");
            var school = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK);
            var low = f.generic(donor, 100, 0);
            var wounded = f.generic(donor, 200, 1);
            var selected = f.generic(donor, 200, 2);
            wounded.runtimeEntity(donor).orElseThrow().setHealth(120);
            selected.runtimeEntity(donor).orElseThrow().setHealth(160);
            var nativeWizard = f.wizard(school, MagicSchoolTowers.FRESHMAN, 0);
            var enemy = f.enemy(school, 1);
            f.start();
            var entity = selected.runtimeEntity(donor).orElseThrow();
            entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .2, 100);
            int entityId = entity.getId();
            f.tick();
            check(selected.reinforcementLane() == school, "The healthiest maximum-HP tie must join the uncleared school lane.");
            check(low.deployedAtFinalDefense() && wounded.deployedAtFinalDefense(), "Other donor towers enter final defense.");
            check(selected.attachedLane() == donor && donor.towers().contains(selected) && !school.towers().contains(selected),
                    "Borrowing must retain the owner registration, capacity, and economy.");
            check(entity.getId() == entityId && entity.laneId() == school.laneId(), "The existing entity switches combat lane without respawning.");
            requireClose(160, entity.getHealth(), "Moving must preserve current health.");
            requireClose(.2, entity.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Moving must preserve buffs.");
            check(entity.isValidAttackTarget(enemy) && enemy.canTargetDefense(entity), "Both attack and aggro must recognize the supported lane.");
            school.killTower(nativeWizard);
            f.tick();
            check(!school.laneDefenseBroken() && !enemy.runtimeMonster().inFinalDefenseCombat(), "A living visitor keeps the lane defended.");
            enemy.runtimeMonster().syncHealth(0);
            f.tick();
            check(selected.reinforcementLane() == null && selected.deployedAtFinalDefense(), "Clearing the recipient releases the visitor.");
            check(entity.laneId() == donor.laneId(), "Final-defense combat restores its owner's lane identity.");
            f.team.resetForRound();
            check(selected.position().equals(selected.originalPosition()) && !selected.deployedAtFinalDefense(), "The next round restores original placement.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void outgoingBroomSendsDistinctWizardsAndExcludesSchoolDemonAndDefeatedLanes(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var school = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK);
            var first = f.lane("villager_towers");
            var second = f.lane("animal_towers");
            var otherSchool = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK);
            var demon = f.lane("demon_lord_towers");
            var broken = f.lane("villager_towers");
            var strong = f.wizard(school, MagicSchoolTowers.KIND_ARCHWIZARD, 0);
            var next = f.wizard(school, MagicSchoolTowers.GRYFFINDOR, 1);
            var spare = f.wizard(school, MagicSchoolTowers.FRESHMAN, 2);
            f.school(school, 3);
            for (var lane : List.of(first, second, otherSchool, demon, broken)) {
                f.generic(lane, 100, 0);
                f.enemy(lane, 1);
            }
            f.start();
            broken.killTower(broken.towers().getFirst());

            MagicSchoolBroomsticks.beforeFinalDefense(f.team, school, f.players);
            school.moveTowersToFinalDefense();
            check(strong.reinforcementLane() == first && next.reinforcementLane() == second, "Send different wizards, strongest first, in lane order.");
            check(spare.deployedAtFinalDefense(), "Excluded destinations must not consume the spare wizard.");
            check(otherSchool.reinforcingTowers().isEmpty() && demon.reinforcingTowers().isEmpty()
                    && broken.reinforcingTowers().isEmpty(), "Never support Magic School, Demon Lord, or wiped lanes.");
            MagicSchoolBroomsticks.beforeFinalDefense(f.team, school, f.players);
            check(first.reinforcingTowers().size() == 1 && second.reinforcingTowers().size() == 1, "Repeated callbacks cannot resend towers.");
            f.team.forceFinalDefense();
            check(strong.deployedAtFinalDefense() && next.deployedAtFinalDefense()
                    && strong.reinforcementLane() == null && next.reinforcementLane() == null, "Timeout must gather all visitors.");
            f.team.resetForRound();
            check(strong.position().equals(strong.originalPosition()), "Reset must restore all borrowed placements.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void borrowedWizardAttacksSupportedEnemiesWithSplashAndCreatesLaneLocalProps(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var owner = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK, MagicSchoolAugments.GRADUATE_SCHOOL);
            var recipient = f.lane("villager_towers");
            var wizard = f.wizard(owner, MagicSchoolTowers.BRAVE_ARCHWIZARD, 0);
            f.generic(recipient, 100, 0);
            var primary = f.enemy(recipient, 1);
            var splash = f.enemy(recipient, 2);
            var funds = f.players.get(owner.ownerPlayer()).economy();
            for (int tier = 2; tier <= 4; tier++) MagicSchoolCurriculum.unlockSpellTier(owner.ownerPlayer(), tier, funds);
            check(wizard.selectSpell(MagicSchoolSpell.BOMBARDA), "Spell must equip before combat.");
            MagicSchoolCurriculum.purchase(owner.ownerPlayer(), MagicSchoolCurriculum.Upgrade.TRANSFIGURATION, funds);
            f.start();
            double proficiency = wizard.proficiency();
            f.tick();
            check(wizard.reinforcementLane() == recipient && wizard.maxProficiency() == 1250, "Borrowed wizards retain owner augments.");
            requireClose(proficiency, wizard.proficiency(), "Movement must not grant another wave-start award.");
            var source = wizard.runtimeEntity(owner).orElseThrow();
            primary.setPos(source.position().add(.5, 0, 0));
            splash.setPos(source.position().add(1.5, 0, 0));
            var foreign = f.enemy(owner, 2);
            foreign.setPos(source.position().add(1, 0, 0));
            new TowerAttackMonsterGoal(source).tick();
            check(primary.getHealth() < 10000 && splash.getHealth() < 10000, "Actual attack AI and shared splash must damage the supported lane.");
            requireClose(10000, foreign.getHealth(), "Splash must exclude the old lane at the same position.");
            primary.runtimeMonster().syncHealth(0);
            MagicSchoolTransfiguration.onKill(wizard, source, primary);
            var barrel = owner.towers().stream().filter(MagicSchoolBarrelTower.class::isInstance).map(MagicSchoolBarrelTower.class::cast)
                    .findFirst().orElseThrow();
            check(barrel.combatLane() == recipient && barrel.runtimeEntity(owner).orElseThrow().defendsLane(recipient.laneId()),
                    "Decoys spawned by borrowed wizards must defend the recipient while retaining owner state.");
            MagicSchoolCurriculum.purchase(owner.ownerPlayer(), MagicSchoolCurriculum.Upgrade.EXPLOSIVE_BARRELS, funds);
            MagicSchoolTransfiguration.upgradeBarrels(owner.ownerPlayer());
            var bombs = context.getLevel().getEntitiesOfClass(MagicSchoolBombBarrelEntity.class, source.getBoundingBox().inflate(8));
            check(bombs.size() == 1 && source.isValidAttackTarget(bombs.getFirst()), "Converted bombs must remain attackable on the supported lane.");
            double health = splash.getHealth();
            recipient.moveTowersToFinalDefense();
            check(bombs.getFirst().isRemoved(), "Finishing a supported lane must remove borrowed props.");
            requireClose(health, splash.getHealth(), "Cleanup must not explode bombs.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void exclusiveUnlocksApplyToExistingAndNewTowersWhileCursesStayOwnerUnique(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var lane = f.lane("magic_school");
            var existing = f.wizard(lane, MagicSchoolTowers.WISE_ARCHWIZARD, 0);
            check(!existing.selectSpell(MagicSchoolSpell.MUGGLE_WAND) && !existing.selectSpell(MagicSchoolSpell.CRUCIO), "Both special spell groups start locked.");
            lane.assignAugmentSnapshot(snapshot(MagicSchoolAugments.MUGGLE_WAND, MagicSchoolAugments.GRADUATE_SCHOOL,
                    MagicSchoolAugments.UNFORGIVABLE_CURSES));
            check(existing.selectSpell(MagicSchoolSpell.MUGGLE_WAND), "Existing wizard receives silver immediately.");
            check(existing.runtimeEntity(lane).orElseThrow().attackIntervalTicks() == 20, "Equipped Muggle has its fixed interval.");
            existing.gainProficiency(10000, lane);
            requireClose(1250, existing.proficiency(), "Graduate maximum applies to existing archwizards.");
            var raven = f.wizard(lane, MagicSchoolTowers.RAVENCLAW, 1);
            check(existing.selectSpell(MagicSchoolSpell.CRUCIO), "Prism bypasses curriculum tier purchases.");
            check(!raven.selectSpell(MagicSchoolSpell.CRUCIO) && raven.selectSpell(MagicSchoolSpell.IMPERIO), "Ravenclaw T2 can equip a different curse, not a duplicate.");
            var freshman = f.wizard(lane, MagicSchoolTowers.FRESHMAN, 2);
            check(!freshman.selectSpell(MagicSchoolSpell.AVADA_KEDAVRA), "Prism must retain caster tier restrictions.");
            var other = f.lane("magic_school", MagicSchoolAugments.UNFORGIVABLE_CURSES);
            check(f.wizard(other, MagicSchoolTowers.WISE_ARCHWIZARD, 0).selectSpell(MagicSchoolSpell.CRUCIO), "Curse uniqueness is per owner.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void incomingBroomNeverBorrowsFromExcludedBuildersAndStopsWhenVisitorsDie(GameTestHelper context) {
        try (var f = new Fixture(context)) {
            var school = f.lane("magic_school", MagicSchoolAugments.BROOMSTICK);
            var otherSchool = f.lane("magic_school");
            var demon = f.lane("demon_lord_towers");
            var donor = f.lane("villager_towers");
            var nativeWizard = f.wizard(school, MagicSchoolTowers.FRESHMAN, 0);
            var excludedWizard = f.wizard(otherSchool, MagicSchoolTowers.KIND_ARCHWIZARD, 0);
            var excludedDemon = f.generic(demon, 1000, 0);
            var visitor = f.generic(donor, 200, 0);
            var enemy = f.enemy(school, 1);
            f.start();
            f.tick();
            check(excludedWizard.deployedAtFinalDefense() && excludedDemon.deployedAtFinalDefense(), "Excluded builders must never lend towers.");
            check(visitor.reinforcementLane() == school, "An ordinary allied builder may lend a tower.");
            school.killTower(nativeWizard);
            donor.killTower(visitor);
            f.tick();
            check(school.laneDefenseBroken() && enemy.runtimeMonster().inFinalDefenseCombat(), "Losing the last visitor must still break defense.");
        }
        context.succeed();
    }

    static AugmentSnapshot snapshot(String... ids) {
        return new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(ids).map(id ->
                new PlayerAugmentState.Selection(5, AugmentCatalog.find(id).orElseThrow().rarity(), id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper context;
        final TeamLaneGroup team = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        final Map<UUID, SemionPlayer> players = new LinkedHashMap<>();
        Fixture(GameTestHelper context) {
            this.context = context;
            ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        }
        PlayerLane lane(String job, String... augments) {
            int id = team.lanes().size() + 1;
            UUID owner = UUID.randomUUID();
            Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(id, 2, 2)));
            Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(8, 2, 13)));
            var layout = new LaneRegionLayout(id, spawn, List.of(spawn, boss), boss,
                    BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 5, 14))),
                    List.of(pos(id, 12), pos(id, 13)));
            var lane = new PlayerLane(TeamId.RED, id, owner, context.getLevel(), layout);
            var player = new SemionPlayer(owner, "broom-test-" + id, TeamId.RED, id, new PlayerEconomy(EconomyConfig.defaultConfig()));
            player.assignJob(JobRegistry.find(Identifier.parse("semion-td:" + job)).orElseThrow());
            player.economy().addDiamond(100000);
            players.put(owner, player);
            team.addLane(lane);
            lane.assignAugmentSnapshot(snapshot(augments));
            return lane;
        }
        GridPosition pos(int x, int z) { return GridPosition.from(context.absolutePos(new BlockPos(x, 1, z))); }
        EntityBackedTower generic(PlayerLane lane, double hp, int offset) {
            var type = TowerType.builder("broom_test", "지원 검증").maxHealth(hp).damage(20).range(8).attackIntervalTicks(20).build();
            var tower = new ProductionTower(type, lane.ownerPlayer(), lane.teamId(), lane.laneId(), pos(lane.laneId(), 3 + offset));
            lane.addTower(tower);
            tower.runtimeEntity(lane).orElseThrow().setNoAi(true);
            return tower;
        }
        MagicSchoolWizardTower wizard(PlayerLane lane, TowerType type, int offset) {
            var tower = (MagicSchoolWizardTower) MagicSchoolTowerIntegrationTest.add(lane, type, pos(lane.laneId(), 3 + offset));
            tower.runtimeEntity(lane).orElseThrow().setNoAi(true);
            return tower;
        }
        void school(PlayerLane lane, int offset) { MagicSchoolTowerIntegrationTest.add(lane, MagicSchoolTowers.HOGWARTS, pos(lane.laneId(), 3 + offset)); }
        SemionMonsterEntity enemy(PlayerLane lane, int offset) {
            var monster = new Monster("broom-enemy", lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                    10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0);
            var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            entity.configureFrom(monster, lane.laneLayout());
            entity.setNoAi(true);
            entity.setNoGravity(true);
            entity.setPos(Vec3.atCenterOf(new BlockPos(pos(lane.laneId(), 6 + offset).x(), pos(lane.laneId(), 6 + offset).y() + 1,
                    pos(lane.laneId(), 6 + offset).z())));
            context.getLevel().addFreshEntity(entity);
            monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
            lane.activeMonsters().add(monster);
            return entity;
        }
        void start() { team.lanes().forEach(lane -> lane.markWaveStarted(1)); }
        void tick() { team.tick(context.getLevel().getServer(), null, players); }
        @Override public void close() {
            players.keySet().forEach(MagicSchoolCurriculum::clear);
            team.closeRuntime();
        }
    }
}
