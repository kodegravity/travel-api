package com.aitravel.places.api;

import com.aitravel.places.provider.AttractionProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/places")
public class PlacesSearchController {

    private final AttractionProvider attractionProvider;

    public PlacesSearchController(AttractionProvider attractionProvider) {
        this.attractionProvider = attractionProvider;
    }

    @PostMapping("/search")
    public PlacesSearchResponse search(@Valid @RequestBody PlacesSearchRequest request) {
        PlacesSearchRequest.Coordinates location = request.location();
        return new PlacesSearchResponse(attractionProvider.findNearby(
                location.latitude(), location.longitude(), request.searchRadiusKm()));
    }
}
