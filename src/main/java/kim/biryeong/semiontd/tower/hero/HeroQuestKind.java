package kim.biryeong.semiontd.tower.hero;

public enum HeroQuestKind {
    WAVE_CLEAR("웨이브 클리어"),
    CLEAN_CLEAR("무결점 클리어"),
    WEAPON_KILLS("지정 무기 처치"),
    WEAPON_DAMAGE("지정 무기 피해"),
    GREATSWORD_MULTI_HIT("대검 휩쓸기"),
    LONGBOW_MARK_DAMAGE("장궁 표식 피해"),
    STAFF_SPECIAL_HITS("지팡이 연쇄·광역 적중"),
    SWORD_DAMAGE_PREVENTED("검 피해 방어"),
    TOME_HEALING("치유서 회복"),
    HERO_KILLS("용사 처치"),
    COMPANION_KILLS("동료 처치"),
    PARTY_DAMAGE("파티 합산 피해"),
    HERO_SURVIVAL("용사 생존"),
    PARTY_SURVIVAL("파티 전원 생존"),
    KNIGHT_GUARD("기사 피해 방어"),
    ARCHER_BOSS_DAMAGE("궁수 보스 피해"),
    MAGE_SPLASH_HITS("마법사 광역 적중"),
    PRIEST_HEALING("사제 회복"),
    ROGUE_EXECUTE_HITS("도적 처형 공격"),
    BARD_AURA_SUPPORT("음유시인 오라 지원");

    private final String displayName;

    HeroQuestKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
