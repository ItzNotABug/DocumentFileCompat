package com.lazygeniouz.dfc.observer.internal.snapshot

/** A complete cursor scan plus creations already identified during that scan. */
internal class SnapshotScan(
    val snapshot: LinkedHashMap<String, ChildState>,
    val creations: List<ChildState>,
)