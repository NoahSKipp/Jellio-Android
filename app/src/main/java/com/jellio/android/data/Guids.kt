package com.jellio.android.data

// The plugin writes GUIDs with dashes, Jellyfin items carry them without.
fun normalizeGuid(id: String): String = id.replace("-", "").lowercase()
