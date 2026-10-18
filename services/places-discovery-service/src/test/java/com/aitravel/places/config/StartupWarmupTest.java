package com.aitravel.places.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class StartupWarmupTest {

    @Test
    void warmUpDecodesAProbeTokenAndSwallowsEveryFailure() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("expected"));
        // Port 1 refuses connections, so the downstream step fails fast.
        StartupWarmup warmup = new StartupWarmup(decoder);

        assertThatCode(warmup::run).doesNotThrowAnyException();
        verify(decoder).decode(StartupWarmup.UNSIGNED_TOKEN);
    }

    @Test
    void probeTokenIsAThreePartJwt() {
        org.assertj.core.api.Assertions.assertThat(StartupWarmup.UNSIGNED_TOKEN.split("\\.")).hasSize(3);
    }
}
