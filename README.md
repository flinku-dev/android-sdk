# Flinku Android SDK

Official Android SDK for [Flinku](https://flinku.dev) — deferred deep linking for Android. The modern replacement for Firebase Dynamic Links.

## Installation

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'dev.flinku:flinku-android-sdk:0.2.0'
}
```

Or add via JitPack — add to your root `build.gradle`:

```gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Then:

```gradle
dependencies {
    implementation 'com.github.flinku-dev:android-sdk:0.2.0'
}
```

## Setup

Configure in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Flinku.configure(this, baseUrl = "https://yourapp.flku.dev")
    }
}
```

## Handle deep links

Call `Flinku.match()` in your splash or main activity:

```kotlin
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val link = Flinku.match(this@SplashActivity)

            if (link.matched) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.deepLink)))
            } else {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            }
            finish()
        }
    }
}
```

## Query parameters

```kotlin
lifecycleScope.launch {
    val link = Flinku.match(this@MyActivity)

    if (link.matched) {
        val ref = link.params?.get("ref") as? String       // e.g. "instagram"
        val promo = link.params?.get("promo") as? String   // e.g. "SAVE20"
    }
}
```

## Android manifest setup

Add intent filters to your `AndroidManifest.xml`:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="yourapp.flku.dev" />
</intent-filter>
```

## Reset (testing only)

```kotlin
Flinku.reset(context)
```

## Test app

Copy `local.properties.example` to `local.properties` and set `flinku.base.url` to your project URL. Never commit secrets or production URLs you consider sensitive if your policy forbids it.
