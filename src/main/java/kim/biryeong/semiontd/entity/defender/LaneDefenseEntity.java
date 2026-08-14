package kim.biryeong.semiontd.entity.defender;

public interface LaneDefenseEntity {
    boolean defendsLane(int laneId);

    int aggroPriority();

    /**
     * Monsters skip defenders that do not draw aggro when picking a target.
     */
    default boolean drawsAggro() {
        return true;
    }
}
