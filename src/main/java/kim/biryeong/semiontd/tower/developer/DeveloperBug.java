package kim.biryeong.semiontd.tower.developer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The 25 defects a patch can leave behind.
 *
 * <p>One rule governs the whole list: <b>no bug is a pure gain or a pure loss</b>. Every entry
 * flips depending on where it landed — which tower, which lane position, which other bugs are
 * already on it. That is what makes "fix it or keep it" a real decision every round instead of a
 * chore, and it is the reason none of these are simple stat penalties.
 *
 * <p>Each bug resolves its numbers through {@link TowerBalanceRuntime} under the
 * {@code developer_global} config id, so the whole table is tunable without a rebuild.
 */
public enum DeveloperBug {

    // ------------------------------------------------------------------ 공격 계산

    /** Twice the reach for half the punch. Great on a bend, terrible on a single choke point. */
    PRIMITIVE("primitive", "원시", Category.DAMAGE, Items.WOODEN_SWORD, 2.0, 0.5,
            List.of("<gray>사거리가 2배가 되고 피해가 절반이 됩니다.</gray>")),

    /** Rewards standing exactly at the edge, which turns placement into the whole puzzle. */
    BOUNDARY("boundary", "경계 조건", Category.DAMAGE, Items.STRING, 0.85, 2.0,
            List.of("<gray>사거리 바깥쪽 15% 구간의 적에게만 피해가 2배가 됩니다.</gray>")),

    /** Same expected value, far more variance. Only matters on towers that hit hard. */
    FLOATING_POINT("floating_point", "부동소수점", Category.DAMAGE, Items.QUARTZ, 0.40, 0.0,
            List.of("<gray>피해가 매번 40%까지 위아래로 흔들립니다. 기댓값은 같습니다.</gray>")),

    /**
     * The family's landmine.
     *
     * <p>Harmless on a weak tower, fatal on one that has been patched or optimised into a big
     * single hit — exactly the tower a player is most proud of.
     */
    INTEGER_OVERFLOW("integer_overflow", "정수 오버플로", Category.DAMAGE, Items.REDSTONE, 3.0, 0.1,
            List.of("<red>피해가 기본값의 3배를 넘으면 최소 피해로 되돌아갑니다.</red>")),

    /** Slower but heavier. Pairs beautifully with 오버킬 and disastrously with 정수 오버플로. */
    TIMEOUT("timeout", "타임아웃", Category.DAMAGE, Items.CLOCK, 0.30, 0.80,
            List.of("<gray>공격 간격이 30% 늘어나고 피해가 80% 증가합니다.</gray>")),

    /** Reads the crowd: excellent into packs, dead weight against stragglers. */
    BUFFER_OVERRUN("buffer_overrun", "버퍼 오버런", Category.DAMAGE, Items.HOPPER, 0.60, 0.40,
            List.of("<gray>사거리 내 적이 5기 이상이면 피해 +60%, 2기 이하면 -40%.</gray>")),

    // ------------------------------------------------------------------ 타겟 선정

    /** Boss insurance that leaks every trash wave. */
    AGGRO_INVERSION("aggro_inversion", "어그로 역전", Category.TARGETING, Items.ENDER_EYE, 0.0, 0.0,
            List.of("<gray>가장 약한 적 대신 <yellow>가장 강한 적</yellow>만 노립니다.</gray>")),

    /** Hits the leader of the pack instead of what is about to reach the end. */
    REVERSE_SORT("reverse_sort", "역방향 정렬", Category.TARGETING, Items.LEAD, 0.0, 0.0,
            List.of("<gray>가장 가까운 적 대신 <yellow>가장 먼 적</yellow>부터 노립니다.</gray>")),

    /** Refuses to switch. Cancels 캐시 미스 entirely, which is the tidiest pair in the list. */
    INFINITE_LOOP("infinite_loop", "무한 루프", Category.TARGETING, Items.CHAIN, 0.0, 0.0,
            List.of("<gray>한 대상을 처치할 때까지 타겟을 바꾸지 않습니다.</gray>")),

    /** The first monster it ever hits decides what this tower is good at for the rest of the match. */
    HARDCODED("hardcoded", "하드코딩", Category.TARGETING, Items.PAPER, 2.0, 0.6,
            List.of("<gray>처음 공격한 몬스터 종류에 피해 2배, 나머지에는 0.6배가 됩니다.</gray>")),

