package kim.biryeong.semiontd.tower.blueprint;



import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.blueprint.Blueprint;
import kim.biryeong.semiontd.tower.blueprint.BlueprintModule;
import kim.biryeong.semiontd.tower.blueprint.BlueprintPricing;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStats;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTargetPriority;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTower;
import kim.biryeong.semiontd.tower.blueprint.BlueprintVisuals;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class BlueprintTowerModuleTest {
    /**
     * 설계도 모듈: 대상 우선도(낮은 체력), 다중 사격(가까운 적 둘에게 60%), 맞은 적 둔화·취약, 방호(받는 피해 감소),
     * 처형(체력 낮은 적 피해 증가)이 실제 전투 훅에서 돌고, 모듈을 붙이면 값이 오릅니다.
     */
    @GameTest
    public void modulesApplyThroughTheCombatHooks(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-modules".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats plain = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL);
            BlueprintStats armed = plain.withModules(Map.of(
                    BlueprintModule.MULTISHOT, 2,
                    BlueprintModule.SLOW, 1,
                    BlueprintModule.VULNERABILITY, 1,
                    BlueprintModule.ARMOR, 3
            ), BlueprintTargetPriority.WEAKEST);
            require(BlueprintPricing.price(armed) > BlueprintPricing.price(plain), "Modules must raise the price.");
            require(BlueprintPricing.validate(armed.withModules(Map.of(BlueprintModule.CRIT, 4), BlueprintTargetPriority.FIRST)).isPresent(),
                    "A module level above the cap must be rejected.");

            String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
            var creation = BlueprintStates.create(owner, "시험", armed, visual);
            require(creation.success(), "Blueprint creation must succeed: " + creation.message());
            Blueprint blueprint = creation.blueprint();
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(blueprint.towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 3, 1, 3));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());

            Monster primary = spawnMonster(context, lane, "bp-primary", position(context, 4, 1, 3));
            Monster wounded = spawnMonster(context, lane, "bp-wounded", position(context, 4, 1, 4));
            Monster third = spawnMonster(context, lane, "bp-third", position(context, 3, 1, 5));
            wounded.damage(400.0, DamageType.TRUE);
            SemionMonsterEntity primaryEntity = entity(context, primary);
            SemionMonsterEntity woundedEntity = entity(context, wounded);
            SemionMonsterEntity thirdEntity = entity(context, third);

            require(tower.selectAttackTarget(towerEntity, List.of(primaryEntity, woundedEntity, thirdEntity))
                            .orElse(null) == woundedEntity,
                    "The weakest-first priority must pick the most wounded enemy.");

            double woundedBefore = wounded.health();
            double thirdBefore = third.health();
            tower.onAttackResolved(towerEntity, primaryEntity, 50.0, 50.0, 50.0, false);
            require(wounded.health() < woundedBefore && third.health() < thirdBefore,
                    "Multishot 2 must also hit the two other enemies in range.");
            require(primaryEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 0.0,
                    "Slow must land on the struck enemy.");
            require(primaryEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0.0,
                    "Vulnerability must land on the struck enemy.");

            double expectedArmor = 1.0 - BlueprintModule.ARMOR.value("reduction", 3);
            require(Math.abs(tower.modifyIncomingDamage(towerEntity, null, 100.0) - 100.0 * expectedArmor) < 1.0e-6,
                    "Armor 3 must cut incoming damage by its reduction.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /** 처형: 체력이 문턱 아래인 적에게만 피해가 늘어납니다. */
    @GameTest
    public void executeOnlyBoostsDamageAgainstLowHealthTargets(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-execute".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.EXECUTE, 1), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "처형", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 3, 1, 3));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());
            Monster healthy = spawnMonster(context, lane, "bp-healthy", position(context, 4, 1, 3));
            Monster dying = spawnMonster(context, lane, "bp-dying", position(context, 4, 1, 4));
            dying.damage(850.0, DamageType.TRUE);

            double bonus = 1.0 + BlueprintModule.EXECUTE.value("damageBonus", 1);
            require(Math.abs(tower.modifyOutgoingDamage(towerEntity, entity(context, healthy), 100.0) - 100.0) < 1.0e-6,
                    "A healthy enemy must take normal damage.");
            require(Math.abs(tower.modifyOutgoingDamage(towerEntity, entity(context, dying), 100.0) - 100.0 * bonus) < 1.0e-6,
                    "An enemy below the threshold must take execute damage.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /**
     * 모듈 겹치기: 다중 사격 화살도 광역이 터지고, 처치 폭발은 죽은 적 자리에서 주변 적을 칩니다.
     * C는 첫 대상 A의 광역 반경 밖이고 다중 사격 대상도 아니지만, 다중 사격으로 맞은 B의 광역에 맞아야 합니다.
     */
    @GameTest
    public void multishotArrowsSplashAndKillsExplode(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-combo".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL).withModules(Map.of(
                    BlueprintModule.MULTISHOT, 1,
                    BlueprintModule.SPLASH, 1,
                    BlueprintModule.KILL_EXPLOSION, 1
            ), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "연계", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 1, 1, 1));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());

            Monster a = spawnMonster(context, lane, "combo-a", position(context, 4, 1, 3));
            Monster b = spawnMonster(context, lane, "combo-b", position(context, 4, 1, 5));
            Monster c = spawnMonster(context, lane, "combo-c", position(context, 5, 1, 6));
            double cBefore = c.health();
            tower.onAttackResolved(towerEntity, entity(context, a), 50.0, 50.0, 50.0, false);
            require(b.health() < 1_000.0, "Multishot must hit the nearest other enemy.");
            require(c.health() < cBefore, "The multishot arrow must splash onto the enemy next to its target.");

            Monster corpse = spawnMonster(context, lane, "combo-corpse", position(context, 2, 1, 6));
            Monster bystander = spawnMonster(context, lane, "combo-bystander", position(context, 2, 1, 7));
            double bystanderBefore = bystander.health();
            tower.onKill(towerEntity, entity(context, corpse), 100.0);
            require(bystander.health() < bystanderBefore, "A killed enemy must explode onto the enemy beside it.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /**
     * 직선 관통은 타워-대상 방향 뒤쪽 적까지 맞히고, 한 플레이어의 둔화는 설계도가 달라도 같은 적에게 겹쳐 쌓이지 않습니다.
     */
    @GameTest
    public void lineHitsEnemiesBehindTheTargetAndOwnDebuffsDoNotStack(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-line".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
            BlueprintStats lineStats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.LINE, 1, BlueprintModule.SLOW, 1), BlueprintTargetPriority.FIRST);
            var first = BlueprintStates.create(owner, "직선", lineStats, visual);
            var second = BlueprintStates.create(owner, "둔화", new BlueprintStats(200.0, 20.0, 20, 6.0, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.SLOW, 1), BlueprintTargetPriority.FIRST), visual);
            require(first.success() && second.success(), "Both blueprints must be created.");
            BlueprintTower lineTower = (BlueprintTower) ProductionTowerCatalog.find(first.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 1, 1, 3));
            BlueprintTower slowTower = (BlueprintTower) ProductionTowerCatalog.find(second.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 1, 1, 5));
            lane.addTower(lineTower);
            lane.addTower(slowTower);
            SemionTowerEntity lineEntity = (SemionTowerEntity) context.getLevel().getEntity(lineTower.entityId().getAsInt());
            SemionTowerEntity slowEntity = (SemionTowerEntity) context.getLevel().getEntity(slowTower.entityId().getAsInt());

            Monster front = spawnMonster(context, lane, "line-front", position(context, 3, 1, 3));
            Monster behind = spawnMonster(context, lane, "line-behind", position(context, 5, 1, 3));
            Monster aside = spawnMonster(context, lane, "line-aside", position(context, 3, 1, 6));
            double behindBefore = behind.health();
            double asideBefore = aside.health();
            lineTower.onAttackResolved(lineEntity, entity(context, front), 50.0, 50.0, 50.0, false);
            require(behind.health() < behindBefore, "The line must hit the enemy behind the target.");
            require(aside.health() == asideBefore, "An enemy off the line must not be hit.");

            double oneSlow = entity(context, front).activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION);
            slowTower.onAttackResolved(slowEntity, entity(context, front), 20.0, 20.0, 20.0, false);
            double afterSecond = entity(context, front).activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION);
            require(oneSlow > 0.0 && Math.abs(afterSecond - oneSlow) < 1.0e-9,
                    "One player's slows must not stack across their blueprints: " + oneSlow + " -> " + afterSecond);
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /**
     * 새 모듈 묶음: 집중 사격은 같은 적을 이어 때릴수록 피해가, 광란은 공격할수록 공격 속도가 오르고, 도발은 어그로를,
     * 보스 사냥은 보스에게 피해를 올리며, 넉백은 적을 레인 뒤로 밀고, 소환은 하수인을 불렀다가 라운드가 끝나면 치웁니다.
     */
    @GameTest
    public void focusFrenzyTauntBossKnockbackAndSummonWork(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-more".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL).withModules(Map.of(
                    BlueprintModule.FOCUS, 1,
                    BlueprintModule.FRENZY, 1,
                    BlueprintModule.TAUNT, 1,
                    BlueprintModule.BOSS_SLAYER, 1
            ), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "사냥꾼", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 1, 1, 1));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());
            require(tower.aggroPriority() > 25, "Taunt must raise the aggro priority.");

            Monster target = spawnMonster(context, lane, "more-target", position(context, 5, 1, 5));
            SemionMonsterEntity targetEntity = entity(context, target);
            double first = tower.modifyAttackDamage(towerEntity, targetEntity, 100.0);
            int intervalBefore = tower.adjustAttackInterval(20);
            tower.onAttackResolved(towerEntity, targetEntity, 1.0, 1.0, 1.0, false);
            tower.onAttackResolved(towerEntity, targetEntity, 1.0, 1.0, 1.0, false);
            tower.onAttackResolved(towerEntity, targetEntity, 1.0, 1.0, 1.0, false);
            require(tower.modifyAttackDamage(towerEntity, targetEntity, 100.0) > first,
                    "Focus must raise damage after hitting the same enemy again.");
            require(tower.adjustAttackInterval(20) < intervalBefore, "Frenzy must shorten the attack interval as hits pile up.");

            Monster boss = spawnMonster(context, lane, "boss_test", position(context, 5, 1, 6));
            double normal = tower.modifyOutgoingDamage(towerEntity, targetEntity, 100.0);
            double versusBoss = tower.modifyOutgoingDamage(towerEntity, entity(context, boss), 100.0);
            require(versusBoss > normal, "Boss slayer must add damage against a boss.");

            var layout = lane.laneLayout();
            double progressBefore = layout.progressAt(targetEntity.position());
            tower.knockBack(towerEntity, targetEntity);
            require(layout.progressAt(targetEntity.position()) < progressBefore, "Knockback must push the enemy back along the lane.");

            BlueprintStats summonStats = new BlueprintStats(300.0, 20.0, 20, 6.0, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.SUMMON, 1), BlueprintTargetPriority.FIRST);
            var summoner = BlueprintStates.create(owner, "소환사", summonStats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(summoner.success(), summoner.message());
            BlueprintTower summonTower = (BlueprintTower) ProductionTowerCatalog.find(summoner.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 2, 1, 2));
            lane.addTower(summonTower);
            for (int tick = 0; tick < 240; tick++) {
                summonTower.tick(lane);
            }
            require(summonTower.runtimeDetailLines().contains("하수인 1기"), "Summon must call a minion: " + summonTower.runtimeDetailLines());
            List<SemionTowerEntity> minions = context.getLevel().getEntitiesOfClass(SemionTowerEntity.class,
                    towerEntity.getBoundingBox().inflate(20), candidate -> candidate.runtimeTower() != null
                            && candidate.runtimeTower().isTemporaryCopy()
                            && owner.equals(candidate.runtimeTower().ownerPlayer()));
            require(minions.size() == 1, "Summon must create exactly one owned temporary entity.");
            summonTower.resetForRound(lane);
            require(summonTower.runtimeDetailLines().contains("하수인 0기"), "Minions must be dismissed when the round resets.");
            require(minions.getFirst().isRemoved(), "Round reset must discard the actual minion entity.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /** The world clock must expire minions even while their summoner cannot execute abilities. */
    @GameTest(maxTicks = 320)
    public void reviewMinionLifetimeContinuesAfterSummonerDeath(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("review-blueprint-dead-summoner".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        Runnable cleanup = () -> {
            SuccubusDreams.clearPlayer(owner);
            BlueprintStates.clear(owner);
            group.closeRuntime();
        };
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300, 0, 20, 6, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.SUMMON, 3), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "수명 검수", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 2, 1, 2));
            lane.addTower(tower);
            BlueprintTower sleeper = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 5, 1, 2));
            lane.addTower(sleeper);
            for (int tick = 0; tick < 240; tick++) {
                tower.tick(lane);
                sleeper.tick(lane);
            }
            require(tower.runtimeDetailLines().contains("하수인 1기"), "Probe must spawn a minion first.");
            require(sleeper.runtimeDetailLines().contains("하수인 1기"), "Sleeping source must start with a minion.");
            require(SuccubusDreams.add(sleeper, lane, tower, 100), "Apply sleep through the actual dream path.");
            require(SuccubusDreams.isAsleep(sleeper), "Source must be sleeping.");
            tower.syncHealth(0);
            tower.notifyDeath(lane);
            long spawnedAt = context.getLevel().getGameTime();
            context.onEachTick(() -> {
                try {
                    long elapsed = context.getLevel().getGameTime() - spawnedAt;
                    tower.tick(lane);
                    sleeper.tick(lane);
                    if (elapsed < 300) {
                        require(tower.runtimeDetailLines().contains("하수인 1기"), "Death must neither dismiss nor renew a minion.");
                        require(sleeper.runtimeDetailLines().contains("하수인 1기"), "Sleep must prevent new summons without dismissing the old one.");
                        return;
                    }
                    require(tower.runtimeDetailLines().contains("하수인 0기"), "Dead source minion must expire at 300 ticks.");
                    require(sleeper.runtimeDetailLines().contains("하수인 0기"), "Sleeping source minion must expire at 300 ticks.");
                    tower.syncHealth(tower.currentMaxHealth());
                    for (int tick = 0; tick < 240; tick++) tower.tick(lane);
                    require(tower.runtimeDetailLines().contains("하수인 1기"), "Revived source may summon again.");
                    tower.onRemoved(lane);
                    require(tower.runtimeDetailLines().contains("하수인 0기"), "Permanent removal must dismiss living minions.");
                    cleanup.run();
                    context.succeed();
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    context.fail(net.minecraft.network.chat.Component.literal(failure.toString()));
                }
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest
    public void supportAurasRefreshWithoutStackingAndExpireWithoutTheirSource(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-aura-refresh".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats plain = new BlueprintStats(300, 0, 20, 6, 25, DamageType.PHYSICAL);
            String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
            var support = BlueprintStates.create(owner, "오라", plain.withModules(
                    Map.of(BlueprintModule.HASTE_AURA, 2, BlueprintModule.RANGE_AURA, 2),
                    BlueprintTargetPriority.FIRST), visual);
            var recipient = BlueprintStates.create(owner, "대상", plain, visual);
            BlueprintTower first = (BlueprintTower) ProductionTowerCatalog.find(support.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 2, 1, 2));
            BlueprintTower second = (BlueprintTower) ProductionTowerCatalog.find(support.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 4, 1, 2));
            BlueprintTower target = (BlueprintTower) ProductionTowerCatalog.find(recipient.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 3, 1, 2));
            lane.addTower(first);
            lane.addTower(second);
            lane.addTower(target);
            SemionTowerEntity entity = (SemionTowerEntity) context.getLevel().getEntity(target.entityId().getAsInt());
            entity.setNoAi(true);
            for (int tick = 0; tick < 200; tick++) {
                entity.aiStep();
                first.tick(lane);
                second.tick(lane);
                require(Math.abs(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS)
                                - BlueprintModule.HASTE_AURA.value("attackSpeedBonus", 2)) < 1.0e-6,
                        "Haste aura must remain active without stacking at tick " + tick);
                require(Math.abs(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_FLAT_RANGE_BONUS)
                                - BlueprintModule.RANGE_AURA.value("rangeBonus", 2)) < 1.0e-6,
                        "Range aura must remain active without stacking at tick " + tick);
            }
            for (int tick = 0; tick < 81; tick++) entity.aiStep();
            require(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS) == 0,
                    "Haste must expire when the source stops refreshing it.");
            require(entity.activeTimedEffectMagnitude(TimedEffectType.TOWER_FLAT_RANGE_BONUS) == 0,
                    "Range must expire when the source stops refreshing it.");
            context.succeed();
        } catch (RuntimeException | Error failure) {
            System.out.println("BLUEPRINT_AURA_REGRESSION " + failure);
            throw failure;
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    @GameTest
    public void multishotMatchesStableNearestSelectionAtTheCap(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-stable-cap".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            for (int level : new int[] {1, 3}) {
                BlueprintStats stats = new BlueprintStats(300, 50, 20, 6, 25, DamageType.PHYSICAL)
                        .withModules(Map.of(BlueprintModule.MULTISHOT, level), BlueprintTargetPriority.FIRST);
                var created = BlueprintStates.create(owner, "Stable cap " + level, stats,
                        BlueprintVisuals.options().getFirst().sourceTowerId());
                require(created.success(), created.message());
                BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(created.blueprint().towerId())
                        .orElseThrow().create(owner, TeamId.RED, 1, position(context, 2, 1, 3));
                lane.addTower(tower);
                SemionTowerEntity source = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
                Monster primary = spawnMonster(context, lane, "stable-primary", position(context, 3, 1, 3));
                SemionMonsterEntity target = entity(context, primary);
                java.util.ArrayList<SemionMonsterEntity> spawned = new java.util.ArrayList<>();
                for (int index = 0; index < 6; index++) {
                    spawned.add(entity(context, spawnMonster(context, lane, "stable-extra-" + index,
                            position(context, 4 + index / 2, 1, 3))));
                }
                double range = source.attackRange();
                List<SemionMonsterEntity> candidates = source.level().getEntitiesOfClass(SemionMonsterEntity.class,
                        source.targetSearchBox(), monster -> monster.isAlive() && monster != target && !monster.isDominated()
                                && monster.runtimeMonster() != null && source.defendsLane(monster.runtimeMonster().targetLaneId())
                                && source.distanceToSqr(monster) <= range * range);
                List<SemionMonsterEntity> expected = candidates.stream()
                        .sorted(java.util.Comparator.comparingDouble(target::distanceToSqr))
                        .limit((int) BlueprintModule.MULTISHOT.value("extraTargets", level)).toList();
                require(expected.size() > 0 && expected.size() < candidates.size(), "The fixture must exercise truncation.");
                tower.onAttackResolved(source, target, 50, 50, 50, false);
                double damage = 50 * BlueprintModule.MULTISHOT.value("damageRatio", level);
                for (SemionMonsterEntity monster : spawned) {
                    double expectedHealth = 1000 - (expected.contains(monster) ? damage : 0);
                    require(Math.abs(monster.runtimeMonster().health() - expectedHealth) < 1.0e-9,
                            "Multishot must preserve the exact nearest membership and encounter tie order.");
                    monster.discard();
                    lane.activeMonsters().remove(monster.runtimeMonster());
                }
                target.discard();
                lane.activeMonsters().remove(primary);
                lane.removeTower(tower);
            }
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    @GameTest
    public void cappedAreaSelectionKeepsUuidOrderingAndCallbackSnapshot(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-area-cap".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            var created = BlueprintStates.create(owner, "Area cap", new BlueprintStats(300, 50, 20, 6, 25, DamageType.PHYSICAL),
                    BlueprintVisuals.options().getFirst().sourceTowerId());
            require(created.success(), created.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(created.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 2, 1, 3));
            lane.addTower(tower);
            SemionTowerEntity source = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
            java.util.Set<SemionMonsterEntity> spawned = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (int index = 0; index < 6; index++) {
                spawned.add(entity(context, spawnMonster(context, lane, "area-cap-" + index,
                        position(context, 3 + index / 2, 1, 3))));
            }
            var request = kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest.aroundTower(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("semion-td", "gametest/stable_cap"), source, 6,
                    kim.biryeong.semiontd.api.area.AreaVfxSpec.none()).withFilter(spawned::contains);
            java.util.ArrayList<SemionMonsterEntity> encounter = new java.util.ArrayList<>();
            kim.biryeong.semiontd.api.SemionTdApi.areaEffects().applyToMonsters(request, monster -> {
                encounter.add(monster);
                return kim.biryeong.semiontd.api.area.AreaEffectOutcome.UNCHANGED;
            });
            require(encounter.size() == 6, "All six candidates must be present.");
            var order = java.util.Comparator.comparingDouble((SemionMonsterEntity monster) ->
                    monster.position().distanceToSqr(request.center())).thenComparing(monster -> monster.runtimeMonster().logicalId());
            for (int limit : new int[] {1, 3, 6}) {
                List<SemionMonsterEntity> expected = limit < encounter.size()
                        ? encounter.stream().sorted(order).limit(limit).toList() : List.copyOf(encounter);
                java.util.ArrayList<SemionMonsterEntity> actual = new java.util.ArrayList<>();
                var result = kim.biryeong.semiontd.api.SemionTdApi.areaEffects().applyToMonsters(request.nearestTargets(limit), monster -> {
                    actual.add(monster);
                    return kim.biryeong.semiontd.api.area.AreaEffectOutcome.UNCHANGED;
                });
                require(actual.equals(expected) && result.candidateCount() == limit,
                        "Capped targets must retain distance/UUID order; below the cap must retain encounter order.");
            }
            List<SemionMonsterEntity> expected = encounter.stream().sorted(order).limit(3).toList();
            java.util.ArrayList<SemionMonsterEntity> callbacks = new java.util.ArrayList<>();
            var result = kim.biryeong.semiontd.api.SemionTdApi.areaEffects().applyToMonsters(request.nearestTargets(3), monster -> {
                callbacks.add(monster);
                if (callbacks.size() == 1) expected.getLast().discard();
                return kim.biryeong.semiontd.api.area.AreaEffectOutcome.APPLIED;
            });
            require(callbacks.equals(expected) && result.candidateCount() == 3 && result.appliedCount() == 3,
                    "An earlier callback must not replace or omit targets selected in the original snapshot.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 4, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7))),
                BlockBounds.of(min, max),
                List.of(position(context, 6, 1, 6))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void fillFloor(GameTestHelper context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                context.setBlock(x, 1, z, Blocks.STONE);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
    }

    private static Monster spawnMonster(GameTestHelper context, PlayerLane lane, String id, GridPosition position) {
        Monster monster = new Monster(id, lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                1_000.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return monster;
    }

    private static SemionMonsterEntity entity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
