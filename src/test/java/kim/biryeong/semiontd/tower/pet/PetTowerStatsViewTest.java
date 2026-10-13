package kim.biryeong.semiontd.tower.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PetTowerStatsViewTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBalance() {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
    }

    @Test
    void ownerAndLostCompanionKeepTheirEarlyReturnMessagesAndInheritedLines() {
        var owner = tower(PetTowers.BUTLER_T1);
        var puppy = tower(PetTowers.DOG_T1);
        assertEquals(List.of("마당 범위 1칸"), owner.runtimeDetailLines());
        var inherited = List.of("기존 공통 정보");
        var lines = PetTowerStatsView.create(puppy, inherited);
        assertEquals("기존 공통 정보", lines.getFirst());
        assertTrue(lines.get(1).startsWith("길잃음 주인이 없어 출력 "));
        assertEquals(2, lines.size());
        assertEquals(List.of("기존 공통 정보"), inherited);
    }

    @Test
    void yardAndSpeciesDetailsRefreshAfterOwnershipChanges() {
        var cat = tower(PetTowers.CAT_T1);
        cat.updateYardState(1, 0, true, true, PetTowers.BUTLER_T1);
        var solo = cat.runtimeDetailLines();
        assertTrue(solo.getFirst().startsWith("유대 0.0/"));
        assertTrue(solo.contains("마당 반려 1/8"));
        assertTrue(solo.stream().anyMatch(line -> line.startsWith("독립 활성, 공격력 +")));
        cat.updateYardState(2, 0, false, true, PetTowers.BUTLER_T1);
        var shared = cat.runtimeDetailLines();
        assertTrue(shared.contains("마당 반려 2/8"));
        assertTrue(shared.contains("독립 비활성 (같은 마당에 다른 고양이가 있습니다)"));
        assertEquals(0, cat.bond());
    }

    private static PetTower tower(kim.biryeong.semiontd.tower.TowerType type) {
        return new PetTower(type, new UUID(0, 8102), TeamId.RED, 1, new GridPosition(0, 0, 0));
    }
}