    /** Keeps swinging at the corpse. Waste on a straggler, free cleave on a pack. */
    OVERKILL("overkill", "오버킬", Category.TARGETING, Items.BONE, 40.0, 0.5,
            List.of("<gray>대상이 죽어도 그 자리를 2초간 계속 공격합니다.</gray>")),

    // ------------------------------------------------------------------ 생존과 어그로

    /** Instant tank, instant corpse — depends entirely on which tower drew it. */
    AGGRO_STORM("aggro_storm", "어그로 폭주", Category.SURVIVAL, Items.TARGET, 80.0, 0.0,
            List.of("<gray>어그로가 <yellow>80</yellow> 오릅니다. 라인의 적이 이 타워로 몰립니다.</gray>")),

    /**
     * Deliberately not full untargetability.
     *
     * <p>Aggro is a sort key in {@code AcquireLaneDefenseTargetGoal}, not a hard filter, so a
     * stealthed tower still gets hit when nothing else is in range. Making it unhittable instead
     * would be a pure gain, which this list does not allow.
     */
    STEALTH("stealth", "은신", Category.SURVIVAL, Items.PHANTOM_MEMBRANE, 40.0, 0.30,
            List.of(
                    "<gray>어그로가 <yellow>40</yellow> 내려갑니다.</gray>",
                    "<green>최근 3초간 피해를 받지 않았으면 피해가 30% 오릅니다.</green>"
            )),

    /** Buys five seconds at half power. On the wall, that is often the whole wave. */
    ZOMBIE_PROCESS("zombie_process", "좀비 프로세스", Category.SURVIVAL, Items.ROTTEN_FLESH, 100.0, 0.5,
            List.of("<gray>체력이 0이 되어도 5초간 버팁니다. 그동안 피해는 절반입니다.</gray>")),

    /** Eats one execution-sized hit per wave and pays for it with the rest of that wave. */
    EXCEPTION_HANDLING("exception_handling", "예외 처리", Category.SURVIVAL, Items.SHIELD, 0.30, 0.50,
            List.of(
                    "<green>체력의 30% 이상을 한 번에 깎는 피해를 1로 만듭니다.</green>",
                    "<red>발동한 웨이브 동안 공격력이 절반이 됩니다.</red>"
            )),

    /** Emergency recovery paid for by one active patch. */
    GARBAGE_COLLECTION("garbage_collection", "가비지 컬렉션", Category.SURVIVAL, Items.BUCKET, 0.25, 0.0,
            List.of(
                    "<green>웨이브당 1회, 피해 후 체력이 25% 이하면 완전히 회복됩니다.</green>",
                    "<red>활성 패치 1건을 제거하며, 패치가 없으면 발동하지 않습니다.</red>"
            )),

    /** Trades the support net for raw resilience. Cancels out with 가비지 컬렉션 nicely. */
    SIGN_FLIP("sign_flip", "부호 반전", Category.SURVIVAL, Items.GLOW_INK_SAC, 0.25, 0.0,
            List.of(
                    "<green>받는 피해가 25% 줄어듭니다.</green>",
                    "<red>아군의 회복 효과를 받을 수 없습니다.</red>"
            )),

    // ------------------------------------------------------------------ 시간과 상태

    /** Free if the player already runs a maintenance rotation, crippling if not. */
    MEMORY_LEAK("memory_leak", "메모리 누수", Category.TEMPORAL, Items.COBWEB, 0.06, 0.60,
            List.of(
                    "<red>라운드가 지날 때마다 공격 간격이 6%씩 늘어납니다.</red>",
                    "<gray>긴급 점검을 하면 초기화됩니다.</gray>"
            )),

    /** Punishes target switching, which is exactly what 무한 루프 stops doing. */
    CACHE_MISS("cache_miss", "캐시 미스", Category.TEMPORAL, Items.ICE, 10.0, 0.0,
            List.of("<gray>타겟을 바꿀 때마다 첫 공격이 0.5초 늦어집니다.</gray>")),

    /** Bad early, better than baseline afterwards. Wave length decides the verdict. */
    LAZY_LOADING("lazy_loading", "지연 로딩", Category.TEMPORAL, Items.AMETHYST_SHARD, 0.50, 1.30,
            List.of("<gray>웨이브 시작 후 10초간 성능이 50%, 이후에는 130%가 됩니다.</gray>")),

