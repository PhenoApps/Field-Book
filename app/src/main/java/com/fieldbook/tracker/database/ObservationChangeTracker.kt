package com.fieldbook.tracker.database

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic counter bumped whenever the observations table is written.
 *
 * In-memory caches of observation data (see [DataGridCache]) store the revision they were built
 * at and reload when it no longer matches. A row count is not sufficient on its own: editing an
 * existing value in place — the most common change of all — leaves the count untouched, and some
 * update paths don't refresh the timestamp either.
 *
 * The counter is process-scoped, which matches the lifetime of the in-memory caches that read it;
 * both are rebuilt from scratch when the process restarts. It deliberately does not identify
 * *which* study changed, so a write to any study invalidates cached snapshots of every study.
 * That over-invalidates in the rare cross-study case and never under-invalidates.
 */
object ObservationChangeTracker {

    private val revision = AtomicLong(0)

    /** The current revision. Compare against a previously captured value to detect writes. */
    val current: Long get() = revision.get()

    /** Records that the observations table was written. Safe to call from any thread. */
    fun markChanged() {
        revision.incrementAndGet()
    }
}
