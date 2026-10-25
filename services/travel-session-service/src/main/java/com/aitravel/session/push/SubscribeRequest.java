package com.aitravel.session.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code PushSubscription.toJSON()} from the browser, minus {@code expirationTime}. */
public record SubscribeRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotNull @Valid Keys keys
) {

    /** Both base64url, as the browser encodes them. */
    public record Keys(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+=*") String p256dh,
            @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9_-]+=*") String auth
    ) {
    }
}
