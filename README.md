# Jellio for Android

Native Android mobile client for [Jellio](https://github.com/NoahSKipp/Jellio) and Jellyfin servers, built with Kotlin, Jetpack Compose, and Media3 ExoPlayer.

## Highlights

- **Floating Pill Navigation**: Mobile-first glassmorphic floating pill bottom navigation bar with adaptive sidebar fallback for landscape and tablet screens.
- **Native Video & Live TV Playback**: Direct play and HLS transcoding with Media3 ExoPlayer, touch gestures (vertical brightness/volume swipe, double-tap +/-10s seek), intro/credits skip segments, trickplay thumbnails, and GroupWatch / SyncPlay.
- **Native Book & Manga Reader**:
  - **EPUB**: Reflowable reader (paged or continuous vertical scroll), typography customization, Dark/Sepia/Light themes, table of contents, full-book search, and 5-color highlights with notes.
  - **PDF**: High-DPI native rendering via Android `PdfRenderer` with pinch-to-zoom, double-tap zoom, and page scrubber.
  - **Manga & Comics**: Local CBZ archives and streamed Suwayomi chapters with 5 reading modes (RTL, LTR, Vertical Webtoon, Vertical with Gaps, Double-Page Spread), scanlator filters, brightness dimmer, and color filters.
  - **Language & Vocabulary Study**: Inline Wiktionary definitions, DeepL translation, and SM-2 Spaced Repetition Vocabulary flashcards with CSV / Anki export.
- **Native Audiobook Player & Mini-Player**: Multi-track and single-file M4B playback with embedded chapter markers, variable playback speed (0.75x to 2.0x), sleep timer with gradual 15-second audio fade-out, and a persistent docked mini-player.
- **Offline Storage & Sync Queue**: Full offline download manager for movies, episodes, books, manga chapters, and audiobooks with automatic offline reading/listening progress queueing and replay on reconnect.