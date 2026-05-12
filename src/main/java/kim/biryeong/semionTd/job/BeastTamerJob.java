package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.tower.TowerFaction;
import net.minecraft.network.chat.Component;

public final class BeastTamerJob extends FactionTowerJob {
    public BeastTamerJob() {
        super(
                "beast_tamer",
                "동물 조련사",
                TowerFaction.BEAST,
                List.of(
                        Component.literal("동물 타워만 사용할 수 있습니다."),
                        Component.literal("Rage 스택으로 교전 중 공격 속도와 압박 대응력이 올라갑니다.")
                )
        );
    }
}
