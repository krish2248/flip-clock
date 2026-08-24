# SPEC 02 — "Flip" : Classic Flip Clock (Android, Kotlin, Compose)

> **THIS DOCUMENT IS THE SINGLE SOURCE OF TRUTH.**
> You are an autonomous coding agent. Build the entire app from this file.
> **DO NOT ASK THE USER ANY QUESTIONS.** Every decision is made below.
> The single most important quality bar for this app is **animation smoothness**. A dropped frame is a bug.

---

## 0. AGENT OPERATING RULES

1. Kotlin + Jetpack Compose only. No XML layouts. No Java. No third-party libraries at all (not even Coil/Lottie/Accompanist). Everything is drawn by us.
2. **No network, no permissions except `WAKE_LOCK` is NOT needed — use `FLAG_KEEP_SCREEN_ON` instead.** The finished app requests **zero runtime permissions**.
3. Pin every version in §2 exactly.
4. Follow the file tree in §4 exactly.
5. **Never allocate inside a draw or animation frame.** No `Path()`, `Paint()`, `List`, `String.format`, or lambda capture allocation inside `draw{}` / `graphicsLayer{}` blocks. Pre-allocate in `remember`.
6. `graphicsLayer { }` **lambda** form only (deferred, does not recompose). Never use the deprecated `Modifier.graphicsLayer(rotationX = x)` parameter form for animated values — that form invalidates composition every frame.
7. Only the digit that changes may recompose. Verify with Layout Inspector / `Recomposition counts`.
8. Build must pass `./gradlew :app:assembleRelease` and hit a locked 60 fps (or the panel's native rate) before you report done.
9. All time handling uses `java.time` with core library desugaring.

---

## 1. PRODUCT DEFINITION

A single-screen flip clock. It looks like a 1970s split-flap clock: dark cards with a hairline seam across the middle, digits that fall over with a physical, slightly heavy motion.

### Features (complete list — nothing else)
| # | Feature |
|---|---|
| 1 | Flip-animated HH : MM (:SS optional) clock, portrait and landscape |
| 2 | Two modes: **Light** and **Dark** (plus "Follow system" as a third setting value; the user's requested two visual modes are Light and Dark) |
| 3 | 12 h / 24 h toggle, with AM/PM indicator in 12 h mode |
| 4 | Seconds on/off |
| 5 | Date line under the clock, on/off, with format choice |
| 6 | Keep screen awake toggle + screen dimming after 30 s of no touch (night-stand mode) |
| 7 | Landscape auto-fullscreen (immersive, hides system bars) |
| 8 | Tap anywhere → settings overlay fades in; tap outside → fades out |
| 9 | Optional flip "tick" sound (off by default, generated with `SoundPool` from a tiny bundled 24 kB wav — if you cannot supply an asset, synthesise a click with `AudioTrack` at 8 kHz, 40 ms, decaying square wave) |
| 10 | Optional battery + charging indicator dot in the corner (read from `ACTION_BATTERY_CHANGED` sticky intent, no permission needed) |

### Non-goals (DO NOT BUILD)
Alarms, timers, stopwatch, world clock, weather, widgets, wallpapers, themes beyond light/dark, custom fonts downloaded at runtime, Wear OS, ads, analytics, any network call.

---

## 2. TECH STACK — PINNED

- compileSdk 35, targetSdk 35, **minSdk 26**.
- Gradle 8.9, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00, KSP not needed (no Room).
- Dependencies (exhaustive): `core-ktx 1.15.0`, `lifecycle-runtime-ktx 2.8.7`, `lifecycle-runtime-compose 2.8.7`, `lifecycle-viewmodel-compose 2.8.7`, `activity-compose 1.9.3`, Compose BOM (`ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-extended`), `datastore-preferences 1.1.1`, `kotlinx-coroutines-android 1.9.0`, `desugar_jdk_libs 2.1.4`.
- `namespace = "dev.flip.clock"`, `applicationId = "dev.flip.clock"`.
- `isCoreLibraryDesugaringEnabled = true`, Java 17, `jvmTarget = "17"`.

Use the same `libs.versions.toml` / `settings.gradle.kts` shape as any standard Compose project; drop the Room and KSP entries.

### 2.1 Release build config
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

---

## 3. `AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".FlipApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="false"
        android:enableOnBackInvokedCallback="true"
        android:theme="@style/Theme.Flip">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="fullUser"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode|density|fontScale"
            android:theme="@style/Theme.Flip">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```
`configChanges` is deliberate: rotation must **not** recreate the Activity, so the clock never stutters or resets when the device is turned. Compose handles the relayout itself.

`res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Flip" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#0B0B0D</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
    </style>
</resources>
```
Add `res/values-night/themes.xml` and `res/values-v27/themes.xml` as needed; keep `windowBackground` dark in both to avoid a white flash on cold start (this app is dark-first).

---

## 4. FILE TREE

```
app/src/main/java/dev/flip/clock/
├── FlipApp.kt
├── MainActivity.kt                 // edge-to-edge, immersive in landscape, keep-screen-on
├── AppContainer.kt                 // settings repo + sound player
│
├── time/
│   ├── ClockTicker.kt              // wall-clock-aligned tick Flow
│   ├── TimeParts.kt                // data class + splitting logic
│   └── DateFormats.kt
│
├── settings/
│   ├── SettingsRepository.kt       // DataStore
│   ├── SettingsModels.kt           // ThemeMode, DateStyle, FlipStyle, enums
│   └── SettingsSheet.kt            // the overlay UI
│
├── flip/
│   ├── FlipDigit.kt                // ONE digit column, owns its animation
│   ├── FlipCard.kt                 // static/half rendering primitives
│   ├── FlipGeometry.kt             // sizing math
│   ├── FlipSpec.kt                 // durations, easings, camera distance, shading curves
│   └── DigitFace.kt                // draws a single glyph centered in a half-card
│
├── ui/
│   ├── ClockScreen.kt              // composes the whole face
│   ├── ClockViewModel.kt
│   ├── Separator.kt                // the ":" dots
│   ├── AmPmTag.kt
│   ├── DateLine.kt
│   ├── BatteryDot.kt
│   └── theme/ { Color.kt, Theme.kt, Type.kt }
│
└── util/
    ├── Dimming.kt                  // idle-dim controller
    └── Sound.kt                    // click player
```

---

## 5. VISUAL DESIGN (exact values)

### 5.1 Anatomy of one digit card
```
        ┌───────────────────────────┐   ← corner radius R
        │                           │
        │           7               │   TOP HALF   (height H/2)
        │                           │
        ├───────────────────────────┤   ← SEAM: a 2 px gap + 1 px highlight line
        │                           │
        │           7               │   BOTTOM HALF (height H/2)
        │                           │
        └───────────────────────────┘
```
- The glyph is drawn **once at full size, centered on the whole card**, then each half clips it. Never draw two half-glyphs — they will never align.
- Card aspect ratio **W : H = 0.68 : 1**.
- Corner radius `R = H * 0.10`, clamped to `[8.dp, 28.dp]`.
- Seam: draw a `2.dp` tall rectangle of the background color across the vertical center, then a `1.dp` line at `seamTop` in `seamHighlight` color at 0.5 alpha, and a `1.dp` line at `seamBottom` in black at 0.25 alpha. The seam sells the illusion; do not skip it.
- Digit glyph: `FontFamily.Default`, `FontWeight.Bold`, size = `H * 0.72`, color = `digitColor`, **tabular figures** (`fontFeatureSettings = "tnum"`), letterSpacing 0.
  - Optional (nice): draw the glyph with `TextMeasurer` + `drawText` into the card so it can be clipped precisely.
- Gap between the two digits of a group = `W * 0.06`. Gap between groups (HH | MM) = `W * 0.42` (the separator lives there).

### 5.2 Colors — `ui/theme/Color.kt`

```kotlin
// DARK MODE (default)
val D_Screen        = Color(0xFF0B0B0D)   // page background
val D_CardTop       = Color(0xFF1C1C21)   // top half base
val D_CardBottom    = Color(0xFF17171B)   // bottom half base (slightly darker = depth)
val D_CardEdge      = Color(0xFF2A2A31)   // 1px outline
val D_Digit         = Color(0xFFF4F4F6)
val D_DigitShadow   = Color(0x66000000)
val D_SeamHighlight = Color(0xFF34343C)
val D_Separator     = Color(0xFFF4F4F6)
val D_SecondaryText = Color(0xFF8A8A94)
val D_Accent        = Color(0xFFE8A33D)   // AM/PM tag, charging dot

// LIGHT MODE
val L_Screen        = Color(0xFFEFEFEA)
val L_CardTop       = Color(0xFFFDFDFB)
val L_CardBottom    = Color(0xFFF4F4F0)
val L_CardEdge      = Color(0xFFDDDDD6)
val L_Digit         = Color(0xFF1A1A1C)
val L_DigitShadow   = Color(0x33000000)
val L_SeamHighlight = Color(0xFFFFFFFF)
val L_Separator     = Color(0xFF1A1A1C)
val L_SecondaryText = Color(0xFF6E6E75)
val L_Accent        = Color(0xFFC2761A)
```

Wrap the whole palette in a `data class FlipColors(...)` provided via `CompositionLocalOf`. When the theme changes, animate **every** color with `animateColorAsState(tween(400, easing = FastOutSlowInEasing))` so the switch is a smooth wash, not a snap.

### 5.3 Layout / sizing (`FlipGeometry.kt`)
Given the available content size `(availW, availH)` and the number of digit groups:

```
groups   = if (showSeconds) 3 else 2          // HH, MM, SS
digits   = groups * 2
sepCount = groups - 1

// horizontal budget
// total = digits*W + (groups)*innerGap + sepCount*groupGap
// innerGap = 0.06W applied once per group (between its two digits)
// groupGap = 0.42W
widthUnits  = digits * 1f + groups * 0.06f + sepCount * 0.42f
cardW_fromW = availW / widthUnits
cardH_fromW = cardW_fromW / 0.68f

cardH_fromH = availH * (if (landscape) 0.78f else 0.42f)
cardW_fromH = cardH_fromH * 0.68f

cardW = min(cardW_fromW, cardW_fromH)
cardH = cardW / 0.68f
```
Clamp `cardH` to `[64.dp, 340.dp]`. Portrait with seconds off should end up ≈ 0.38–0.42 of screen height. Compute this in a `BoxWithConstraints` and pass concrete `Dp` values down; **do not** let text autosize.

### 5.4 Screen composition (portrait)
```
[ status bar space ]
        (weight 1)
   HH : MM  ( : SS )
        AM                     ← only in 12h mode, small tag under-right of the last digit
        (16.dp)
   Fri, 24 July                ← date line, optional
        (weight 1.2)
[ nav bar space ]
```
Landscape: identical but vertically centered, cards larger, date line to the right of the clock if `availH < 320.dp` else below.

`BatteryDot` sits in the top-right safe area: a 8.dp circle, `secondaryText` color at 0.5 alpha, turning `accent` while charging, plus a 10sp percentage label to its left when the setting is on.

---

## 6. THE FLIP ANIMATION — THIS IS THE CORE OF THE APP

### 6.1 The physical model
A real split-flap unit shows digit **N**. To advance to **N+1**:
- The **upper flap of N** is hinged at the middle seam and falls **forward and down**, rotating about the horizontal axis from 0° to −90°.
- Behind it, the **upper half of N+1** is already sitting there, static, revealed as the flap falls away.
- At exactly −90° the falling flap is edge-on and invisible. At that instant the **lower flap of N+1** takes over: it starts at +90° (edge-on, hinged at the seam) and falls **down and forward** to 0°, covering the lower half of N.

So at any moment there are exactly **four** layers:

| z-order | layer | content | state |
|---|---|---|---|
| 0 (back) | upper static | **top half of the NEW digit** | static |
| 0 (back) | lower static | **bottom half of the OLD digit** | static |
| 1 (front) | upper flap | **top half of the OLD digit** | rotates 0° → −90°, hinge at its BOTTOM edge, visible only in phase 1 |
| 1 (front) | lower flap | **bottom half of the NEW digit** | rotates +90° → 0°, hinge at its TOP edge, visible only in phase 2 |

Because each moving flap is hidden outside its own phase, you never see a back face and never need `cameraDistance` tricks to avoid z-fighting.

### 6.2 Progress → angle mapping

One `Animatable<Float>` per digit, `p ∈ [0,1]`.

```kotlin
// phase 1: p in [0, 0.5)   -> topFlapAngle = lerp(0f, -90f, easeIn(p / 0.5f))
// phase 2: p in [0.5, 1]   -> bottomFlapAngle = lerp(90f, 0f, easeOut((p - 0.5f) / 0.5f))
```

Easings (`FlipSpec.kt`):
```kotlin
val EaseFallIn  = CubicBezierEasing(0.55f, 0.03f, 0.86f, 0.30f) // gravity-like acceleration
val EaseFallOut = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1.00f) // decelerate into the stop
const val FLIP_MS_DEFAULT = 520          // total, both phases
const val FLIP_MS_FAST    = 340          // used when seconds are shown
const val FLIP_MS_SLOW    = 700          // "Relaxed" style
```
Rules:
- Seconds digits use `FLIP_MS_FAST` (must finish well inside 1000 ms; 340 ms leaves ample margin).
- Minute/hour digits use the user's `flipSpeed` setting (Fast 340 / Normal 520 / Relaxed 700).
- **A flip must never exceed 900 ms**, and a seconds flip must never exceed 700 ms. Clamp.

### 6.3 The optional bounce (on by default)
After phase 2 reaches 0°, add a tiny settle so the flap "lands":
```kotlin
// run as a second animateTo after the main one
angle: 0f -> +6f -> -2f -> 0f
spec: keyframes { durationMillis = 130; 0f at 0; 6f at 45 with LinearOutSlowInEasing; -2f at 90; 0f at 130 }
```
Implement by animating the bottom flap's `rotationX` past 0 rather than adding another Animatable. Setting `flipBounce = false` skips this.

### 6.4 Perspective
`graphicsLayer { cameraDistance = 16f * density }`.
- Compose's default is `DefaultCameraDistance = 8f`; **8 is too aggressive** for tall cards and produces a fish-eye stretch at 45°.
- Use **16f** and visually verify at both the smallest and largest card sizes. If the flap looks like it's stretching toward the viewer, raise to 22f; if it looks perfectly flat/fake, lower to 12f. Do not go below 10f.

### 6.5 Shading (this is what makes it look real)
Two overlay scrims, both drawn *inside* the flap's own layer so they rotate with it:

1. **Falling top flap** — darkens as it turns away:
   `topScrimAlpha = 0.62f * easeIn(phase1Progress)`, black. At −90° it is 0.62 (and invisible anyway).
2. **Incoming bottom flap** — starts dark (it is facing up/away from the light) and brightens as it lands:
   `bottomScrimAlpha = 0.62f * (1f - easeOut(phase2Progress))`, black.
3. **Static lower half (old digit)** — receives the shadow of the flap passing above it:
   `castShadowAlpha = 0.34f * (1f - abs(2f*p - 1f))` — peaks at the midpoint, zero at both ends. Draw it as a top-anchored vertical gradient (`Brush.verticalGradient(0f to black.copy(alpha), 0.55f to Color.Transparent)`) over the lower static half only.

In light mode use black scrims at **0.5×** these alphas (paper doesn't go as dark).

### 6.6 Building the halves in Compose

```kotlin
@Composable
fun CardHalf(
    digit: Int, half: Half, w: Dp, h: Dp, colors: FlipColors, modifier: Modifier = Modifier
) {
    // h is the FULL card height; the half box is h/2 tall and clips a full-size face
    Box(
        modifier
            .size(w, h / 2)
            .clip(halfShape(half, radius))     // rounded only on the outer two corners
            .background(if (half == Half.TOP) colors.cardTop else colors.cardBottom)
    ) {
        Box(
            Modifier
                .size(w, h)
                .offset(y = if (half == Half.TOP) 0.dp else -(h / 2))
        ) { DigitFace(digit, w, h, colors) }
    }
}
```
`halfShape(TOP) = RoundedCornerShape(topStart = R, topEnd = R, bottomStart = 0, bottomEnd = 0)`;
`halfShape(BOTTOM) = RoundedCornerShape(0, 0, R, R)`.

`DigitFace` draws the glyph centered in the **full** card rectangle so both halves line up perfectly.

### 6.7 The digit composable

```kotlin
@Composable
fun FlipDigit(
    value: Int,                  // 0..9, the target
    cardW: Dp, cardH: Dp,
    colors: FlipColors,
    spec: FlipSpec
) {
    var shown by remember { mutableIntStateOf(value) }   // the digit currently fully displayed
    var previous by remember { mutableIntStateOf(value) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (value == shown) return@LaunchedEffect
        previous = shown
        shown = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(spec.durationMs, easing = LinearEasing))
        // NOTE: LinearEasing here — the two-phase easing is applied inside the angle mapping,
        // not by the animation spec. Do not double-apply easing.
    }

    Box(Modifier.size(cardW, cardH)) {
        // back layers
        CardHalf(shown,    Half.TOP,    cardW, cardH, colors)                 // new top
        CardHalf(previous, Half.BOTTOM, cardW, cardH, colors, Modifier.align(Alignment.BottomCenter))
        CastShadow(progress.value, colors)                                    // on the lower static half

        val p = progress.value
        if (p < 0.5f) {
            val t = spec.easeIn(p * 2f)
            CardHalf(previous, Half.TOP, cardW, cardH, colors,
                Modifier.graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 1f)   // hinge = bottom edge of the top half
                    rotationX = -90f * t
                    cameraDistance = 16f * density
                })
            // + black scrim at 0.62f * t inside the same layer
        } else {
            val t = spec.easeOut((p - 0.5f) * 2f)
            CardHalf(shown, Half.BOTTOM, cardW, cardH, colors,
                Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0f)   // hinge = top edge of the bottom half
                    rotationX = 90f * (1f - t) + bounce(t, spec)
                    cameraDistance = 16f * density
                })
            // + black scrim at 0.62f * (1f - t)
        }
        Seam(colors, cardW, cardH)
    }
}
```

**Critical details:**
- `transformOrigin` is in **fractions of the composable being transformed**, and the composable here is the *half* (height `h/2`). So `(0.5f, 1f)` is the bottom edge of the top half = the seam. Correct. `(0.5f, 0f)` on the bottom half is also the seam. Correct.
- The scrim must be a child *inside* `CardHalf` (or drawn with `Modifier.drawWithContent { drawContent(); drawRect(Color.Black, alpha = a) }` on the same modifier chain **after** `graphicsLayer`) so it rotates with the flap.
- Do **not** put `AnimatedContent`, `Crossfade`, or `key(value)` around a digit. Reusing the same composable instance is what makes this cheap.

### 6.8 Preventing over-recomposition
- `FlipDigit` takes an `Int`, not a `State`. Hoist so that only the digits that changed get a new `value`.
- In `ClockScreen`, derive each digit separately:
  ```kotlin
  val parts by viewModel.time.collectAsStateWithLifecycle()
  val h1 by remember { derivedStateOf { parts.hour / 10 } }   // etc. for h2, m1, m2, s1, s2
  ```
  Better still: have `ClockViewModel` expose six separate `StateFlow<Int>`s (`hourTens`, `hourOnes`, …) built with `.map { }.distinctUntilChanged().stateIn(...)`. Then a minute change touches at most two composables.
- Never pass a lambda that captures the current time into `FlipDigit`.
- Never format strings during composition of a digit — pass the `Int` and let `DigitFace` do `remember(digit) { digit.toString() }` (or index a preallocated `arrayOf("0",…,"9")`).

### 6.9 The ticker (`ClockTicker.kt`)
```kotlin
fun tickerFlow(intervalMs: Long): Flow<Long> = flow {
    while (currentCoroutineContext().isActive) {
        val now = System.currentTimeMillis()
        emit(now)
        val next = ((now / intervalMs) + 1) * intervalMs
        delay((next - System.currentTimeMillis()).coerceAtLeast(1L))
    }
}
```
- `intervalMs = 1000` when seconds are shown, else `1000` anyway (keep 1 s; it lets minute rollover happen on the exact second and costs nothing). Do **not** poll faster than 1 Hz.
- Collect with `collectAsStateWithLifecycle` so it stops in `onStop`. On resume, snap all digits to the current time **without animating** (compare against `shown` and use `progress.snapTo(1f)` when the delta is more than one step or when returning from background — see §8.3).
- Also register a `BroadcastReceiver` for `ACTION_TIME_CHANGED` and `ACTION_TIMEZONE_CHANGED` and force an immediate non-animated resync.

### 6.10 Frame-rate target
- Request the highest refresh rate on API 30+ where it is free:
  ```kotlin
  if (Build.VERSION.SDK_INT >= 30) window.attributes = window.attributes.apply { preferredDisplayModeId = bestModeId }
  ```
  Simpler and preferred: `window.setFrameRate(...)` is API 34; instead just do nothing and let the system decide — **the animation must look right at 60 Hz**, and higher rates come free. Do not ship code that forces 120 Hz.
- Budget: at most **4 layers × 1 glyph** drawn per animating digit. With seconds on, at most 2 digits animate simultaneously (a rollover like 59→00 animates 2 seconds digits + possibly 2 minute digits + 2 hour digits = 6 at 23:59:59; that is the worst case and must still be smooth — test it by setting the device clock to 23:59:50).
SPECEOF
echo ok
---

## 7. SETTINGS

### 7.1 Stored keys (DataStore file `flip_settings`)

| key | type | default | values |
|---|---|---|---|
| `theme_mode` | String | `DARK` | `SYSTEM`, `LIGHT`, `DARK` |
| `use_24h` | Boolean | *system default* (`DateFormat.is24HourFormat(context)` on first run) | |
| `show_seconds` | Boolean | `false` | |
| `show_date` | Boolean | `true` | |
| `date_style` | String | `DAY_MONTH` | `DAY_MONTH` ("Fri, 24 July"), `SHORT` ("24/07/26"), `WEEKDAY_ONLY` ("Friday"), `ISO` ("2026-07-24") |
| `flip_speed` | String | `NORMAL` | `FAST`(340) `NORMAL`(520) `RELAXED`(700) |
| `flip_bounce` | Boolean | `true` | |
| `keep_screen_on` | Boolean | `true` | |
| `auto_dim` | Boolean | `true` | dims to 12% brightness after 30 s idle |
| `dim_delay_sec` | Int | `30` | 15/30/60/120 |
| `sound_enabled` | Boolean | `false` | |
| `show_battery` | Boolean | `false` | |
| `landscape_fullscreen` | Boolean | `true` | |

### 7.2 Settings overlay UI
- Tapping anywhere on the clock face toggles the overlay. It is **not** a new screen — it is an `AnimatedVisibility` layer above the clock so the clock keeps ticking behind it.
- Enter: `fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 6 }`. Exit: reverse, 180 ms. Behind it, apply `Modifier.blur(12.dp)` to the clock **only on API 31+** (`Modifier.blur` is a no-op below 31 — that's fine) plus a scrim at 0.55 alpha.
- Layout: a bottom sheet-ish rounded panel (28.dp top radius) that occupies at most 70% of the height, scrollable.
- Rows, in order:
  1. **Theme** — a 3-way segmented control: System / Light / Dark. (These are the "2 modes" the user asked for, plus system.)
  2. **24-hour clock** — switch
  3. **Show seconds** — switch (note under it: "Uses slightly more battery")
  4. **Show date** — switch, and when on, a 4-chip row for `date_style`
  5. **Flip speed** — 3-way segmented: Fast / Normal / Relaxed, with a live mini-preview card that flips a demo digit whenever the value changes
  6. **Bounce on landing** — switch
  7. **Keep screen on** — switch
  8. **Dim when idle** — switch + delay chips (15 s / 30 s / 1 m / 2 m)
  9. **Flip sound** — switch
  10. **Battery indicator** — switch
  11. **Fullscreen in landscape** — switch
  12. About: version + "No permissions. No internet. No data collected."
- A "Done" text button bottom-right, plus tap-outside-to-close, plus system back closes the overlay before it exits the app (`BackHandler(enabled = overlayVisible)`).
- While the overlay is open, **auto-dim is suspended** and the screen returns to full brightness.

---

## 8. BEHAVIOURS

### 8.1 Keep-screen-on & dimming (`util/Dimming.kt`)
```kotlin
// keep awake
DisposableEffect(keepOn) {
    val w = activity.window
    if (keepOn) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
}

