package com.aitravel.places.api;

import com.aitravel.places.model.OptimizedRoute;
import com.aitravel.places.model.RouteRequest;
import com.aitravel.places.provider.RouteProvider;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/routes")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final RouteProvider routeProvider;

    public RouteController(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    @PostMapping("/optimize")
    public OptimizedRoute optimize(@Valid @RequestBody RouteRequest request) {
        log.info("POST /internal/v1/routes/optimize received stops={} mode={}", request.stops().size(), request.mode());
        long distinct = request.stops().stream().map(RouteRequest.Stop::id).distinct().count();
        if (distinct != request.stops().size()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Each stop may appear only once.");
            problem.setTitle("Duplicate stops");
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
        }
        OptimizedRoute route = routeProvider.optimize(request);
        log.info("POST /internal/v1/routes/optimize completed source={}", route.source());
        return route;
    }
}
