package kim.biryeong.semionTd.entity.defender;

public interface LaneDefenseEntity {
    boolean defendsLane(int laneId);

    int aggroPriority();
}
