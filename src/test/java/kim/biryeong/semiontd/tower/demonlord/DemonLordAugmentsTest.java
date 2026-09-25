package kim.biryeong.semiontd.tower.demonlord;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemonLordAugmentsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        kim.biryeong.semiontd.config.TowerBalanceRuntime.apply(kim.biryeong.semiontd.config.TowerBalanceConfig.defaultConfig());
    }

    @Test
    void commonDesignationsApplyToSelfAndKeepGrowthAcrossDeathReconnectButNotNewMatch() {
        UUID owner = UUID.randomUUID();
        DemonLordStates.clearAllForTesting();
        DemonLordState state = DemonLordStates.getOrCreate(owner);
        double baseline = state.maxHealth();
        var snapshot = snapshot("one_man_show", "tactical_designation_3_assault");
        state.syncAugments(snapshot);
        state.enterCombat();
        assertEquals(baseline * 2, state.maxHealth(), 1e-6);
        assertEquals(4, state.augments().damageMultiplier(snapshot, 0), 1e-6);
        snapshot = snapshot("tactical_designation_3_cover");
        state.syncAugments(snapshot);
        state.enterCombat();
        state.applyDamage(100, snapshot, 0, true);
        assertEquals(baseline - 60, state.health(), 1e-6);

        snapshot = snapshot("overheat_core", "battlefield_mastery");
        state.syncAugments(snapshot);
        state.enterCombat();
        state.augments().beginTargeted(snapshot, 5, state.maxHealth());
        state.grantShield(baseline, 1000);
        state.applyDamage(baseline * .5, snapshot, 0, true);
        assertEquals(0, state.augments().targetedProgress().enemyDamage());
        state.clearShield();
        state.applyDamage(baseline * .5, snapshot, 1, true);
        state.settleTargetedAugments(5);
        state.settleTargetedAugments(5);
        assertEquals(1, state.augments().targetedProgress().mastery());
        assertEquals(1, state.augments().targetedProgress().heat());
        assertEquals(baseline * 1.2, state.maxHealth(), 1e-6);

        state.enterCombat();
        state.augments().beginTargeted(snapshot, 6, state.maxHealth());
        state.leaveCombat();
        state.settleTargetedAugments(6);
        assertEquals(1, state.augments().targetedProgress().mastery(), "Death cannot earn mastery");
        assertEquals(2, state.augments().targetedProgress().heat(), "Death cannot erase the heat cost");
        DemonLordStates.clear(owner);
        state = DemonLordStates.getOrCreate(owner);
        state.syncAugments(snapshot);
        state.settleTargetedAugments(6);
        assertEquals(2, state.augments().targetedProgress().heat());
        assertEquals(1, state.augments().targetedProgress().mastery());
        for (int round = 7; round <= 12; round++) {
            state.enterCombat();
            state.augments().beginTargeted(snapshot, round, state.maxHealth());
            state.applyDamage(state.maxHealth() * .5, snapshot, 0, false);
            state.settleTargetedAugments(round);
        }
        assertEquals(5, state.augments().targetedProgress().heat());
        assertEquals(1, state.augments().targetedProgress().mastery(), "Non-enemy damage cannot build mastery");
        assertEquals(.9, state.augments().damageMultiplier(snapshot, 0), 1e-6);
        DemonLordStates.clear(owner);
        DemonLordStates.resetProgression(owner);
        assertEquals(0, DemonLordStates.getOrCreate(owner).augments().targetedProgress().heat());
        DemonLordStates.clearAllForTesting();
    }

    @Test
    void phaseUsesDemonPoolAfterShieldAndOnlyOncePerWave() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        state.enterCombat();
        AugmentSnapshot snapshot = snapshot(DemonLordAugments.PHASE);
        double max = state.maxHealth();
        state.startCooldown(DemonLordSkill.DEMON_BARRIER, 0, 400);
        state.grantShield(max, 1000);
        assertFalse(state.applyDamage(max * 0.8, snapshot, 0));
        assertEquals(max, state.health(), 1.0e-6);
        assertEquals(400, state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, 0));
        state.clearShield();
        assertFalse(state.applyDamage(max * 0.7, snapshot, 1));
        assertEquals(max * 0.8, state.health(), 1.0e-6);
        assertEquals(0, state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, 1));
        assertEquals(1.8, state.augments().damageMultiplier(snapshot, 200), 1.0e-6);
        assertEquals(1.0, state.augments().damageMultiplier(snapshot, 201), 1.0e-6);
        assertTrue(state.applyDamage(max, snapshot, 202));
        state.enterCombat();
        assertFalse(state.applyDamage(max, snapshot, 203));
        assertEquals(max * 0.5, state.health(), 1.0e-6);
    }

    @Test
    void finisherRequiresRealSpellHitExpiresAndConsumesOnce() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        AugmentSnapshot snapshot = snapshot(DemonLordAugments.SILVER);
        assertEquals(0.0, state.augments().consumeFinisher(snapshot, 0));
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 10), 10);
        assertEquals(2.0, state.augments().consumeFinisher(snapshot, 50));
        assertEquals(0.0, state.augments().consumeFinisher(snapshot, 50));
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 100), 100);
        assertEquals(0.0, state.augments().consumeFinisher(snapshot, 141));
        AugmentCombat.runWithoutTriggers(() -> state.augments().recordSpell(
                state, snapshot, spell(DemonLordSkill.SKY_BREAKER, 150), 150));
        assertEquals(0.0, state.augments().consumeFinisher(snapshot, 150));
    }

    @Test
    void comboRequiresThreeDistinctSkillsAndReducesOnlyOffensiveCooldowns() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        AugmentSnapshot snapshot = snapshot(DemonLordAugments.COMBO);
        for (DemonLordSkill skill : DemonLordSkill.values()) {state.startCooldown(skill, 0, 500);}
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 0), 0);
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 5), 5);
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SKY_BREAKER, 10), 10);
        assertEquals(490, state.remainingCooldownTicks(DemonLordSkill.SKY_BREAKER, 10));
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SOUL_DRAIN, 20), 20);
        assertEquals(400, state.remainingCooldownTicks(DemonLordSkill.SKY_BREAKER, 20));
        assertEquals(480, state.remainingCooldownTicks(DemonLordSkill.DEMON_BARRIER, 20));
        assertEquals(1.0, state.augments().damageMultiplier(snapshot, 20));
        state.augments().beginSpell(spell(DemonLordSkill.SKY_BREAKER, 20).altar());
        assertEquals(1.6, state.augments().damageMultiplier(snapshot, 20), 1.0e-6);
        assertEquals(1.0, state.augments().damageMultiplier(snapshot, 140), 1.0e-6);
        state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SOUL_DRAIN, 21), 21);
        assertEquals(399, state.remainingCooldownTicks(DemonLordSkill.SKY_BREAKER, 21));
    }

    @Test
    void thronesRequireTwoRecentDistinctHitsAndResetWithoutLeaking() {
        DemonLordState state = new DemonLordState(UUID.randomUUID());
        AugmentSnapshot snapshot = snapshot(DemonLordAugments.THRONES);
        assertTrue(state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 0), 0).isEmpty());
        assertTrue(state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SKY_BREAKER, 161), 161).isEmpty());
        assertEquals(2, state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.WAVE_OF_MALICE, 162), 162).size());
        assertTrue(state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SOUL_DRAIN, 163), 163).isEmpty());
        state.enterCombat();
        assertTrue(state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SOUL_DRAIN, 164), 164).isEmpty());
        assertEquals(2, state.augments().recordSpell(state, snapshot, spell(DemonLordSkill.SKY_BREAKER, 165), 165).size());
    }

    private static DemonLordAugments.Spell spell(DemonLordSkill skill, long now) {
        DemonLordSkillTower altar = new DemonLordSkillTower(DemonLordTowers.tower(skill, 1), UUID.randomUUID(),
                TeamId.RED, 1, new GridPosition(0, 64, 0));
        return new DemonLordAugments.Spell(altar, now,
                List.of(new DemonLordAugments.Hit(UUID.randomUUID(), 10.0, DamageType.MAGIC)));
    }

    private static AugmentSnapshot snapshot(String... ids) {
        return new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(ids).map(id ->
                new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, id,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList());
    }
}
