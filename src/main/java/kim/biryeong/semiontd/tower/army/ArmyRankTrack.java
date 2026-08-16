package kim.biryeong.semiontd.tower.army;

/**
 * Which rank ladder a tower climbs.
 *
 * <p>The mechanics in {@link ArmyRank} are identical across tracks — four steps, the same attack and
 * buff numbers. Only the titles differ, so that a tower called 소대장 is addressed as an officer
 * rather than being called 이등병, which read as a mistake in game.
 *
 * <p>Track follows tier, not line: a T1 is enlisted, a T2 is an NCO, a T3 is an officer. That keeps
 * the two axes readable — tier buys the ladder, service climbs it.
 */
public enum ArmyRankTrack {
    /** T1. Conscripts. */
    ENLISTED("이등병", "일병", "상병", "병장"),
    /** T2. Non-commissioned officers. */
    NCO("하사", "중사", "상사", "원사"),
    /** T3. Commissioned officers, matching the 소대장 / 포대장 / 헌병대장 titles. */
    OFFICER("소위", "중위", "대위", "소령");

    private final String[] titles;

    ArmyRankTrack(String... titles) {
        this.titles = titles;
    }

    /** Title for a rank on this ladder. */
    public String titleOf(ArmyRank rank) {
        ArmyRank resolved = rank == null ? ArmyRank.PRIVATE : rank;
        return titles[Math.min(resolved.ordinal(), titles.length - 1)];
    }
}
