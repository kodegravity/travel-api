package com.aitravel.places.provider;

import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;
import com.aitravel.places.model.RouteRequest.Point;
import com.aitravel.places.model.RouteRequest.Stop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Free routing for mock mode: the order from nearest-neighbour plus 2-opt over straight-line distances, and times from
 * an average speed with a detour factor (the same model the web app uses). No Google calls.
 */
@Component
@ConditionalOnProperty(prefix = "attraction", name = "provider", havingValue = "mock", matchIfMissing = true)
public class EstimatedRouteProvider implements RouteProvider {

    @Override
    public OptimizedRoute optimize(RouteRequest request) {
        List<Stop> order = shortOrder(request.origin(), request.stops());
        List<OptimizedRoute.Leg> legs = new ArrayList<>();
        Point from = request.origin();
        for (Stop stop : order) {
            double km = from.distanceKm(stop.point());
            legs.add(estimateLeg(km, request.mode()));
            from = stop.point();
        }
        return OptimizedRoute.of(order.stream().map(Stop::id).toList(), legs, "estimate");
    }

    static OptimizedRoute.Leg estimateLeg(double km, RouteRequest.TravelMode mode) {
        double kmh = mode == RouteRequest.TravelMode.WALK ? 4.5 : 22;
        double detour = mode == RouteRequest.TravelMode.WALK ? 1.3 : 1.4;
        double overheadMinutes = mode == RouteRequest.TravelMode.WALK || km == 0 ? 0 : 5;
        double road = km * detour;
        long seconds = Math.round((road / kmh * 60 + overheadMinutes) * 60);
        return new OptimizedRoute.Leg(Math.round(road * 1000), seconds, null);
    }

    /** An open path from the origin through every stop: nearest neighbour, then 2-opt until nothing improves. */
    static List<Stop> shortOrder(Point origin, List<Stop> stops) {
        List<Stop> left = new ArrayList<>(stops);
        List<Stop> path = new ArrayList<>();
        Point at = origin;
        while (!left.isEmpty()) {
            Point from = at;
            Stop next = Collections.min(left, (a, b) -> Double.compare(from.distanceKm(a.point()), from.distanceKm(b.point())));
            left.remove(next);
            path.add(next);
            at = next.point();
        }
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 0; i < path.size() - 1; i++) {
                for (int j = i + 1; j < path.size(); j++) {
                    if (reversalGain(origin, path, i, j) > 1e-9) {
                        Collections.reverse(path.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
        return path;
    }

    /** Distance saved by reversing path[i..j]. The path is open, so the last stop has no edge after it. */
    private static double reversalGain(Point origin, List<Stop> path, int i, int j) {
        Point before = i == 0 ? origin : path.get(i - 1).point();
        Point first = path.get(i).point();
        Point last = path.get(j).point();
        double removed = before.distanceKm(first);
        double added = before.distanceKm(last);
        if (j + 1 < path.size()) {
            Point after = path.get(j + 1).point();
            removed += last.distanceKm(after);
            added += first.distanceKm(after);
        }
        return removed - added;
    }
}
