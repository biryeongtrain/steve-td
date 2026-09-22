package kim.biryeong.semiontd.tower.adversary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroCompanionTower;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.hero.HeroTower;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class HeroAdversaryAugmentGameTest {
    @GameTest
    public void selectionRefreshesHeroAndFoxHealthWithoutLosingTimedBonusesOrHealthFraction(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            HeroTower hero = hero(context, lane);
            var fox = new AdversaryFoxTower(AdversaryTowers.typeFor(FoxForm.GOLDEN_FANG), lane.ownerPlayer(),
                    TeamId.RED, 1, position(context));
            lane.addTower(fox);
            List<EntityBackedTower> towers = List.of(hero, fox);
            double[] base = {hero.effectBaseMaxHealth(), fox.effectBaseMaxHealth()};
            for (EntityBackedTower tower : towers) {
                var entity = entity(lane, tower);
                entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, .25, 100);
                entity.applyTimedEffect(TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS, 20, 100);
                tower.syncHealth(tower.currentMaxHealth() * .4);
                entity.setHealth((float) tower.health());
            }
            lane.assignAugmentSnapshot(snapshot("job_hero_party_p", "job_adversary_towers_p"));
            for (int i = 0; i < towers.size(); i++) {
                EntityBackedTower tower = towers.get(i);
                var entity = entity(lane, tower);
                double expected = base[i] * (i == 0 ? 2.5 : 2) * 1.25 + 20;
                close(expected, tower.currentMaxHealth(), "Selection updates family base while keeping timed percent and flat health");
                close(expected * .4, tower.health(), "Selection keeps logical health fraction");
                close(tower.currentMaxHealth(), entity.getMaxHealth(), "Selection synchronizes entity maximum health");
                close(tower.health(), entity.getHealth(), "Selection synchronizes entity current health");
            }
            lane.assignAugmentSnapshot(AugmentSnapshot.none());
            for (int i = 0; i < towers.size(); i++) {
                close(base[i] * 1.25 + 20, towers.get(i).currentMaxHealth(), "Removing card preserves existing health modifiers");
                close(towers.get(i).currentMaxHealth() * .4, towers.get(i).health(), "Removing card keeps the health fraction");
            }
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void heroRotatesCompanionsAndGeneratedAttacksDoNotCharge(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            HeroTower hero = hero(context, lane);
            var knight = companion(lane, HeroCompanionRole.KNIGHT, hero.position());
            var archer = companion(lane, HeroCompanionRole.ARCHER, hero.position());
            lane.assignAugmentSnapshot(snapshot("job_hero_party_s"));
            lane.markWaveStarted(5);
            SemionTowerEntity source = entity(lane, hero);
            SemionMonsterEntity target = monster(lane, source.position().add(1, 0, 0), 100000);
            double before = target.runtimeMonster().health();
            hit(source, target, 2);
            close(before, target.runtimeMonster().health(), "Silver waits for three successful hits");
            hit(source, target, 1);
            double knightDamage = knight.resolveBasicAttackOutgoingDamage(entity(lane, knight), target,
                    entity(lane, knight).attackDamageAmount(target));
            close(before - knightDamage, target.runtimeMonster().health(), "First companion follows placement order");
            before = target.runtimeMonster().health();
            AugmentCombat.runWithoutTriggers(() -> hit(source, target, 3));
            close(before, target.runtimeMonster().health(), "Generated hero attacks cannot charge silver");
            hit(source, target, 3);
            double archerDamage = archer.resolveBasicAttackOutgoingDamage(entity(lane, archer), target,
                    entity(lane, archer).attackDamageAmount(target));
            close(before - archerDamage, target.runtimeMonster().health(), "Second proc uses the second companion");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void heroPartyBlastRequiresFourLivingCompanionsAndCapsTwelveTargets(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            HeroTower hero = hero(context, lane);
            List<HeroCompanionTower> companions = new ArrayList<>();
            for (HeroCompanionRole role : List.of(HeroCompanionRole.KNIGHT, HeroCompanionRole.ARCHER,
                    HeroCompanionRole.MAGE, HeroCompanionRole.ROGUE)) {
                companions.add(companion(lane, role, hero.position()));
            }
            lane.assignAugmentSnapshot(snapshot("job_hero_party_g2"));
            lane.markWaveStarted(5);
            SemionTowerEntity source = entity(lane, hero);
            List<SemionMonsterEntity> targets = new ArrayList<>();
            for (int i = 0; i < 14; i++) targets.add(monster(lane, source.position().add(1 + i * .1, 0, 0), 100000));
            double sum = hero.resolveBasicAttackOutgoingDamage(source, null, source.attackDamageAmount(null));
            for (var companion : companions) {
                var entity = entity(lane, companion);
                sum += companion.resolveBasicAttackOutgoingDamage(entity, null, entity.attackDamageAmount(null));
            }
            hit(source, targets.getFirst(), 5);
            long damaged = targets.stream().filter(target -> target.runtimeMonster().health() < 100000).count();
            close(12, damaged, "Gold blast includes at most twelve enemies");
            close(100000 - sum * 3, targets.getFirst().runtimeMonster().health(), "Gold sums all five attack stats at 300 percent");
            entity(lane, companions.getFirst()).setHealth(0);
            double before = targets.getFirst().runtimeMonster().health();
            hit(source, targets.getFirst(), 5);
            close(before, targets.getFirst().runtimeMonster().health(), "A dead companion disables the gold blast");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void legendarySlashUsesPhysicalDamageAndTwelveTargetCap(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            HeroTower hero = hero(context, lane);
            double originalHealth = hero.currentMaxHealth();
            lane.assignAugmentSnapshot(snapshot("job_hero_party_p"));
            lane.markWaveStarted(5);
            close(originalHealth * 2.5, hero.currentMaxHealth(), "Legend health is applied when selected");
            SemionTowerEntity source = entity(lane, hero);
            List<SemionMonsterEntity> targets = new ArrayList<>();
            for (int i = 0; i < 13; i++) targets.add(monster(lane, source.position().add(1 + i * .3, 0, 0), 100000));
            SemionMonsterEntity behind = monster(lane, source.position().add(-1, 0, 0), 100000);
            SemionMonsterEntity outside = monster(lane, source.position().add(2, 0, 1.6), 100000);
            double damage = hero.resolveBasicAttackOutgoingDamage(source, null, source.attackDamageAmount(null));
            hit(source, targets.getFirst(), 3);
            close(12, targets.stream().filter(target -> target.runtimeMonster().health() < 100000).count(), "Slash caps twelve targets");
            close(100000 - damage * 5, targets.getFirst().runtimeMonster().health(), "Slash deals 500 percent attack damage");
            close(100000, behind.runtimeMonster().health(), "Slash excludes enemies behind the hero");
            close(100000, outside.runtimeMonster().health(), "Slash width is three blocks");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void adaptationUsesOpeningCapsAndClearsWhileRivalMatchRejectsGeneratedKills(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            GridPosition p = position(context);
            var fox = new AdversaryFoxTower(AdversaryTowers.FOX, lane.ownerPlayer(), TeamId.RED, 1, p);
            lane.addTower(fox);
            lane.assignAugmentSnapshot(snapshot("job_adversary_towers_g2", "job_adversary_towers_g1"));
            lane.markWaveStarted(5);
            double health = fox.currentMaxHealth();
            double damage = fox.form().damage();
            var first = rival(lane, p, 5);
            var source = entity(lane, fox);
            double healthGain = Math.min(health, first.runtimeMonster().maxHealth() * .2);
            double damageGain = Math.min(damage * 1.5, first.runtimeMonster().attackDamage() * .5);
            fox.onKill(source, first, 1);
            close(health + healthGain, fox.currentMaxHealth(), "Adaptation absorbs rival max health");
            close(damage + damageGain, fox.modifyResolvedAttackDamage(source, null, damage), "Adaptation absorbs rival attack");
            fox.onKill(source, first, 1);
            close(health + healthGain, fox.currentMaxHealth(), "Duplicate proxy credit cannot absorb again");
            for (int i = 0; i < 30; i++) fox.onKill(source, rival(lane, p, 30), 1);
            close(health * 2, fox.currentMaxHealth(), "Health cap remains fixed at opening health");
            close(damage * 2.5, fox.modifyResolvedAttackDamage(source, null, damage), "Damage cap remains fixed at opening damage");
            lane.resetForRound();
            close(health, fox.currentMaxHealth(), "Round end removes absorbed health");
            close(damage, fox.modifyResolvedAttackDamage(entity(lane, fox), null, damage), "Round end removes absorbed damage");
            lane.markWaveStarted(6);
            var guarded = rival(lane, p, 6);
            AugmentCombat.runWithoutTriggers(() -> fox.onKill(entity(lane, fox), guarded, 1));
            close(health, fox.currentMaxHealth(), "Generated kill cannot absorb");
            close(0, entity(lane, fox).activeTimedEffectMagnitude(TimedEffectType.TOWER_DAMAGE_BONUS), "Generated kill cannot proc rival match");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void darkHeroRequiresOtherOwnedTowerAndExplodesOnlyOnce(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            GridPosition p = position(context);
            var fox = new AdversaryFoxTower(AdversaryTowers.FOX, lane.ownerPlayer(), TeamId.RED, 1, p);
            var other = new ProductionTower(HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, 1),
                    lane.ownerPlayer(), TeamId.RED, 1, p);
            lane.addTower(fox);
            lane.addTower(other);
            lane.assignAugmentSnapshot(snapshot("job_adversary_towers_s"));
            lane.markWaveStarted(5);
            var proxy = rival(lane, p, 5);
            var inside = monster(lane, proxy.position().add(1, 0, 0), 100000);
            var outside = monster(lane, proxy.position().add(3.1, 0, 0), 100000);
            AdversaryAugments.onKill(fox, entity(lane, fox), proxy);
            close(100000, inside.runtimeMonster().health(), "Fox kills do not trigger dark hero");
            AugmentCombat.runWithoutTriggers(() -> AdversaryAugments.onKill(other, entity(lane, other), proxy));
            close(100000, inside.runtimeMonster().health(), "Generated kills do not trigger dark hero");
            AdversaryAugments.onKill(other, entity(lane, other), proxy);
            close(100000 - proxy.runtimeMonster().maxHealth() * .25, inside.runtimeMonster().health(), "Other owned tower explodes rival for 25 percent health");
            double after = inside.runtimeMonster().health();
            AdversaryAugments.onKill(other, entity(lane, other), proxy);
            close(after, inside.runtimeMonster().health(), "One proxy explodes once");
            close(100000, outside.runtimeMonster().health(), "Dark hero radius remains three");
            context.succeed();
        } finally { cleanup(lane); }
    }

    @GameTest
    public void finaleAddsTwoDistinctTargetsForTenSeconds(GameTestHelper context) {
        PlayerLane lane = lane(context);
        try {
            GridPosition p = position(context);
            var fox = new AdversaryFoxTower(AdversaryTowers.FOX, lane.ownerPlayer(), TeamId.RED, 1, p);
            lane.addTower(fox);
            lane.assignAugmentSnapshot(snapshot("job_adversary_towers_p"));
            lane.markWaveStarted(5);
            var source = entity(lane, fox);
            var proxy = rival(lane, p, 5);
            fox.onKill(source, proxy, 1);
            proxy.discard();
            var primary = monster(lane, source.position().add(1, 0, 0), 100000);
            List<SemionMonsterEntity> extras = new ArrayList<>();
            for (int i = 0; i < 6; i++) extras.add(monster(lane, source.position().add(1 + i * .1, 0, 0), 100000));
            source.recordAttack(primary, 10, 10, 10, false);
            close(5, extras.stream().filter(target -> target.runtimeMonster().health() < 100000).count(),
                    "Three native splash targets plus two different finale targets");
            close(2, extras.stream().filter(target -> Math.abs(target.runtimeMonster().health() - 99990) < .001).count(),
                    "Two finale targets take full primary damage");
            for (int i = 0; i < 200; i++) fox.tick(lane);
            List<Double> before = extras.stream().map(target -> target.runtimeMonster().health()).toList();
            source.recordAttack(primary, 10, 10, 10, false);
            long changed = 0;
            for (int i = 0; i < extras.size(); i++) if (extras.get(i).runtimeMonster().health() < before.get(i)) changed++;
            close(3, changed, "At ten seconds only native splash remains");
            context.succeed();
        } finally { cleanup(lane); }
    }

    private static void hit(SemionTowerEntity source, SemionMonsterEntity target, int count) {
        for (int i = 0; i < count; i++) source.recordAttack(target, 1, 1, 1, false);
    }

    private static HeroTower hero(GameTestHelper context, PlayerLane lane) {
        GridPosition p = position(context);
        var hero = new HeroTower(HeroPartyTowers.HERO, lane.ownerPlayer(), TeamId.RED, 1, p, p);
        lane.addTower(hero);
        return hero;
    }

    private static HeroCompanionTower companion(PlayerLane lane, HeroCompanionRole role, GridPosition p) {
        var companion = new HeroCompanionTower(HeroPartyTowers.companion(role, 1), lane.ownerPlayer(), TeamId.RED, 1, p, p);
        lane.addTower(companion);
        return companion;
    }

    private static SemionMonsterEntity rival(PlayerLane lane, GridPosition p, int round) {
        var rival = new AdversaryRivalTower(AdversaryTowers.BREEZE_RIVAL, lane.ownerPlayer(), TeamId.RED, 1, p);
        lane.addTower(rival);
        rival.onWaveStarted(lane, round);
        var proxy = (SemionMonsterEntity) lane.arenaWorld().getEntity(lane.activeMonsters().getLast().minecraftEntityId());
        proxy.setNoAi(true);
        return proxy;
    }

    private static GridPosition position(GameTestHelper context) {
        return GridPosition.from(context.absolutePos(new BlockPos(2, 2, 3)));
    }

    private static SemionTowerEntity entity(PlayerLane lane, EntityBackedTower tower) {
        var entity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        return entity;
    }

    private static SemionMonsterEntity monster(PlayerLane lane, Vec3 position, double health) {
        Monster monster = new Monster("job_augment_target_" + UUID.randomUUID(), TeamId.RED, 1,
                Optional.empty(), Optional.empty(), health, 0, 0, AttackKind.MELEE, "minecraft:zombie", null,
                DamageType.PHYSICAL, 0, SummonTier.T1, List.of(SummonRole.RUSH), 0);
        var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        lane.arenaWorld().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static AugmentSnapshot snapshot(String... cards) {
        List<PlayerAugmentState.Selection> selections = new ArrayList<>();
        for (String card : cards) selections.add(new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()));
        return new AugmentSnapshot(AugmentConfig.defaults(), selections);
    }

    private static PlayerLane lane(GameTestHelper context) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(spawn.add(4, 0, 0)), spawn.add(4, 0, 4),
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(7, 6, 7))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(6, 2, 6)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static void cleanup(PlayerLane lane) {
        for (Monster monster : lane.activeMonsters()) {
            if (monster.hasMinecraftEntity() && lane.arenaWorld().getEntity(monster.minecraftEntityId()) != null) {
                lane.arenaWorld().getEntity(monster.minecraftEntityId()).discard();
            }
        }
        lane.activeMonsters().clear();
        for (Tower tower : List.copyOf(lane.towers())) lane.removeTower(tower);
        AreaEffectLaneIndex.unregister(lane);
        HeroPartyStates.clear(lane.ownerPlayer());
        AdversaryProgressStates.clear(lane.ownerPlayer());
    }

    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
    }
}
