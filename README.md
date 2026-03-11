# 🛡️ Brave YouTube

A YouTube viewing app for Android with built-in ad blocking, inspired by [Brave Browser](https://github.com/brave/brave-browser).

## Features

### 🛡️ Brave Shields Ad Blocking
- **Network-level blocking**: Intercepts and blocks requests to known ad-serving domains (DoubleClick, Google Syndication, etc.)
- **Cosmetic filtering**: Hides ad elements using CSS injection (banner ads, companion ads, overlay ads)
- **Scriptlet injection**: Neutralizes YouTube's ad player scripts to skip pre-roll and mid-roll ads
- **Procedural filtering**: Advanced element hiding using `:has()`, `:has-text()` selectors
- **Aggressive mode**: Maximum blocking strength (Brave defaults to aggressive on YouTube)

### 📺 YouTube-Specific Features
- **Pre-roll/Mid-roll ad skipping**: Automatically fast-forwards and skips video ads
- **Overlay ad removal**: Closes banner overlays on the video player
- **Feed ad hiding**: Removes promoted/sponsored content from home feed and search results
- **Shorts blocking**: Option to hide YouTube Shorts shelf
- **Recommendation blocking**: Option to hide distracting recommendation content
- **Premium upsell blocking**: Hides YouTube Premium nagging prompts

### 🎵 Background Playback
- Continue listening to audio when the app is minimized
- Foreground service with media notification controls
- Page Visibility API override to prevent YouTube from pausing

### 🎨 UI/UX (Brave-Inspired)
- Material Design 3 with Brave's signature orange color scheme
- **Shields Panel**: Tap the shield icon to see real-time blocking stats
- Toggle individual protections (ads, trackers, cosmetic filtering)
- Desktop mode for full YouTube experience in WebView
- Deep link support for YouTube URLs

## Architecture

```
com.aspect.braveyoutube/
├── BraveYouTubeApp.kt          # Application class, initializes AdBlockEngine
├── MainActivity.kt              # Main activity with Compose UI
├── adblock/
│   ├── AdBlockEngine.kt         # Core ad-blocking engine (domain + pattern blocking)
│   └── FilterListManager.kt     # Manages filter list subscriptions
├── webview/
│   ├── BraveWebViewClient.kt    # Request interception & script injection
│   ├── BraveWebChromeClient.kt  # Fullscreen & progress handling
│   └── BraveJavaScriptInterface.kt  # JS ↔ Kotlin communication bridge
├── ui/
│   ├── MainViewModel.kt         # UI state management
│   ├── theme/                    # Material Design 3 theming
│   ├── components/
│   │   ├── SearchBar.kt         # URL/search bar with shield badge
│   │   ├── ShieldsPanel.kt      # Brave Shields popup panel
│   │   └── VideoWebView.kt      # WebView Compose wrapper
│   └── screens/
│       └── MainScreen.kt        # Main app screen
├── data/
│   └── PreferencesManager.kt    # DataStore preferences
├── service/
│   └── BackgroundPlaybackService.kt  # Background audio service
└── assets/
    ├── adblock/
    │   ├── ad_domains.txt        # Blocked domain list
    │   └── youtube_cosmetic_filters.json  # CSS element hiding rules
    └── js/
        ├── adblock_inject.js     # Ad-blocking injection script
        └── youtube_enhancer.js   # Background playback & enhancements
```

## How Ad Blocking Works

This app implements a multi-layered ad blocking strategy similar to Brave Browser:

### Layer 1: Network-Level Blocking
The `BraveWebViewClient` intercepts every HTTP request via `shouldInterceptRequest()`. Requests to known ad domains (DoubleClick, Google Syndication, etc.) are blocked by returning empty responses.

### Layer 2: Cosmetic Filtering (CSS)
After each page load, CSS rules are injected to hide ad-related DOM elements (banners, overlays, promoted content). This is equivalent to Brave's cosmetic filtering engine.

### Layer 3: Scriptlet Injection (JavaScript)
JavaScript is injected to:
- Detect when a video ad is playing (`.ad-showing` class)
- Fast-forward the ad to its end (`video.currentTime = video.duration`)
- Click skip buttons automatically
- Close overlay ads

### Layer 4: Mutation Observer
A MutationObserver watches for dynamically added ad elements (YouTube is a Single Page Application) and removes them in real-time.

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Build
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material Design 3
- **Browser Engine**: Android WebView with custom client
- **Ad Blocking**: Custom engine inspired by Brave's adblock-rust
- **Preferences**: DataStore
- **Architecture**: MVVM with StateFlow

## Credits
- Inspired by [Brave Browser](https://github.com/brave/brave-browser) and their [adblock-rust](https://github.com/nickerbocker/nickerbocker.github.io) engine
- Ad domain lists sourced from community-maintained filter lists

## Disclaimer
This project is for educational purposes. It is not affiliated with Brave Software, Inc. or YouTube/Google.

## License
MIT License
