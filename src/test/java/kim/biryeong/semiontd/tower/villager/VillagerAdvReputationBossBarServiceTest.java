package kim.biryeong.semiontd.tower.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VillagerAdvReputationBossBarServiceTest {
    @Test
    void bossBarShowsReputationProgress() {
        assertEquals(0.255F, VillagerAdvReputationBossBarService.progress(25.5, 100));
        assertEquals("평판 - 25.5/100", VillagerAdvReputationBossBarService.title(25.5, 100).getString());
    }

    @Test
    void bossBarClampsReputationToMaximum() {
        assertEquals(1.0F, VillagerAdvReputationBossBarService.progress(120, 100));
        assertEquals("평판 - 100/100", VillagerAdvReputationBossBarService.title(120, 100).getString());
    }
}
