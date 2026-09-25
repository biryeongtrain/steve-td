package kim.biryeong.semiontd.tower.illager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import org.junit.jupiter.api.Test;

class IllagerMarkTest {
    @Test
    void transferredNativeAndOmenMarksRegainFullDurationAndKeepForcedTargetingWithoutStacking() {
        UUID owner = UUID.randomUUID();
        Monster corpse = monster();
        Monster target = monster();
        GridPosition center = new GridPosition(1, 64, 1);
        IllagerMarks.apply(corpse, owner, .3, 200, center, 4);
        IllagerMarks.applyOmen(corpse, owner, .2, 80);
        for (int tick = 0; tick < 60; tick++) {corpse.tickSurvivalScaling(null, 0);}
        for (int tick = 0; tick < 10; tick++) {target.tickSurvivalScaling(null, 0);}
        assertEquals(200, IllagerMarks.transfer(corpse, target, owner));
        IllagerMark mark = IllagerMarks.activeMark(target, owner).orElseThrow();
        assertEquals(target.activeTicks() + 200, mark.expiresAtMonsterTick());
        assertEquals(200, mark.durationTicks());
        assertTrue(mark.forcesTargetFor(center));
        for (int tick = 0; tick < 60; tick++) {target.tickSurvivalScaling(null, 0);}
        assertEquals(.2, IllagerMarks.omenBonus(target, owner));
        IllagerMarks.transfer(corpse, target, owner);
        assertEquals(.2, IllagerMarks.omenBonus(target, owner));
        assertEquals(.3, IllagerMarks.activeMark(target, owner).orElseThrow().damageTakenBonus());
        assertEquals(target.activeTicks() + 200,
                IllagerMarks.activeMark(target, owner).orElseThrow().expiresAtMonsterTick());
    }

    @Test
    void expiredMarksCannotTransfer() {
        UUID owner = UUID.randomUUID();
        Monster corpse = monster();
        IllagerMarks.apply(corpse, owner, .3, 2, null, 0);
        IllagerMarks.applyOmen(corpse, owner, .2, 2);
        for (int tick = 0; tick < 3; tick++) {corpse.tickSurvivalScaling(null, 0);}
        assertFalse(IllagerMarks.hasTransferableMark(corpse, owner));
        assertEquals(0, IllagerMarks.transfer(corpse, monster(), owner));
    }
    @Test
    void omenRefreshesWithoutStackingAndKeepsOtherOwnersAndCaptainMark() {
        Monster monster = monster();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        IllagerMarks.apply(monster, owner, .3, 200, new GridPosition(0, 64, 0), 4);
        IllagerMarks.applyOmen(monster, owner, .2, 80);
        IllagerMarks.applyOmen(monster, other, .4, 80);
        assertEquals(.2, IllagerMarks.omenBonus(monster, owner));
        for (int i = 0; i < 40; i++) {monster.tickSurvivalScaling(null, 0);}
        IllagerMarks.applyOmen(monster, owner, .2, 80);
        for (int i = 0; i < 40; i++) {monster.tickSurvivalScaling(null, 0);}
        assertEquals(.2, IllagerMarks.omenBonus(monster, owner));
        assertEquals(0, IllagerMarks.omenBonus(monster, other));
        assertEquals(.3, IllagerMarks.activeMark(monster, owner).orElseThrow().damageTakenBonus());
        assertTrue(IllagerMarks.activeMark(monster, owner).orElseThrow().forcesTargetFor(new GridPosition(0, 64, 1)));
        for (int i = 0; i < 40; i++) {monster.tickSurvivalScaling(null, 0);}
        assertEquals(0, IllagerMarks.omenBonus(monster, owner));
    }
    @Test
    void markIsActiveForOwnerAndExpiresByMonsterTick() {
        UUID owner = UUID.randomUUID();
        Monster monster = monster();

        IllagerMarks.apply(monster, owner, 0.2, 2, new GridPosition(1, 64, 1), 1.0);

        Optional<IllagerMark> mark = IllagerMarks.activeMark(monster, owner);
        assertTrue(mark.isPresent());
        assertEquals(0.2, mark.get().damageTakenBonus(), 0.0001);
        assertTrue(mark.get().forcesTargetFor(new GridPosition(1, 64, 2)));
        assertFalse(IllagerMarks.activeMark(monster, UUID.randomUUID()).isPresent());

        monster.tickSurvivalScaling(null, 0);
        monster.tickSurvivalScaling(null, 0);
        monster.tickSurvivalScaling(null, 0);

        assertFalse(IllagerMarks.activeMark(monster, owner).isPresent());
    }

    private static Monster monster() {
        return new Monster(
                "test",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                100,
                0,
                1,
                AttackKind.MELEE,
                "minecraft:zombie",
                1
        );
    }
}
