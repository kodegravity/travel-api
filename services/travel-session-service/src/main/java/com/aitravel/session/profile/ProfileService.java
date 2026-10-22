package com.aitravel.session.profile;

import com.aitravel.session.logging.LogIds;
import com.aitravel.session.plan.TravelPlanService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Each traveler's profile and preferences. A traveler is registered the first time they load their profile. */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileStore store;
    private final Clock clock;

    public ProfileService(ProfileStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public ProfileResponse get(String subject) {
        String owner = TravelPlanService.ownerKey(subject);
        return ProfileResponse.of(owner, store.touch(owner, clock.instant()));
    }

    public ProfileResponse update(String subject, UpdateProfileRequest request) {
        String owner = TravelPlanService.ownerKey(subject);
        ProfileStore.ProfileFields fields = new ProfileStore.ProfileFields(trimmed(request.displayName()),
                trimmed(request.homeCity()), request.interests(), trimmed(request.currency()), request.travelMode());
        TravelerProfile saved = store.save(owner, fields, clock.instant());
        log.info("Profile saved owner={} interests={} travelMode={}", LogIds.subject(subject),
                saved.interests().size(), saved.travelMode());
        return ProfileResponse.of(owner, saved);
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
