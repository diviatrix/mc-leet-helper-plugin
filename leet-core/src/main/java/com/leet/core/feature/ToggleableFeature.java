package com.leet.core.feature;

import java.util.UUID;

/**
 * A feature whose per-player effect can be toggled on/off (the personal /leet
 * toggle). Absence from the store means enabled. Provided by the gated-feature
 * base; retained as an explicit contract for independent systems that want the
 * same toggle semantics without the gated lifecycle.
 */
public interface ToggleableFeature {

    /** Whether the feature is enabled for this player (absent = enabled). */
    boolean isUserEnabled(UUID uuid);

    /** The feature id, used to namespace toggle state. */
    String featureId();
}