// dim: change only THIS window's brightness, never system brightness (that needs WRITE_SETTINGS)
fun setWindowBrightness(activity: Activity, value: Float) {   // -1f = follow system, 0f..1f = override
    activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
}
```
- Idle detection: a `MutableStateFlow<Long>` of `lastInteractionMs`, updated by a `Modifier.pointerInput` on the root that records **any** down event (do not consume it). A `LaunchedEffect` compares against `dim_delay_sec`.
- Dim transition: animate `screenBrightness` from current to `0.12f` over **1200 ms** using an `Animatable`, so it fades instead of stepping. Restore to `-1f` (system) on any touch, over 200 ms.
- Additionally, when dimmed, fade the UI content alpha to `0.55f` and desaturate slightly — this protects OLED and looks intentional. Implement with `Modifier.graphicsLayer { alpha = contentAlpha }`.

### 8.2 Immersive fullscreen
```kotlin
val controller = WindowCompat.getInsetsController(window, window.decorView)
controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
if (landscape && landscapeFullscreen) controller.hide(WindowInsetsCompat.Type.systemBars())
else controller.show(WindowInsetsCompat.Type.systemBars())
```
Also `WindowCompat.setDecorFitsSystemWindows(window, false)` in `onCreate`, and use `Modifier.safeDrawingPadding()` on the root when bars are visible.

### 8.3 Background / foreground
- On `ON_STOP`: cancel the ticker (handled by `collectAsStateWithLifecycle`), clear `FLAG_KEEP_SCREEN_ON`, restore brightness.
- On `ON_START`: read the current time and **snap** every digit to it. Snapping rule inside `FlipDigit`:
  ```kotlin
  LaunchedEffect(value, snapToken) {
      if (value == shown) return@LaunchedEffect
      if (snapToken != lastToken) { previous = value; shown = value; progress.snapTo(1f); lastToken = snapToken; return@LaunchedEffect }
      ...normal animate...
  }
  ```
  `snapToken` is an `Int` incremented by the ViewModel on resume / time-change / settings-change-that-alters-digit-count.
- Never animate more than one step: if the digit jumped by more than 1 (e.g. app was backgrounded for 7 minutes), still perform exactly **one** flip from `previous` to `value` — never a rolling sequence.

### 8.4 12 h mode
- `hour12 = ((hour24 + 11) % 12) + 1` → 1..12.
- **Leading zero:** in 12 h mode the tens digit for hours 1–9 shows a blank card, not "0". Implement `DigitFace` with `digit = -1` meaning "blank" (draw the card with no glyph). Do not animate a blank↔digit transition differently — it flips normally, which looks right.
- `AmPmTag`: a small rounded rect, 10sp bold letter-spaced text, `accent` color at 0.9 alpha, placed at the baseline-right of the minutes group with 8.dp offset. It cross-fades (no flip) when AM↔PM changes.

### 8.5 Sound
- `SoundPool(maxStreams = 4, AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SONIFICATION))`.
- Play one click at the **start of phase 2** (that is when a real flap hits the stop), i.e. when `p` crosses 0.5. Detect with a `remember { mutableStateOf(false) }` latch inside `FlipDigit`, not by polling.
- Volume 0.35, plus a ±4% random rate variation per play so repeats don't sound robotic.
- If seconds are enabled, only play for the **seconds ones-digit** (otherwise it's a machine-gun).
- Respect ringer mode: skip playback when `AudioManager.ringerMode == RINGER_MODE_SILENT`.

### 8.6 Battery dot
Read the sticky broadcast once and register a receiver for `ACTION_BATTERY_CHANGED`:
```kotlin
val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
val pct   = if (level >= 0) level * 100 / scale else -1
val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
```
Throttle UI updates to at most once per 30 s. No permission required.

---

## 9. EDGE CASES

| # | Case | Behaviour |
|---|---|---|
| 1 | 23:59:59 → 00:00:00 | Up to 6 digits flip at once. Must stay smooth. Test explicitly by setting the device clock. |
| 2 | Timezone change / DST | `ACTION_TIMEZONE_CHANGED` → snap-resync, no animation. |
| 3 | Manual clock change backwards | Snap-resync. Never animate backwards. |
| 4 | Rotation | Activity is not recreated (`configChanges`); geometry recomputes; digits keep their current `shown` values; no flip is triggered by the resize. |
| 5 | Very small screen (< 320 dp width) | Seconds are force-hidden if `cardH` would fall below 64.dp; show a one-time toast-free inline note in settings ("Seconds hidden — screen too narrow"). |
| 6 | Font scale 2.0 | The digits are size-driven by geometry, not `sp` scaling — use `Dp`-derived font sizes via `with(LocalDensity.current) { cardH.toSp() * 0.72f }` so accessibility font scaling does not break the layout. The date line and settings text **do** honour `sp`. |
| 7 | Display cutout / notch in landscape | `windowLayoutInDisplayCutoutMode="shortEdges"` + `safeDrawingPadding` when bars are shown. |
| 8 | Multi-window | Geometry recomputes from `BoxWithConstraints`; nothing else changes. |
| 9 | Battery saver on | Android may scale animator durations to 0. Check `Settings.Global.ANIMATOR_DURATION_SCALE`; if it is 0, **skip animation entirely** and snap digits (do not run a 0-duration `animateTo` in a loop). |
| 10 | Talkback | The clock exposes a single `Modifier.semantics { contentDescription = "…"; liveRegion = LiveRegionMode.Polite }` on the whole face, updated once a minute, not once a second. Individual cards are `clearAndSetSemantics {}`. |
| 11 | Cold start flash | XML `windowBackground` matches the dark screen color; if the stored theme is LIGHT, the first frame may flash dark for ~1 frame — accept it, or read the theme synchronously from a tiny `SharedPreferences` mirror written alongside DataStore. Do the mirror; it's 10 lines. |
| 12 | Screenshot / recents thumbnail | Nothing special. |
| 13 | Process death while dimmed | Brightness override dies with the window. Fine. |

---

## 10. ACCEPTANCE CRITERIA

- [ ] Builds debug + release with zero warnings that reference our own code.
- [ ] The flip reads as a physical split-flap: acceleration on the way down, deceleration into the landing, correct shading on all four layers, visible seam.
- [ ] **No jank.** Run with `adb shell dumpsys gfxinfo dev.flip.clock framestats` while seconds are on for 60 s: 99th-percentile frame time under the vsync budget, zero "Missed Vsync" spikes attributable to the clock.
- [ ] Only the changing digits recompose (verify with the Layout Inspector recomposition counter: at :30 seconds with seconds on, exactly one digit shows an increment).
- [ ] Light and Dark both look deliberate — dark is the flagship, light must not look like a broken dark theme.
- [ ] Theme switch animates over ~400 ms with no flicker.
- [ ] 12/24 h, seconds, date on/off all reflow the layout correctly in portrait and landscape.
- [ ] Landscape hides system bars and fills the screen; swiping from an edge shows them transiently.
- [ ] Keep-screen-on works; auto-dim fades down after the configured delay and restores on touch.
- [ ] The clock is accurate: the flip **starts** within 60 ms of the true second boundary. Verify against a reference clock on screen next to it.
- [ ] App uses < 60 MB RAM and, with seconds off and screen on, has negligible CPU (< 1% average).
- [ ] APK (release, no split) under 4 MB.
- [ ] Zero runtime permissions requested.

---

## 11. TEST PLAN

**Unit**
1. `FlipGeometryTest` — given (availW, availH, groups) assert cardW/cardH within expected ranges; assert clamping at extremes (240 dp wide, 1600 dp wide).
2. `AngleMappingTest` — assert `angleAt(0f) == 0f`, `angleAt(0.4999f) ≈ -90f`, `angleAt(0.5f) ≈ +90f`, `angleAt(1f) == 0f`, monotonic within each phase.
3. `TimePartsTest` — 12 h conversion at 00:xx (→12 AM), 12:xx (→12 PM), 13:00 (→1 PM); blank-tens rule.
4. `TickerTest` — with a virtual clock, assert emissions land on second boundaries and drift stays under 5 ms over 1000 simulated ticks.

**Instrumented / visual**
5. Screenshot test (or manual): capture frames at p = 0.0, 0.25, 0.49, 0.51, 0.75, 1.0 for a 3→4 transition and eyeball each against §6.1.
6. Set the system clock to 23:59:50 and record the rollover at 240 fps (slow-mo) to inspect for stutter.
7. Leave running for 12 hours; confirm no drift, no leak (`adb shell dumpsys meminfo`), no crash.

---

## 12. BUILD ORDER

**Phase 0** — Project skeleton, theme, `MainActivity` edge-to-edge, a static (non-animated) clock face using plain `Text`. Verify layout at all sizes/orientations first. *Get the geometry right before you animate anything.*

**Phase 1** — `FlipCard` / `CardHalf` / `DigitFace` / `Seam`. Render a static card that looks correct (seam, radius, colors, glyph centering). Add a `@Preview` grid showing digits 0–9 in both themes.

**Phase 2** — `FlipDigit` animation, phase mapping, `transformOrigin`, `cameraDistance`. Test with a debug slider that scrubs `p` from 0→1 manually so you can inspect every intermediate angle. **Keep that debug scrubber behind a `BuildConfig.DEBUG` flag; do not delete it until Phase 6.**

**Phase 3** — Shading scrims + cast shadow + bounce.

**Phase 4** — `ClockTicker`, `ClockViewModel`, six independent digit flows, wiring to real time, separator, AM/PM, date line, snap-on-resume, time-change receiver.

**Phase 5** — Settings: DataStore, overlay UI, every toggle wired live.

**Phase 6** — Dimming, immersive landscape, sound, battery dot, semantics, ProGuard, icon (adaptive: `#0B0B0D` background, a white flip-card glyph showing "7" as foreground).

