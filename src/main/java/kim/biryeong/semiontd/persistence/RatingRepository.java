package kim.biryeong.semiontd.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.rating.PlayerRatingProfile;

public interface RatingRepository {
    Optional<PlayerRatingProfile> findProfile(UUID playerId);

    PlayerRatingProfile saveProfile(UUID playerId, PlayerRatingProfile profile);

    Map<UUID, PlayerRatingProfile> findAllProfiles();
}
