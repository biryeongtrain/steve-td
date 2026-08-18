package kim.biryeong.semiontd.tower.demonlord;

/**
 * Where a demon lord skill sits on the keyboard.
 *
 * <p>Bindings are handed out in <b>build order</b>: the first altar raised takes {@code 1}, the next
 * {@code 2}, and so on. That way the skill a player leans on is always the one under their index
 * finger, instead of every skill owning a fixed far-away slot.
 *
 * <p>The first four are real hotbar slots, so pressing the number key selects them and fires. The
 * last two ride on keys that are otherwise dead weight in this game mode - {@code F} (swap with
 * offhand) and {@code Q} (drop item) - which are intercepted and cancelled before they do their
 * vanilla job.
 */
public enum DemonLordBinding {
    SLOT_1(0, "1", true),
    SLOT_2(1, "2", true),
    SLOT_3(2, "3", true),
    SLOT_4(3, "4", true),
    /**
     * The one slot you can actually hold. Selecting it does nothing; right-clicking fires. Useful
     * for a skill you want to aim carefully instead of the instant number-key cast.
     */
    RIGHT_CLICK(4, "마검 우클릭", false),
    /**
     * F 와 Q 는 핫바를 눌러 쓰는 키가 아니지만, 아이템이 없으면 쿨타임을 볼 방법이 없습니다.
     * 쿨다운 표시는 아이템 위에만 그려지기 때문입니다. 그래서 보여 주기용 자리를 줍니다 -
     * 집어 들어도 시전되지 않고 마검으로 되돌아갑니다.
     */
    OFFHAND(5, "F", false),
    DROP(6, "Q", false);

    /** Bindings that are not a hotbar slot use this sentinel. */
    public static final int NO_SLOT = -1;

    private final int hotbarSlot;
    private final String label;
    private final boolean castOnSelect;

    DemonLordBinding(int hotbarSlot, String label, boolean castOnSelect) {
        this.hotbarSlot = hotbarSlot;
        this.label = label;
        this.castOnSelect = castOnSelect;
    }

    /**
     * Whether merely selecting the slot fires the skill.
     *
     * <p>False for {@link #RIGHT_CLICK}, which is the only binding you can hold in hand - otherwise
     * the skill would fire the instant the slot was picked and you could never aim it.
     */
    public boolean castOnSelect() {
        return castOnSelect;
    }

    /** Hotbar index, or {@link #NO_SLOT} for the key-driven bindings. */
    public int hotbarSlot() {
        return hotbarSlot;
    }

    public boolean isHotbarSlot() {
        return hotbarSlot != NO_SLOT;
    }

    /** Key shown in tooltips and the tower list. */
    public String label() {
        return label;
    }

    /** The binding for a given build position, or {@code null} once the player runs out of keys. */
    public static DemonLordBinding forIndex(int index) {
        DemonLordBinding[] values = values();
        return index < 0 || index >= values.length ? null : values[index];
    }

    /** The binding that owns a hotbar slot, or {@code null} when that slot is not a skill key. */
    public static DemonLordBinding forHotbarSlot(int slot) {
        for (DemonLordBinding binding : values()) {
            if (binding.hotbarSlot == slot) {
                return binding;
            }
        }
        return null;
    }
}
