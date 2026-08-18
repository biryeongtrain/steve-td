package kim.biryeong.semiontd.tower.demonlord;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * What a demon lord can spend level-up points on.
 *
 * <p>Levels alone already raise health and damage; these let the player decide <i>which</i> way the
 * run bends. Everything here reads its numbers from {@code demon_lord_global} so a server can retune
 * the curve without a rebuild.
 *
 * <p>Two of them are deliberately not linear. Cooldown and defence would trivialise the builder if
 * a big enough pile of points could reach zero, so they approach a floor instead of crossing it -
 * see {@link DemonLordState#cooldownMultiplier()} and {@link DemonLordState#damageReduction()}.
 */
public enum DemonLordStat {
    MAX_HEALTH("max_health", "체력", Items.GOLDEN_APPLE, "최대 체력이 늘어납니다."),
    ATTACK("attack", "공격력", Items.IRON_SWORD, "마검 평타와 모든 스킬 피해가 함께 늘어납니다."),
    DEFENSE("defense", "방어력", Items.IRON_CHESTPLATE, "받는 피해가 줄어듭니다. 100%에는 닿지 않습니다."),
    COOLDOWN("cooldown", "재사용 대기시간 감소", Items.CLOCK, "스킬 쿨타임이 줄어듭니다. 0에는 닿지 않습니다."),
    SKILL_RANGE("skill_range", "스킬 범위", Items.SPYGLASS, "스킬의 사거리와 반경이 넓어집니다."),
    MOVE_SPEED("move_speed", "이동 속도", Items.SUGAR, "레인에서 더 빨리 움직입니다.");

    private final String key;
    private final String displayName;
    private final Item icon;
    private final String description;

    DemonLordStat(String key, String displayName, Item icon, String description) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Item icon() {
        return icon;
    }

    public String description() {
        return description;
    }

    public static DemonLordStat fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (DemonLordStat stat : values()) {
            if (stat.key.equals(normalized)) {
                return stat;
            }
        }
        return null;
    }
}
