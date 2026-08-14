package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroPartyStates;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class HeroPartyTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "hero_party");

    public HeroPartyTowerJob() {
        super(
                ID,
                Component.literal("용사 빌더"),
                List.of(
                        SemionText.mini("<gray>용사를 먼저 설치하고 최대 네 명의 동료로 파티를 완성합니다.</gray>"),
                        SemionText.mini("<gray>준비 시간에 장비를 바꾸고 웨이브 퀘스트로 파티를 강화합니다.</gray>"),
                        SemionText.mini("<yellow>선택한 동료 종류는 해당 경기 동안 변경할 수 없습니다.</yellow>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!HeroPartyTowers.isHeroPartyTower(towerType)) {
            return false;
        }
        if (context == null) {
            return true;
        }
        UUID playerId = context.player().uuid();
        if (HeroPartyTowers.isHero(towerType)) {
            return !HeroPartyStates.hasActiveHero(context.game(), playerId);
        }
        HeroCompanionRole role = HeroPartyTowers.role(towerType).orElse(null);
        if (role == null) {
            return false;
        }
        if (HeroPartyTowers.tier(towerType) > 1) {
            return HeroPartyStates.state(playerId).isCommitted(role);
        }
        if (!HeroPartyStates.hasActiveHero(context.game(), playerId)
                || HeroPartyStates.hasActiveCompanion(context.game(), playerId, role)) {
            return false;
        }
        return HeroPartyStates.state(playerId).canCommit(role);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return HeroPartyTowers.isHeroPartyTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        HeroPartyStates.clear(context.player().uuid());
        HeroPartyStates.state(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        HeroPartyStates.assignQuest(context, round);
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        HeroPartyStates.finishQuest(context);
    }

    @Override
    public void onEliminated(JobContext context) {
        HeroPartyStates.clear(context.player().uuid());
    }
}
