package kim.biryeong.semiontd.api.area;

import java.util.Objects;
import java.util.Optional;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.tower.Tower;

public record AreaTowerTarget(Tower tower, Optional<SemionTowerEntity> entity, boolean illusion) {
    public AreaTowerTarget {
        Objects.requireNonNull(tower, "tower");
        entity = entity == null ? Optional.empty() : entity;
    }
}
