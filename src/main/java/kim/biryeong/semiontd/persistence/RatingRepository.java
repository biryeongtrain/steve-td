package kim.biryeong.semiontd.persistence;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.rating.SemionRatingProfile;

public interface RatingRepository {
    Optional<SemionRatingProfile> findProfile(UUID playerId);

    SemionRatingProfile saveProfile(UUID playerId, SemionRatingProfile profile);
}
