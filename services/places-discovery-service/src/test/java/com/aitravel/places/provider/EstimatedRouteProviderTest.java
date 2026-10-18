package com.aitravel.places.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;
import com.aitravel.places.model.RouteRequest.Point;
import com.aitravel.places.model.RouteRequest.Stop;
import com.aitravel.places.model.RouteRequest.TravelMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstimatedRouteProviderTest {

    private static final Point SOL = new Point(40.4169, -3.7035);
    private static final Stop PALACE = new Stop("palace", 40.4179, -3.7143);
    private static final Stop MAYOR = new Stop("mayor", 40.4155, -3.7074);
    private static final Stop RETIRO = new Stop("retiro", 40.4153, -3.6844);
    private static final Stop PRADO = new Stop("prado", 40.4138, -3.6921);

    private final EstimatedRouteProvider provider = new EstimatedRouteProvider();

    @Test
    void avoidsZigzagging() {
        // East, west, east, west: a sensible day goes one way first.
        OptimizedRoute route = provider.optimize(new RouteRequest(TravelMode.WALK, SOL, List.of(RETIRO, PALACE, PRADO, MAYOR)));

        // The west side first (nearest to Sol), then cross town once.
        assertThat(route.order().subList(0, 2)).containsExactlyInAnyOrder("mayor", "palace");
        assertThat(route.order().subList(2, 4)).containsExactly("prado", "retiro");
        assertThat(route.legs()).hasSize(4).allSatisfy(leg -> assertThat(leg.polyline()).isNull());
        assertThat(route.source()).isEqualTo("estimate");
    }

    @Test
    void walkingTakesLongerThanDriving() {
        var stops = List.of(PALACE, RETIRO);
        long walk = provider.optimize(new RouteRequest(TravelMode.WALK, SOL, stops)).durationSeconds();
        long drive = provider.optimize(new RouteRequest(TravelMode.DRIVE, SOL, stops)).durationSeconds();

        assertThat(walk).isGreaterThan(drive);
    }
}
