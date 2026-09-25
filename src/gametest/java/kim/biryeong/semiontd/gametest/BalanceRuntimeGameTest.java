package kim.biryeong.semiontd.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.balance.manage.*;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.game.*;
import kim.biryeong.semiontd.job.AnimalTowerJob;
import kim.biryeong.semiontd.summon.SummonRegistry;
import kim.biryeong.semiontd.tower.*;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.animal.PigTower;
import kim.biryeong.semiontd.tower.villager.VillagerSplashTower;
import kim.biryeong.semiontd.tower.villager.VillagerThornTower;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitEffects;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.effect.TimedEffectType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class BalanceRuntimeGameTest {
    @GameTest
    public void immediateAbilitiesRefreshExistingTowersWithoutReplayingGrowthOrCooldowns(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context); BalanceChangeService service = scene.service()) {
            PigTower pig = scene.pig();
            scene.pig();
            pig.tick(scene.lane());
            var splash = (VillagerSplashTower) scene.tower(VillagerTowers.T2_LIBRARIAN_TOWER);
            var thorns = (VillagerThornTower) scene.tower(VillagerTowers.T2_GOLEM_TOWER);
            field(splash, "survivalBouns", 2);
            field(thorns, "thornCooldownTicks", 17);
            pig.addPermanentMaxHealthBonus(43, scene.lane());
            pig.syncHealth(23);
            pig.onStateChanged(scene.lane());
            double oldDamage = pig.modifyAttackDamage(scene.entity(pig), null, 10);
            double oldHealth = pig.currentMaxHealth();
            long investment = pig.paidMineralCost();
            long money = scene.game.players().get(scene.red).economy().diamond();
            var original = scene.manager.captureBalanceBundle();
            String pigId = pig.type().id();
            String splashId = splash.type().id();
            double perStack = original.tower().ability(pigId, "damagePerStack", 0);
            double stacks = (oldDamage - 10) / perStack;
            require(stacks > 0, "Fixture has active pack stacks");
            var patch = new BalancePatch(service.currentRevision(), ApplyMode.NOW, "ability refresh", null, List.of(
                    new BalanceChange("tower:/abilities/" + pigId + "/damagePerStack", perStack, perStack + 3),
                    new BalanceChange("tower:/abilities/" + pigId + "/healthPerStack",
                            original.tower().ability(pigId, "healthPerStack", 0), original.tower().ability(pigId, "healthPerStack", 0) + 7),
                    new BalanceChange("tower:/abilities/" + splashId + "/splashRadius", splash.getSplashRange(), 4),
                    new BalanceChange("tower:/abilities/" + splashId + "/bonusPerSurvivedRound",
                            original.tower().ability(splashId, "bonusPerSurvivedRound", 0), .15)));
            var validation = service.validate(patch, "test", "apply");
            service.submit(UUID.randomUUID().toString(), patch, validation.validationHash(), "test", "gametest", "apply");
            close(oldDamage, pig.modifyAttackDamage(scene.entity(pig), null, 10), "No mutation before the server tick");
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            close(oldDamage + stacks * 3, pig.modifyAttackDamage(scene.entity(pig), null, 10), "Next attack reads the new ability");
            close(oldHealth + stacks * 7, pig.currentMaxHealth(), "Ability-dependent maximum health refreshes");
            close(pig.currentMaxHealth(), scene.entity(pig).getMaxHealth(), "Entity and logical maximum health agree");
            close(23, pig.health(), "Ability refresh does not heal");
            close(23, scene.entity(pig).getHealth(), "Entity health stays injured");
            close(4, splash.getSplashRange(), "Non-animal ability refreshes immediately");
            close(130, splash.modifyAttackDamage(scene.entity(splash), null, 100), "Existing survival stacks use the new multiplier");
            require((int) get(splash, "survivalBouns") == 2 && (int) get(thorns, "thornCooldownTicks") == 17,
                    "No new growth or cooldown reset");
            require(investment == pig.paidMineralCost() && money == scene.game.players().get(scene.red).economy().diamond(),
                    "No purchase or economy replay");
            require(splash.type().description().equals(ProductionTowerCatalog.find(splashId).orElseThrow().type().description()),
                    "Existing tower descriptions use the refreshed catalog");
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            require(scene.game.balancePatchEvents().size() == 1, "Repeated tick cannot apply twice");
            context.succeed();
        }
    }

    @GameTest
    public void everyLiveWhitelistedAnimalFactoryRefreshesAllPermittedCoreFields(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            for (String id : BalanceGameRuntime.verifiedLiveTowerIds().stream().sorted().toList()) {
                BlockPos position = context.absolutePos(new BlockPos(2, 2, 2));
                Tower tower = ProductionTowerCatalog.find(id).orElseThrow().create(scene.red, TeamId.RED, 1,
                        new GridPosition(position.getX(), position.getY(), position.getZ()));
                scene.lane().addTower(tower);
                SemionTowerEntity entity = scene.entity((EntityBackedTower) tower);
                entity.setNoAi(true);
                if (tower instanceof kim.biryeong.semiontd.tower.animal.FoxTower) field(tower, "killBonusDamage", 2.5);
                tower.addPermanentMaxHealthBonus(43, scene.lane());
                entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, .25, 200);
                tower.syncHealth(23);
                tower.onStateChanged(scene.lane());
                long paid = tower.paidMineralCost();
                TowerType before = tower.type();
                JsonObject json = scene.manager.captureBalanceBundle().toJson();
                JsonObject stats = json.getAsJsonObject("tower").getAsJsonObject("towers").getAsJsonObject(id);
                stats.addProperty("damage", before.damage() + 3);
                stats.addProperty("range", before.range() + 2);
                stats.addProperty("aggroPriority", before.aggroPriority() + 7);
                BalanceBundle now = BalanceBundle.fromJson(json);
                scene.runtime.apply(now, ApplyMode.NOW, "all-now-" + id, now.revision());
                close(before.damage() + 3, entity.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(), id + " damage");
                close(tower.adjustAttackRange(before.range() + 2), entity.attackRange(), id + " range");
                require(entity.aggroPriority() == before.aggroPriority() + 7, id + " aggro");
                close(23, tower.health(), id + " injured health");
                stats.addProperty("maxHealth", before.maxHealth() + 10);
                stats.addProperty("attackIntervalTicks", before.attackIntervalTicks() + 5);
                stats.addProperty("mineralCost", before.mineralCost() + 99);
                JsonObject costs = json.getAsJsonObject("tower").getAsJsonObject("upgradeCosts");
                for (String edge : costs.keySet()) {
                    if (edge.startsWith(id + "->")) costs.addProperty(edge, costs.get(edge).getAsLong() + 3);
                }
                BalanceBundle prepare = BalanceBundle.fromJson(json);
                scene.runtime.apply(prepare, ApplyMode.NEXT_PREPARE, "all-prepare-" + id, prepare.revision());
                close((before.maxHealth() + 10 + 43) * 1.25, tower.currentMaxHealth(), id + " health growth and buff");
                require(entity.attackIntervalTicks() == Math.max(tower.minimumAttackIntervalTicks(),
                        tower.adjustAttackInterval(before.attackIntervalTicks() + 5)), id + " interval");
                require(tower.paidMineralCost() == paid && tower.type().mineralCost() == before.mineralCost() + 99,
                        id + " purchase price changes without repricing prior investment");
                for (var upgrade : ProductionTowerCatalog.upgrades(tower.type())) {
                    require(upgrade.mineralCost() == costs.get(id + "->" + upgrade.id()).getAsLong(), id + " edge cost");
                }
                if (tower instanceof kim.biryeong.semiontd.tower.animal.FoxTower) close(2.5, (double) get(tower, "killBonusDamage"), id + " earned kill growth");
                close(23, entity.getHealth(), id + " no healing or payout replay");
                scene.lane().removeTower(tower);
            }
            context.succeed();
        }
    }

    @GameTest
    public void immediateStatsKeepStacksHealthInvestmentAndCopies(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            PigTower pig = scene.pig();
            scene.pig();
            Tower copy = scene.pig().markTemporaryCopy(pig.logicalId());
            TowerType frozenType = copy.type();
            pig.tick(scene.lane());
            pig.addPermanentFlatDamageBonus(7, scene.lane());
            pig.addPermanentMaxHealthBonus(43, scene.lane());
            scene.entity(pig).applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, .25, 200);
            double grownHealth = pig.currentMaxHealth();
            pig.syncHealth(23);
            pig.onStateChanged(scene.lane());
            List<String> stacks = pig.runtimeDetailLines();
            long invested = pig.paidMineralCost();
            long money = scene.game.players().get(scene.red).economy().diamond();
            double oldDamage = pig.type().damage();
            BalanceBundle candidate = change(scene.manager.captureBalanceBundle(), AnimalTowers.T1_PIG_TOWER.id(), "damage", oldDamage + 19);
            String oldRevision = scene.runtime.revision();
            scene.runtime.apply(candidate, ApplyMode.NOW, "immediate", candidate.revision());
            close(oldDamage + 19, pig.type().damage(), "Logical tower damage changes");
            close(oldDamage + 19, scene.entity(pig).getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue(), "Entity damage changes in the same task");
            close(23, pig.health(), "Immediate patch cannot heal");
            close(23, scene.entity(pig).getHealth(), "Entity health is retained");
            close(7, pig.permanentFlatDamageBonus(), "Permanent growth survives");
            close(grownHealth, pig.currentMaxHealth(), "Permanent and timed maximum-health growth survives");
            require(stacks.equals(pig.runtimeDetailLines()), "Stack state must survive");
            require(invested == pig.paidMineralCost() && money == scene.game.players().get(scene.red).economy().diamond(), "No repurchase, payout, or reward replay");
            require(copy.type() == frozenType, "Temporary copy retains its captured type");
            BalancePatchEvent event = scene.game.balancePatchEvents().getFirst();
            require(event.requestId().equals("immediate") && event.previousRevision().equals(oldRevision)
                    && event.effectiveRevision().equals(candidate.revision()) && event.round() == 1
                    && event.serverTick() == scene.game.currentTick(), "Patch event captures the exact round and tick");
            field(scene.game, "phase", RoundPhase.ENDED);
            MatchResult result = scene.game.matchResult().orElseThrow();
            Gson gson = new Gson();
            MatchResult restored = gson.fromJson(gson.toJson(result), MatchResult.class);
            require(restored.balancePatchEvents().equals(result.balancePatchEvents())
                    && restored.startBalanceRevision().equals(oldRevision), "Mixed-version metadata survives persistence JSON");
            JsonObject historic = gson.toJsonTree(result).getAsJsonObject();
            historic.remove("balancePatchEvents");
            historic.remove("startBalanceRevision");
            require(gson.fromJson(historic, MatchResult.class).balancePatchEvents().isEmpty(), "Historic result payloads remain readable");
            context.succeed();
        }
    }

    @GameTest
    public void nextPrepareRunsBeforeHealthRestorationAndDoesNotReplay(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context);
             BalanceChangeService service = scene.service()) {
            PigTower pig = scene.pig();
            pig.syncHealth(17);
            pig.onStateChanged(scene.lane());
            double health = pig.type().maxHealth() + 400;
            BalancePatch patch = patch(service, ApplyMode.NEXT_PREPARE, AnimalTowers.T1_PIG_TOWER.id(), "maxHealth", pig.type().maxHealth(), health);
            var validation = service.validate(patch, "test", "apply");
            service.submit(UUID.randomUUID().toString(), patch, validation.validationHash(), "test", "gametest", "apply");
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            close(17, pig.health(), "Scheduling in existing prepare does not trigger restoration");
            require(pig.type().maxHealth() != health, "No early type update");
            field(scene.game, "currentRound", 2);
            field(scene.game, "phase", RoundPhase.ROUND_PAYOUT);
            var prepare = SemionGame.class.getDeclaredMethod("startPreparePhase", net.minecraft.server.MinecraftServer.class);
            prepare.setAccessible(true);
            prepare.invoke(scene.game, context.getLevel().getServer());
            close(health, pig.type().maxHealth(), "Pending health installed on the next boundary");
            close(pig.currentMaxHealth(), pig.health(), "Round restoration sees the new health cap");
            require(scene.game.balancePatchEvents().size() == 1 && scene.game.balancePatchEvents().getFirst().round() == 2,
                    "Exactly one patch recorded at round two");
            service.onBoundary(BalanceChangeService.Boundary.BEFORE_PREPARE);
            require(scene.game.balancePatchEvents().size() == 1, "Repeated boundary does not replay a patch");
            context.succeed();
        }
    }

    @GameTest
    public void waitingLobbyPrepareReservationAppliesBeforeInitialRoster(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            scene.game.close();
            scene.game = scene.newGame();
            field(scene.manager, "activeGame", scene.game);
            try (BalanceChangeService service = scene.service()) {
                require(scene.runtime.view().gameId() == null, "An unlocked lobby has no started match ID");
                BalancePatch patch = patch(service, ApplyMode.NEXT_PREPARE, AnimalTowers.T1_PIG_TOWER.id(), "maxHealth", 80, 90);
                var validation = service.validate(patch, "test", "apply");
                service.submit(UUID.randomUUID().toString(), patch, validation.validationHash(), "test", "gametest", "apply");
                service.onBoundary(BalanceChangeService.Boundary.TICK);
                close(90, TowerBalanceRuntime.current().statsFor(AnimalTowers.T1_PIG_TOWER).maxHealth(),
                        "Waiting lobby applies preparation settings on the next tick without starting a match");
                require(scene.game.start(context.getLevel().getServer(), scene.plan()), "Waiting roster starts");
                close(90, TowerBalanceRuntime.current().statsFor(AnimalTowers.T1_PIG_TOWER).maxHealth(), "Initial start applies pending preparation config");
                require(scene.game.startBalanceRevision().equals(service.currentRevision())
                        && scene.game.balancePatchEvents().isEmpty(), "Initial configuration is captured as start revision");
            }
            context.succeed();
        }
    }

    @GameTest
    public void idleNowHealthPatchAppliesAfterMatchWithoutChangingItsTowersOrHistory(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            PigTower pig = scene.pig();
            TowerType before = pig.type();
            BalanceBundle candidate = change(scene.manager.captureBalanceBundle(), before.id(), "maxHealth", before.maxHealth() + 55);
            boolean rejected = false;
            try {scene.runtime.apply(candidate, ApplyMode.NOW, "running", candidate.revision());}
            catch (IllegalArgumentException expected) {rejected = true;}
            require(rejected && pig.type() == before, "Active match still rejects immediate preparation-only stats");
            field(scene.game, "phase", RoundPhase.ENDED);
            require(scene.runtime.view().gameId() == null && scene.runtime.view().nextMatchSafe(), "Ended match counts as idle");
            scene.runtime.apply(candidate, ApplyMode.NOW, "idle", candidate.revision());
            close(before.maxHealth() + 55, scene.manager.captureBalanceBundle().tower().statsFor(before).maxHealth(),
                    "Immediate preparation-only change applies without a running match");
            require(pig.type() == before && scene.game.balancePatchEvents().isEmpty(),
                    "Completed match tower snapshots and patch history remain unchanged");
            context.succeed();
        }
    }

    @GameTest
    public void nextMatchKeepsSelectionsAndAppliesBeforeRoster(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context);
             BalanceChangeService service = scene.service()) {
            Object selectedWave = get(scene.game, "selectedRoundWave");
            Object augments = get(scene.game, "augmentConfig");
            var seed = scene.game.augmentSeed();
            BalanceBundle before = scene.manager.captureBalanceBundle();
            double starting = before.economy().startingDiamond();
            BalancePatch patch = new BalancePatch(service.currentRevision(), ApplyMode.NEXT_MATCH, "next match", null,
                    List.of(new BalanceChange("economy:/startingDiamond", starting, starting + 37)));
            var validation = service.validate(patch, "test", "apply");
            service.submit(UUID.randomUUID().toString(), patch, validation.validationHash(), "test", "gametest", "apply");
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            service.onBoundary(BalanceChangeService.Boundary.BEFORE_PREPARE);
            require(scene.manager.captureBalanceBundle().revision().equals(before.revision()), "Reservation cannot change any global configuration");
            require(get(scene.game, "selectedRoundWave") == selectedWave && get(scene.game, "augmentConfig") == augments
                    && scene.game.augmentSeed() == seed, "Selected wave and augment seed remain captured");
            scene.game.close();
            SemionGame next = scene.newGame();
            field(scene.manager, "activeGame", next);
            scene.manager.attachManagedBalance(scene.runtime, service::onBoundary);
            require(next.start(context.getLevel().getServer(), scene.plan()), "Next roster starts");
            scene.game = next;
            require(scene.manager.captureBalanceBundle().economy().startingDiamond() == starting + 37,
                    "Global config changes at start boundary");
            require(next.players().get(scene.red).economy().diamond() == starting + 37,
                    "New player economy captures the changed starting value");
            require(next.startBalanceRevision().equals(service.currentRevision()) && next.balancePatchEvents().isEmpty(),
                    "The new revision belongs to match start, not a mid-match patch");
            context.succeed();
        }
    }

    @GameTest
    public void traitAndAugmentPatchKeepsCurrentEffectsAndReachesNextMatch(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context); BalanceChangeService service = scene.service()) {
            BalanceBundle before = scene.manager.captureBalanceBundle();
            var loadout = new TraitLoadout(BuiltInTraits.IGNITE_ID, null);
            double oldIgnite = TraitEffects.igniteDamagePerTick(loadout, 0, 1);
            double oldBonus = before.augment().parameter("wartime_economy", "damageBonus", 0);
            double oldDelay = before.augment().parameter("job_engineer_towers_g2", "delayTicks", 0);
            scene.lane().assignAugmentSnapshot(new AugmentSnapshot(before.augment(), List.of(
                    new PlayerAugmentState.Selection(25, AugmentRarity.PRISMATIC, "semiontd:wartime_economy",
                            PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            PigTower current = scene.pig();
            close(oldBonus, AugmentCombat.damageBonus(current, scene.entity(current)), "Current selected effect");
            BalancePatch patch = new BalancePatch(service.currentRevision(), ApplyMode.NEXT_MATCH, "trait and augment", null,
                    List.of(new BalanceChange("trait:/traits/ignite/flatDamagePerTick", oldIgnite, oldIgnite + 7),
                            new BalanceChange("augment:/parameters/semiontd:wartime_economy/damageBonus", oldBonus, oldBonus + .2),
                            new BalanceChange("augment:/parameters/semiontd:job_engineer_towers_g2/delayTicks", oldDelay, oldDelay + 20)));
            String key = UUID.randomUUID().toString();
            var validation = service.validate(patch, "test", "apply");
            service.submit(key, patch, validation.validationHash(), "test", "gametest", "apply");
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            service.onBoundary(BalanceChangeService.Boundary.BEFORE_PREPARE);
            close(oldIgnite, TraitEffects.igniteDamagePerTick(loadout, 0, 1), "Pending trait cannot affect current match");
            close(oldBonus, AugmentCombat.damageBonus(current, scene.entity(current)), "Pending augment cannot affect selected towers");
            require(service.deployment(key).state() == DeploymentState.SCHEDULED, "Still pending during active match");

            var selections = current.augmentSnapshot().selections();
            scene.game.close();
            scene.game = scene.newGame();
            field(scene.manager, "activeGame", scene.game);
            scene.manager.attachManagedBalance(scene.runtime, service::onBoundary);
            require(scene.game.start(context.getLevel().getServer(), scene.plan()), "Next match starts");
            close(oldIgnite + 7, TraitEffects.igniteDamagePerTick(loadout, 0, 1), "Next-match trait effect consumes patched value");
            close(oldDelay + 20, scene.game.augmentConfig().parameter("job_engineer_towers_g2", "delayTicks", 0),
                    "Job augment delay reaches next-match snapshot");
            scene.lane().assignAugmentSnapshot(new AugmentSnapshot(scene.game.augmentConfig(), selections));
            PigTower next = scene.pig();
            close(oldBonus + .2, AugmentCombat.damageBonus(next, scene.entity(next)), "New tower consumes patched augment damage");
            require(scene.game.startBalanceRevision().equals(service.currentRevision()), "New match records patched revision");
            require(scene.game.balancePatchEvents().isEmpty(), "No mid-match patch event at initial boundary");
            context.succeed();
        }
    }

    @GameTest
    public void failedRefreshRestoresEveryTargetAndGlobalRegistry(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            PigTower first = scene.pig();
            PigTower throwing = new PigTower(first.type(), scene.red, TeamId.RED, 1, first.position()) {
                @Override public void refreshType(TowerType type, PlayerLane lane) {
                    super.refreshType(type, lane);
                    throw new IllegalStateException("injected refresh failure");
                }
            };
            scene.lane().addTower(throwing);
            first.syncHealth(41);
            first.onStateChanged(scene.lane());
            Tower.BalanceSnapshot firstState = first.captureBalanceState();
            Tower.BalanceSnapshot failedState = throwing.captureBalanceState();
            BalanceBundle previous = scene.manager.captureBalanceBundle();
            JsonObject json = change(previous, first.type().id(), "maxHealth", 1).toJson();
            json.getAsJsonObject("tower").getAsJsonObject("abilities").getAsJsonObject(first.type().id())
                    .addProperty("damagePerStack", 99);
            BalanceBundle candidate = BalanceBundle.fromJson(json);
            boolean failed = false;
            try {scene.runtime.apply(candidate, ApplyMode.NEXT_PREPARE, "failure", candidate.revision());}
            catch (IllegalStateException expected) {failed = true;}
            require(failed, "Injected failure must reject the entire patch");
            require(first.captureBalanceState().equals(firstState) && throwing.captureBalanceState().equals(failedState),
                    "All changed tower states restored including unclamped prior health");
            close(41, scene.entity(first).getHealth(), "Entity health restored without family hooks");
            require(scene.manager.captureBalanceBundle().revision().equals(previous.revision())
                    && TowerBalanceRuntime.current().equals(previous.tower()), "All global configs restored");
            require(ProductionTowerCatalog.find(first.type().id()).orElseThrow().type().equals(firstState.type()), "Catalog restored");
            require(scene.game.balancePatchEvents().isEmpty(), "Failed patch has no match event");
            context.succeed();
        }
    }

    @GameTest
    public void practiceSessionsBlockGlobalChangesAndReloadKeepsLegacyBaseline(GameTestHelper context) throws Exception {
        try (Scene scene = new Scene(context)) {
            BalanceBundle original = scene.manager.captureBalanceBundle();
            BalanceBundle changed = change(original, AnimalTowers.T1_PIG_TOWER.id(), "damage", 31);
            @SuppressWarnings("unchecked") Map<UUID, SemionGame> practice = (Map<UUID, SemionGame>) get(scene.manager, "sandboxGames");
            practice.put(UUID.randomUUID(), scene.game);
            try {
                require(!scene.runtime.view().nextMatchSafe(), "Practice session closes server-wide next-match boundary");
                boolean rejected = false;
                try {scene.runtime.apply(changed, ApplyMode.NOW, "practice", changed.revision());}
                catch (IllegalStateException expected) {rejected = true;}
                require(rejected && scene.runtime.revision().equals(original.revision()), "No silent practice combat mutation");
            } finally {practice.clear();}
            scene.runtime.apply(changed, ApplyMode.NOW, "managed", changed.revision());
            scene.runtime.checkManualConfigConflict(original);
            require(scene.runtime.writeBlocked() == null, "Unedited legacy JSON is valid after a managed patch");
            scene.runtime.checkManualConfigConflict(change(original, AnimalTowers.T1_PIG_TOWER.id(), "damage", 32));
            require(scene.runtime.writeBlocked() != null, "Actual manual legacy edit is surfaced");
            SemionGameManager restarted = new SemionGameManager();
            restarted.installBalanceBundle(changed);
            BalanceGameRuntime restartedRuntime = new BalanceGameRuntime(context.getLevel().getServer(), restarted);
            restartedRuntime.configureLegacyBaseline(original.revision(), changed);
            require(restartedRuntime.writeBlocked() != null, "Persisted baseline detects offline legacy edits on restart");
            restartedRuntime.configureLegacyBaseline(original.revision(), original);
            require(restartedRuntime.writeBlocked() == null, "Restoring legacy files clears the external conflict");
            context.succeed();
        }
    }

    private static BalancePatch patch(BalanceChangeService service, ApplyMode mode, String tower, String key, double before, double after) {
        return new BalancePatch(service.currentRevision(), mode, "gametest", null,
                List.of(new BalanceChange("tower:/towers/" + tower + "/" + key, before, after)));
    }

    private static BalanceBundle change(BalanceBundle original, String tower, String key, double value) {
        JsonObject json = original.toJson();
        json.getAsJsonObject("tower").getAsJsonObject("towers").getAsJsonObject(tower).addProperty(key, value);
        return BalanceBundle.fromJson(json);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void close(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .01, message + ": " + actual + " != " + expected);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Scene implements AutoCloseable {
        final GameTestHelper context;
        final SemionGameManager manager = new SemionGameManager();
        final UUID red = UUID.randomUUID();
        final UUID blue = UUID.randomUUID();
        final kim.biryeong.semiontd.config.TowerBalanceConfig previousTower = TowerBalanceRuntime.current();
        final kim.biryeong.semiontd.config.TraitBalanceConfig previousTrait = TraitBalanceRuntime.current();
        final ProductionTowerCatalog.Snapshot previousCatalog = ProductionTowerCatalog.snapshot();
        final java.util.Collection<kim.biryeong.semiontd.summon.SummonMonsterType> previousSummons = SummonRegistry.all();
        final BalanceGameRuntime runtime;
        SemionGame game;

        Scene(GameTestHelper context) throws Exception {
            this.context = context;
            ProductionTowerCatalogs.reloadBuiltIns(manager.captureBalanceBundle().tower());
            runtime = new BalanceGameRuntime(context.getLevel().getServer(), manager);
            game = newGame();
            field(manager, "activeGame", game);
            manager.attachManagedBalance(runtime, ignored -> {});
            require(game.start(context.getLevel().getServer(), plan()), "Synthetic game starts");
        }

        SemionGame newGame() {
            var bundle = manager.captureBalanceBundle();
            SemionGame next = new SemionGame(bundle.economy(), bundle.wave(),
                    SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
            next.selectJob(red, AnimalTowerJob.ID);
            next.selectJob(blue, AnimalTowerJob.ID);
            return next;
        }

        ParticipantSelectionPlan plan() {
            return new ParticipantSelectionPlan(MatchMode.NORMAL, List.of(
                    new AssignedParticipant(red, "balance-red", TeamId.RED, 1),
                    new AssignedParticipant(blue, "balance-blue", TeamId.BLUE, 1)), Set.of(), 2);
        }

        PlayerLane lane() {return game.playerLane(red).orElseThrow();}

        EntityBackedTower tower(TowerType type) {
            BlockPos position = context.absolutePos(new BlockPos(3, 2, 3));
            var tower = (EntityBackedTower) ProductionTowerCatalog.find(type.id()).orElseThrow().create(red, TeamId.RED, 1,
                    new GridPosition(position.getX(), position.getY(), position.getZ()));
            lane().addTower(tower);
            entity(tower).setNoAi(true);
            return tower;
        }

        PigTower pig() {
            BlockPos position = context.absolutePos(new BlockPos(2, 2, 2));
            PigTower tower = new PigTower(ProductionTowerCatalog.find(AnimalTowers.T1_PIG_TOWER.id()).orElseThrow().type(),
                    red, TeamId.RED, 1, new GridPosition(position.getX(), position.getY(), position.getZ()));
            lane().addTower(tower);
            entity(tower).setNoAi(true);
            return tower;
        }

        SemionTowerEntity entity(EntityBackedTower tower) {return tower.runtimeEntity(lane()).orElseThrow();}

        BalanceChangeService service() throws Exception {
            BalanceBundle bundle = manager.captureBalanceBundle();
            BalanceChangeService service = new BalanceChangeService(Files.createTempDirectory("semion-balance-gametest-"),
                    "test", bundle, new BalanceFieldRegistry(BalanceBundle.defaults(), null, BalanceGameRuntime.verifiedLiveTowerIds()), runtime);
            manager.attachManagedBalance(runtime, service::onBoundary);
            service.onBoundary(BalanceChangeService.Boundary.TICK);
            return service;
        }

        @Override public void close() {
            game.close();
            TowerBalanceRuntime.apply(previousTower);
            TraitBalanceRuntime.apply(previousTrait);
            ProductionTowerCatalog.install(previousCatalog);
            SummonRegistry.reload(previousSummons);
        }
    }
}
