package kim.biryeong.semiontd.entity.visual;

public enum SemionAnimationState {
    IDLE("idle"),
    WALK("walk"),
    ATTACK("attack"),
    HEAL("heal");

    private final String animationId;

    SemionAnimationState(String animationId) {
        this.animationId = animationId;
    }

    public String animationId() {
        return animationId;
    }
}
