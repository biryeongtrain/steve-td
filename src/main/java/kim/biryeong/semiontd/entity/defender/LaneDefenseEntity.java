package kim.biryeong.semiontd.entity.defender;

public interface LaneDefenseEntity {
    boolean defendsLane(int laneId);

    int aggroPriority();
}
