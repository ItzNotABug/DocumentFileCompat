package com.lazygeniouz.dfc.observer.internal.snapshot

/** One event derived from two complete directory snapshots. */
internal class DiffEvent(
    val event: Int,
    val child: ChildState,
)