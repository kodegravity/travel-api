package com.aitravel.places.api;

import com.aitravel.places.provider.AttractionProvider;
import jakarta.validation.Valid;
import com.aitravel.places.model.Attraction;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/places")
public class PlacesSearchController {

    private static final Logger log = LoggerFactory.getLogger(PlacesSearchController.class);

    private final AttractionProvider attractionProvider;

    public PlacesSearchController(AttractionProvider attractionProvider) {
        this.attractionProvider = attractionProvider;
    }

    @PostMapping("/search")
    public PlacesSearchResponse search(@Valid @RequestBody PlacesSearchRequest request) {
        PlacesSearchRequest.Coordinates location = request.location();
        log.info("POST /internal/v1/places/search received radiusKm={}", request.searchRadiusKm());
        log.debug("Places search coordinates latitude={} longitude={}", location.latitude(), location.longitude());
        List<Attraction> attractions = attractionProvider.findNearby(
                location.latitude(), location.longitude(), request.searchRadiusKm());
        log.info("POST /internal/v1/places/search completed count={}", attractions.size());
        return new PlacesSearchResponse(attractions);
    }
}
