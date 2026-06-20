package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class IllagerTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_towers");

    public IllagerTowerJob() {
        super(
                ID,
                Component.literal("우민 빌더"),
                List.of(
                        SemionText.mini("<gray>라운드마다 습격 게이지를 모아 우민 타워 전체를 강화하는 빌더입니다.</gray>"),
                        SemionText.mini("<gray>습격은 게이지가 가득 차면 자동으로 발동하며 라운드 종료까지 유지됩니다.</gray>")
                )
        );
    }

    @Override
    public List<Component> description() {
        int gaugeMax = abilityInt("gaugeMax", 100);
        int waveKillGauge = abilityInt("waveKillGauge", 3);
        int incomeKillGauge = abilityInt("incomeKillGauge", 8);
        int markedKillBonusGauge = abilityInt("markedKillBonusGauge", 7);
        int towerDeathGauge = abilityInt("illagerTowerDeathGauge", 20);
        String attackSpeedPerTower = percent(ability("attackSpeedPercentPerTower", 0.02));
        String damagePerTower = percent(ability("damagePercentPerTower", 0.05));
        return List.of(
                SemionText.mini("<gray>라운드마다 습격 게이지가 <yellow>" + gaugeMax + "</yellow>가 될 경우 습격 버프를 활성화합니다.</gray>"),
                SemionText.mini("<gray>웨이브 적 처치 시 <yellow>+" + waveKillGauge + "</yellow>, 인컴/소환 적 처치 시 <yellow>+" + incomeKillGauge + "</yellow>, 표식 적 처치 시 추가 <yellow>+" + markedKillBonusGauge + "</yellow>를 얻습니다.</gray>"),
                SemionText.mini("<gray>내 우민 타워가 사망하면 <yellow>+" + towerDeathGauge + "</yellow>를 얻습니다.</gray>"),
                SemionText.mini("<gray>게이지가 가득 차면 습격이 자동 발동하고 라운드 종료까지 유지됩니다.</gray>"),
                SemionText.mini("<green>습격 중 라운드 시작 시 살아있던 우민 타워 1기당 공격속도 " + attackSpeedPerTower + ", 공격력 " + damagePerTower + " 증가.</green>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return IllagerTowers.isIllagerTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onRoundStarted(JobContext context, int round) {
        IllagerRaidStates.onRoundStarted(context);
    }

    @Override
    public void onRoundEnded(JobContext context, int round) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        IllagerRaidStates.clear(context.player().uuid());
    }

    private static double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(IllagerRaidStates.RAID_CONFIG_ID, key, fallback);
    }

    private static int abilityInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(IllagerRaidStates.RAID_CONFIG_ID, key, fallback);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100.0);
    }
}
