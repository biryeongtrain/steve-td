package kim.biryeong.semiontd.tower.description;

import java.util.List;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.config.TowerBalanceConfig;

@FunctionalInterface
public interface TowerDescriptionFactory {
    List<String> build(TowerType type, TowerBalanceConfig config);
}