**Phase 7** — Run every line of §10 and fix.

---

## 13. PRE-ANSWERED QUESTIONS

1. **Should the flip use `AnimatedContent`?** No. It cannot express the four-layer model. Hand-rolled as specified.
2. **Should I use a `Canvas` and draw everything manually instead of composables?** Either works, but do it with composables + `graphicsLayer` as specified — `rotationX` with a proper camera is far easier to get right than hand-rolled 3D projection in a Canvas.
3. **Should digits use a downloaded font (e.g. a "flip clock" font)?** No. System default bold with tabular figures. No network, no font assets.
4. **Should the two halves each render half a glyph?** No — see §5.1. One full-size glyph, clipped twice.
5. **Sound assets?** Optional and off by default. If you cannot add a binary asset, synthesise (§1 item 9) or ship with sound permanently unavailable and hide the setting row. Do not fail the build over this.
6. **Alarm feature?** No. Out of scope.
7. **Widget?** No.
8. **What about 24 h vs 12 h default?** Read the system setting on first run, then it is user-controlled.
9. **Landscape lock?** No lock — `fullUser`, respects the user's rotation setting.
10. **Which module for the ViewModel?** Single-module app. `ClockViewModel` created with `viewModel { }` from `lifecycle-viewmodel-compose`, no DI framework.
11. **Do I need `WAKE_LOCK`?** No. `FLAG_KEEP_SCREEN_ON` needs no permission.
12. **Can I change system brightness?** No — that needs `WRITE_SETTINGS`. Only ever set `window.attributes.screenBrightness`.
13. **Minimum API?** 26. Do not raise it.
14. **Package name?** `dev.flip.clock`.
15. **What if the flip looks "floaty" or "cheap"?** In order: raise the phase-1 acceleration (steeper `EaseFallIn`), increase the peak cast-shadow alpha, reduce the total duration by ~15%, and verify the seam is visible. Those four fix 95% of "it doesn't feel physical".
