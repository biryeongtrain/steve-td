package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.engineer.EngineerBalance;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EngineerTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "engineer_towers");

    public EngineerTowerJob() {
        super(
                ID,
                Component.literal("기술자"),
                List.of(
                        SemionText.mini("<green><bold>시작</bold></green> <gray>구리 골렘과 발판을 놓고 함정까지 레드스톤으로 연결하세요.</gray>"),
                        SemionText.mini("<aqua><bold>운영</bold></aqua> <gray>골렘이 밟을 발판의 위치와 등급으로 함정 발동 순서를 정하세요.</gray>"),
                        SemionText.mini("<yellow><bold>주의</bold></yellow> <gray>회로가 끊기면 함정이 멈추며 강제 최종 방어에서도 작동하지 않습니다.</gray>")
                )
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!EngineerTowers.isEngineerTower(towerType)) {
            return false;
        }
        if (context != null && EngineerTowers.isDust(towerType)) {
            UUID owner = context.player().uuid();
            long redstone = context.game().playerLane(owner)
                    .map(lane -> lane.towers().stream()
                            .filter(tower -> owner.equals(tower.ownerPlayer()))
                            .filter(tower -> EngineerTowers.isDust(tower.type())
                                    || EngineerTowers.repeaterDirection(tower.type()).isPresent())
                            .count())
                    .orElse(0L);
            if (redstone >= EngineerBalance.maxRedstone()) {
                return false;
            }
        }
        if (context != null && EngineerTowers.PlateKind.WOOD == EngineerTowers.plateKind(towerType).orElse(null)) {
            UUID owner = context.player().uuid();
            long plates = context.game().playerLane(owner)
                    .map(lane -> lane.towers().stream()
                            .filter(tower -> owner.equals(tower.ownerPlayer()))
                            .filter(tower -> EngineerTowers.plateKind(tower.type()).isPresent())
                            .count())
                    .orElse(0L);
            if (plates >= EngineerBalance.maxPlates()) {
                return false;
            }
        }
        if (context != null
                && EngineerTowers.trapKind(towerType).orElse(null) == EngineerTowers.TrapKind.PISTON
                && EngineerTowers.trapTier(towerType) == 1) {
            UUID owner = context.player().uuid();
            long pistons = context.game().playerLane(owner)
                    .map(lane -> lane.towers().stream()
                            .filter(tower -> owner.equals(tower.ownerPlayer()))
                            .filter(tower -> EngineerTowers.trapKind(tower.type()).orElse(null)
                                    == EngineerTowers.TrapKind.PISTON)
                            .count())
                    .orElse(0L);
            if (pistons >= EngineerBalance.maxPistons()) {
                return false;
            }
        }
        if (context == null || !EngineerTowers.isGolem(towerType)) {
            return true;
        }
        UUID owner = context.player().uuid();
        return context.game().playerLane(owner)
                .map(lane -> lane.towers().stream()
                        .noneMatch(tower -> owner.equals(tower.ownerPlayer()) && EngineerTowers.isGolem(tower.type())))
                .orElse(true);
    }

    @Override
    public boolean includesTowerInCatalog(TowerType towerType) {
        return EngineerTowers.isEngineerTower(towerType);
    }
}
