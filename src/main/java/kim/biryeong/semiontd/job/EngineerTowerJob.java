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
                List.of(SemionText.mini("<gray>구리 골렘이 <aqua>발판</aqua>을 밟아 <red>레드스톤</red> <yellow>함정</yellow>을 작동시키는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>실제 바닐라 <red>레드스톤</red> 회로로 함정에 <gold>전력</gold>을 공급합니다.</gray>"),
                SemionText.mini("<gray>구리 골렘은 가장 높은 우선순위의 <aqua>발판</aqua>부터 찾아갑니다.</gray>"),
                SemionText.mini("<gray>회로는 슬롯을 쓰지 않지만 <aqua>발판</aqua>과 <yellow>함정</yellow>은 슬롯 하나를 사용합니다.</gray>"),
                SemionText.mini("<gray><red>레드스톤</red>과 중계기 합계는 화면 위 보스바에서 확인합니다.</gray>"),
                SemionText.mini("<red>강제 최종방어에서는 모든 회로와 함정이 정지합니다.</red>")
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
