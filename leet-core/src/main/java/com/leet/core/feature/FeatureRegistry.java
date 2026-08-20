package com.leet.core.feature;

import java.util.Collection;
import java.util.Optional;

/**
 * Narrow, read-only view of the shared feature registry exposed to other plugins
 * through {@link com.leet.core.CoreApi}. Consumers can look up and enumerate
 * features without being coupled to {@link FeatureManager}'s concrete lifecycle or
 * toggle-persistence internals.
 */
public interface FeatureRegistry {

    /** The registered feature with the given id, if present. */
    Optional<AbstractFeature> get(String id);

    /** All currently registered features (unmodifiable). */
    Collection<AbstractFeature> all();
}
