package com.aitravel.session.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogIdsTest {

    @Test
    void hashesSubjectToStableShortValueWithoutRevealingIt() {
        String hashed = LogIds.subject("traveler@example.com");

        assertThat(hashed).hasSize(12).matches("[0-9a-f]{12}").doesNotContain("traveler");
        assertThat(LogIds.subject("traveler@example.com")).isEqualTo(hashed);
        assertThat(LogIds.subject("other@example.com")).isNotEqualTo(hashed);
    }

    @Test
    void nullSubjectIsLoggedAsNone() {
        assertThat(LogIds.subject(null)).isEqualTo("none");
    }
}
