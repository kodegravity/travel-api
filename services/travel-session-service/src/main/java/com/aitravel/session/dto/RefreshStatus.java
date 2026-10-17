package com.aitravel.session.dto;

public enum RefreshStatus {
    /** Recommendations were regenerated and returned. */
    UPDATED,
    /** No refresh was needed. No external provider was called. */
    UNCHANGED,
    /** The evaluation succeeded but no unseen eligible attractions remain. */
    NO_NEW_RESULTS
}
