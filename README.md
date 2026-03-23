# Flinku Android SDK

Native Kotlin SDK for Flinku deep linking. Supports Android 5.0+ (API 21+).

## Security

- Do not commit API keys or other secrets. The test app reads `flinku.api.key` and optional `flinku.base.url` from `local.properties` (gitignored). Copy `local.properties.example` to `local.properties` and fill in values locally.

## Installation

### Gradle

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("dev.flinku:flinku-sdk:0.1.0")
}
```

## Usage

### Initialize in Application or MainActivity

```kotlin
import dev.flinku.sdk.Flinku
import dev.flinku.sdk.FlinkuConfig

Flinku.configure(this, FlinkuConfig(
    apiKey = "<your-api-key>",
    debugMode = true
))
```

Load the API key from your own secure storage or build configuration; do not hardcode production keys in source control.

### Check for deferred deep link

```kotlin
lifecycleScope.launch {
    val link = Flinku.match()
    if (link.matched) {
        // Navigate to link.deepLink
    }
}
```

## Test app

1. Copy `local.properties.example` to `local.properties` in the project root.
2. Set `flinku.api.key` to your test key.
3. Build and run the `:app` module.
