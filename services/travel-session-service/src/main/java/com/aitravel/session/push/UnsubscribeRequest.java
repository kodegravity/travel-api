package com.aitravel.session.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnsubscribeRequest(@NotBlank @Size(max = 1024) String endpoint) {
}
