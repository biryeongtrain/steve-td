package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.tower.TowerFaction;
import net.minecraft.network.chat.Component;

public final class UndeadNecromancerJob extends FactionTowerJob {
    public UndeadNecromancerJob() {
        super(
                "undead_necromancer",
                "언데드 강령술사",
                TowerFaction.UNDEAD,
                List.of(
                        Component.literal("언데드 타워만 사용할 수 있습니다."),
                        Component.literal("Decay 스택과 죽음 폭발로 몹팩을 연쇄 정리합니다.")
                )
        );
    }
}
