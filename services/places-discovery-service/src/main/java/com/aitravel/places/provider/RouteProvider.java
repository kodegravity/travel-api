package com.aitravel.places.provider;

import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;

/** Puts a trip's stops in a short visiting order and times each leg. */
public interface RouteProvider {

    OptimizedRoute optimize(RouteRequest request);
}
