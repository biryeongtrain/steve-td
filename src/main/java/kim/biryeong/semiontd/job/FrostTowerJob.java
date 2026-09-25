package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.frost.FrostTeamEffects;
import kim.biryeong.semiontd.tower.frost.FrostFullOperationService;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 혹한 빌더의 공개 카탈로그와 고유 냉각장치 설치 제한. */
public final class FrostTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "frost");

    public FrostTowerJob() {
        super(
                ID,
                Component.literal("혹한 빌더"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>보유 수 임계점마다 강해지는 혹한의 전사들을 배치하여 적들을 막아내세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>냉기 장치를 활성화하여 적들을 냉매 상태로 만드세요. 공격하여 처치하거나 해동시켜 최대체력에 비례한 피해를 입히세요.</gray>"),
                        SemionText.mini("<yellow><bold>연계</bold></yellow> <gray>시간이 지날수록 전장을 얼립니다. 본인의 타워를 얼리고 특수 능력을 반복 발동하여 라인을 얼리세요. 모든 라인의 적에게 디버프를 적용해 아군을 도우세요.</gray>"),
                        SemionText.mini("<dark_gray><italic>(절대 그들의 고향이 그저 냉동창고일 뿐이라는 것을 알리지 마세요!)</italic></dark_gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!FrostTowers.isFrostTower(towerType)) {
            return false;
        }
        if (context == null) {
            return true;
        }
        if (FrostTowers.EMISSION_COOLING_DEVICE.id().equals(towerType.id())) {
            return context.game().playerLane(context.player().uuid())
                    .map(lane -> lane.towers().stream()
                            .map(tower -> tower.type())
                            .noneMatch(FrostTowers::isEmissionCoolingDevice))
                    .orElse(true);
        }
        if (FrostTowers.ERUPTION_COOLING_DEVICE.id().equals(towerType.id())) {
            return context.game().playerLane(context.player().uuid())
                    .map(lane -> lane.towers().stream()
                            .map(tower -> tower.type())
                            .noneMatch(FrostTowers::isEruptionCoolingDevice))
                    .orElse(true);
        }
        return true;
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return FrostTowers.isFrostTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
        var team = context.game().teams().get(context.player().teamId());
        if (team != null) {
            FrostTeamEffects.registerTeam(context.player().uuid(), team.laneGroup());
        }
    }

    @Override
    public void onEliminated(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostTeamEffects.unregisterPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        kim.biryeong.semiontd.tower.frost.FrostAugments.clearPlayer(context.player().uuid());
        FrostTeamEffects.unregisterPlayer(context.player().uuid());
        FrostFullOperationService.clearPlayer(context.player().uuid());
    }
}
