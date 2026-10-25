package com.aitravel.session.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aitravel.session.support.MutableClock;
import java.time.Duration;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FeedbackServiceTest {

    private static final String TRAVELER = "okta-subject";

    private final MutableClock clock = new MutableClock();
    private final FeedbackService service = new FeedbackService(new InMemoryFeedbackStore(), JsonMapper.builder().build(), clock);

    @Test
    void theLatestRatingOfAPlaceWinsAndComesFirst() {
        service.rate(TRAVELER, "a", new RatePlaceRequest(Rating.LIKE, "Prado", "ART", "madrid"));
        clock.advance(Duration.ofSeconds(1));
        service.rate(TRAVELER, "b", new RatePlaceRequest(Rating.LIKE, "Retiro", null, "madrid"));
        clock.advance(Duration.ofSeconds(1));
        FeedbackResponse response = service.rate(TRAVELER, "a", new RatePlaceRequest(Rating.DISLIKE, " Prado ", " ", null));

        assertThat(response.places()).extracting(PlaceFeedback::placeId).containsExactly("a", "b");
        assertThat(response.places().getFirst().rating()).isEqualTo(Rating.DISLIKE);
        assertThat(response.places().getFirst().name()).isEqualTo("Prado");
        assertThat(response.places().getFirst().category()).isNull();
        assertThat(service.list(TRAVELER)).isEqualTo(response);
    }

    @Test
    void onlyDislikesAreExcludedAndClearingAPlaceUndoesIt() {
        service.rate(TRAVELER, "liked", new RatePlaceRequest(Rating.LIKE, null, null, null));
        service.rate(TRAVELER, "hated", new RatePlaceRequest(Rating.DISLIKE, null, null, null));

        assertThat(service.excludedPlaceIds(TRAVELER)).containsExactly("hated");
        assertThat(service.excludedPlaceIds("someone-else")).isEmpty();

        service.clear(TRAVELER, "hated");
        assertThat(service.excludedPlaceIds(TRAVELER)).isEmpty();
    }

    @Test
    void keepsOnlyTheNewestRatings() {
        IntStream.rangeClosed(1, FeedbackService.MAX_ENTRIES + 5).forEach(i -> {
            clock.advance(Duration.ofSeconds(1));
            service.rate(TRAVELER, "p" + i, new RatePlaceRequest(Rating.DISLIKE, null, null, null));
        });

        FeedbackResponse response = service.list(TRAVELER);
        assertThat(response.places()).hasSize(FeedbackService.MAX_ENTRIES);
        assertThat(response.places().getFirst().placeId()).isEqualTo("p" + (FeedbackService.MAX_ENTRIES + 5));
    }

    @Test
    void anUnreachableStoreMeansNoExclusionsButAFailedSave() {
        FeedbackStore broken = mock(FeedbackStore.class);
        when(broken.find(anyString())).thenThrow(new IllegalStateException("down"));
        FeedbackService degraded = new FeedbackService(broken, JsonMapper.builder().build(), clock);

        assertThat(degraded.excludedPlaceIds(TRAVELER)).isEmpty();

        FeedbackStore conflicting = mock(FeedbackStore.class);
        when(conflicting.save(anyString(), anyLong(), any())).thenReturn(false);
        FeedbackService busy = new FeedbackService(conflicting, JsonMapper.builder().build(), clock);
        assertThatThrownBy(() -> busy.rate(TRAVELER, "a", new RatePlaceRequest(Rating.LIKE, null, null, null)))
                .isInstanceOf(FeedbackUnavailableException.class);
    }
}
