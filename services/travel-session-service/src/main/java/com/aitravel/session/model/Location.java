package com.aitravel.session.model;

import java.math.BigDecimal;

/** A traveler coordinate. Treated as sensitive: never logged and never returned by the API. */
public record Location(BigDecimal latitude, BigDecimal longitude) {
}