    /** One wasted swing a wave. Invisible on a fast tower, painful on a slow one. */
    FIRST_MISS("first_miss", "첫 공격 헛방", Category.TEMPORAL, Items.FEATHER, 0.0, 0.0,
            List.of("<gray>웨이브마다 첫 공격이 빗나갑니다.</gray>")),

    /** The pure gamble. Same machinery as instability, so it costs nothing extra to ship. */
    NULL_POINTER("null_pointer", "널 포인터", Category.TEMPORAL, Items.BARRIER, 0.15, 0.0,
            List.of("<red>15% 확률로 그 웨이브 내내 작동하지 않습니다.</red>")),

    // ------------------------------------------------------------------ 시스템

    /** Makes repositioning free. Worthless in a fight, excellent while still building. */
    PRICE_TAG("price_tag", "가격표 오류", Category.SYSTEM, Items.EMERALD, 1.0, 0.0,
            List.of("<green>판매가가 감가 없이 투자한 금액 전부가 됩니다.</green>")),

    /** Cuts the tower off from the safe path and leaves only the one that always bugs. */
    ROLLBACK_FAILURE("rollback_failure", "롤백 실패", Category.SYSTEM, Items.WRITABLE_BOOK, 0.0, 0.0,
            List.of(
                    "<red>정식 패치가 걸리지 않습니다. 핫픽스만 적용됩니다.</red>",
                    "<gray>버전 고정으로 봉인하면 더 이상 손해가 아닙니다.</gray>"
            )),

    /** Locks out the match-limited resource in exchange for a permanent pipeline bonus. */
    READ_ONLY("read_only", "읽기 전용", Category.SYSTEM, Items.BOOK, 0.20, 0.0,
            List.of(
                    "<red>이 타워에는 최적화를 걸 수 없습니다.</red>",
                    "<green>대신 패치 효율이 20% 오릅니다.</green>"
            ));

    /** Grouping used by the dialog so 25 entries stay readable. */
    public enum Category {
        DAMAGE("공격 계산"),
        TARGETING("타겟 선정"),
        SURVIVAL("생존과 어그로"),
        TEMPORAL("시간과 상태"),
        SYSTEM("시스템");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String key;
    private final String displayName;
    private final Category category;
    private final Item item;
    private final double defaultPrimary;
    private final double defaultSecondary;
    private final List<String> description;

    DeveloperBug(
            String key,
            String displayName,
            Category category,
            Item item,
            double defaultPrimary,
            double defaultSecondary,
            List<String> description
    ) {
        this.key = key;
        this.displayName = displayName;
        this.category = category;
        this.item = item;
        this.defaultPrimary = defaultPrimary;
        this.defaultSecondary = defaultSecondary;
        this.description = List.copyOf(description);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }

    public Item item() {
        return item;
    }

    public List<String> description() {
        return description;
    }

    public double primary() {
        return TowerBalanceRuntime.ability(DeveloperBalance.CONFIG_ID, key + "BugPrimary", defaultPrimary);
    }

    public double secondary() {
        return TowerBalanceRuntime.ability(DeveloperBalance.CONFIG_ID, key + "BugSecondary", defaultSecondary);
    }

    /**
     * Whether copying this onto another tower can quietly lose the lane.
     *
     * <p>Both entries here move aggro. Spread far enough, they leave nobody holding the front,
     * which is the failure the 재현 dialog has to warn about — a player chasing a "good" bug will
     * not notice until the wave walks through.
     */
    public boolean dangerousToSpread() {
        return this == STEALTH || this == AGGRO_STORM;
    }

    /** Shipped default, used to seed the balance file. Reading {@link #primary()} there would recurse. */
    public double defaultPrimary() {
        return defaultPrimary;
    }

    public double defaultSecondary() {
        return defaultSecondary;
    }

    public String primaryKey() {
        return key + "BugPrimary";
    }

    public String secondaryKey() {
        return key + "BugSecondary";
    }

    public static Optional<DeveloperBug> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (DeveloperBug bug : values()) {
            if (bug.key.equals(normalized)) {
                return Optional.of(bug);
            }
        }
        return Optional.empty();
    }
}
