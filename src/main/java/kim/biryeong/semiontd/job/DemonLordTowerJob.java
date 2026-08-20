package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The demon lord builder: the player is the defense, the towers are only a skill bar.
 *
 * <p>Round start puts the player into 전투 상태 with a full health pool; killing monsters feeds the
 * level curve, which is this builder's only source of scaling.
 */
public final class DemonLordTowerJob extends SemionJob {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "demon_lord_towers");

    public DemonLordTowerJob() {
        super(
                ID,
                Component.literal("마왕 빌더"),
                List.of(SemionText.mini("<gray>타워 대신 마왕 본인이 레인에서 직접 싸우는 빌더입니다.</gray>"))
        );
    }

    @Override
    public List<Component> description() {
        int statDiamondCost = TowerBalanceRuntime.abilityInt(
                DemonLordTowers.GLOBAL_CONFIG_ID, "statDiamondCost", 50);
        return List.of(
                SemionText.mini("<gray>시작 타워 대신 마왕이 직접 레인을 지킵니다. 같은 스킬 제단은 하나만 지을 수 있고 타워 한도를 2~4칸 차지합니다.</gray>"),
                SemionText.mini("<gray>운영 단계에서는 먼저 지은 7개 스킬이 <aqua>1~5번, F, Q</aqua>에 배정됩니다. 5번은 우클릭, 나머지는 키 입력으로 사용하며 마검은 9번에 고정됩니다. 스킬 10종의 총 코스트는 32입니다.</gray>"),
                SemionText.mini("<green>성장 처치로 레벨과 스탯 포인트를 얻고, 포인트 투자마다 "
                        + statDiamondCost + "다이아를 소모합니다. 체력이 0이 되면 해당 라운드 전투에서 제외됩니다.</green>")
        );
    }

    @Override
    public boolean canUseTower(JobContext context, TowerType towerType) {
        return DemonLordTowers.isDemonLordTower(towerType);
    }

    @Override
    public void onMatchStarted(JobContext context) {
        DemonLordStates.clear(context.player().uuid());
        // 레벨은 한 경기 안에서만 유지됩니다. 새 경기는 여기서만 잊습니다.
        DemonLordStates.resetProgression(context.player().uuid());
        DemonLordStates.getOrCreate(context.player().uuid());
    }

    /**
     * 라운드 시작은 준비 단계입니다. 여기서는 상태만 있는지 확인하고 전투로는 넣지 않습니다.
     *
     * <p>전투 진입은 웨이브가 실제로 시작될 때({@code PlayerLane#markWaveStarted})입니다.
     * 준비 단계에 전투로 들어가면 핫바가 스킬로 덮여 타워를 살 수 없습니다.
     */
    @Override
    public void onRoundStarted(JobContext context, int round) {
        DemonLordStates.getOrCreate(context.player().uuid());
    }

    /**
     * Kills are the builder's entire scaling curve, so experience is weighted by how tanky the
     * victim was rather than being a flat per-kill number.
     */
    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        if (monster == null) {
            return;
        }
        DemonLordState state = DemonLordStates.get(context.player().uuid());
        if (state == null || !state.inCombat()) {
            return;
        }
        double perMaxHealth = TowerBalanceRuntime.ability(
                DemonLordTowers.GLOBAL_CONFIG_ID, "experiencePerMaxHealth", 0.02);
        state.addExperience(Math.max(0.0, monster.maxHealth() * perMaxHealth));
    }

    /**
     * 라운드를 넘기기만 해도 조금씩 자랍니다.
     *
     * <p>처치가 유일한 성장 수단이면 한 번 밀리기 시작한 마왕은 영영 따라잡지 못합니다. 몹을
     * 하나도 못 잡은 라운드에도 기본치를 주어, 뒤처진 판에서도 복구할 여지를 남깁니다.
     * 직접 잡는 편이 여전히 훨씬 빠릅니다.
     */
    @Override
    public void onRoundEnded(JobContext context, int round) {
        DemonLordState state = DemonLordStates.get(context.player().uuid());
        if (state == null) {
            return;
        }
        double passive = TowerBalanceRuntime.ability(
                DemonLordTowers.GLOBAL_CONFIG_ID, "passiveExperiencePerRound", 6.0);
        state.addExperience(Math.max(0.0, passive));
    }

    @Override
    public void onEliminated(JobContext context) {
        DemonLordService.clearPlayerState(context.player().uuid());
    }
}
