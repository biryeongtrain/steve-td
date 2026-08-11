package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.end.EndConfig.Ability;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.attackDamageText;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.format;
import static kim.biryeong.semiontd.tower.description.TowerDescriptionTemplate.healthText;
import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;
import static kim.biryeong.semiontd.tower.end.EndFormatting.endText;
import static kim.biryeong.semiontd.tower.end.EndFormatting.warningText;

public final class EndTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            SemionTd.MOD_ID,
            "end_towers"
    );

    public EndTowerJob() {
        super(
                ID,
                Component.literal("엔드 빌더"),
                List.of(
                        SemionText.mini("<gray>타워를 설치해 " + endText("엔더 드래곤") + "을</gray>"),
                        SemionText.mini("<gray>성장시키는 빌더입니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        return List.of(
                SemionText.mini("<gray>아군 타워의 " + healthText("체력") + "과 " + attackDamageText("피해") + "를</gray>"),
                SemionText.mini("<gray>" + seconds() + "에 걸쳐 힘을 받습니다.</gray>"),
                SemionText.mini("<gray>" + healthText("체력 " + percent(ROUND_HEALTH_RATIO)) + ", " + attackDamageText("피해 " + percent(ROUND_DAMAGE_RATIO)) + "를</gray>"),
                SemionText.mini("<gray>해당 라운드 동안 얻고,</gray>"),
                SemionText.mini("<gray>" + healthText("체력 " + percent(PERMANENT_HEALTH_RATIO)) + ", " + attackDamageText("피해 " + percent(PERMANENT_DAMAGE_RATIO)) + "를 영구 누적합니다.</gray>"),
                SemionText.mini("<gray>능력치는 높아질수록 증가 효율이 감소합니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>" + healthText("셜커") + " 계열은 " + healthText("체력") + "을,</gray>"),
                SemionText.mini("<gray>" + attackDamageText("엔드 수정") + " 계열은 " + attackDamageText("피해") + "를 강화합니다.</gray>"),
                Component.empty(),
                SemionText.mini("<gray>" + endText("엔더 드래곤") + "으로 진화하면</gray>"),
                SemionText.mini("<gray>추가 <yellow>고유 능력</yellow>을 획득합니다.</gray>"),
                Component.empty(),
                SemionText.mini(warningText("초보자에게 추천하지 않습니다."))
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        if (!EndTowers.isEndTower(towerType)) {
            return false;
        }
        if (!EndTowers.isBaseEndTower(towerType) || context == null) {
            return true;
        }
        return context.game().playerLane(context.player().uuid()).map(lane -> lane.towers().stream().map(Tower::type).noneMatch(EndTowers::isBaseEndTower)).orElse(true);
    }

    private static String seconds() {
        return format(TowerBalanceRuntime.ability(EndTowers.CONFIG_ID, TRANSFER_TICKS.key()), "seconds");
    }

    private static String percent(Ability ability) {
        return format(TowerBalanceRuntime.ability(EndTowers.CONFIG_ID, ability.key()), "percent");
    }
}
