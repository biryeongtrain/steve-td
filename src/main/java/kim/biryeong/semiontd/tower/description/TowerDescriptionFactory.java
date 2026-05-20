package kim.biryeong.semiontd.tower.description;

import java.util.List;
import kim.biryeong.semiontd.tower.TowerType;

@FunctionalInterface
public interface TowerDescriptionFactory {
    List<String> build(TowerType type);
}
