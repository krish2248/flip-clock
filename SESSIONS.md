# Flip — Session Log

> Context file for whoever works on this next (human or agent). Read **Current state**
> and the latest session entry first, then **Next up**. Update at the end of every session.
>
> Project: **Flip**, a split-flap flip clock for Android (Kotlin + Jetpack Compose).
> Original spec: `02-flip-clock-SPEC.md` — v2 deliberately goes beyond it (widgets,
> themes, more options) at the owner's request; where they conflict, this file wins.
> Package: `dev.flip.clock` · Repo: https://github.com/krish2248/flip-clock

---

## Current state (end of Session 3 — 2026-11-18)

- **Version:** 2.0.0 (versionCode 2). Full rewrite of UI + flip engine, widgets added.
- **Builds:** ✅ `:app:compileDebugKotlin`, ✅ `:app:assembleDebug`, ✅ `:app:testDebugUnitTest` (23/23 pass).
- **Not yet run this session:** `assembleRelease`, `bundleRelease`, `lintRelease`. Do these first.
- **Not yet verified on a device/emulator.** Nothing has been seen running yet — see Next up.
- **Toolchain:** JDK 17 (Temurin), Gradle **8.14.3**, AGP **8.13.0**, Kotlin **2.2.20**,
  Compose BOM **2025.10.01**, compileSdk/targetSdk **36** (Play requires 36 for new apps
  from Aug 2026), minSdk 26. `local.properties` → `sdk.dir=C:/Users/sonik/AppData/Local/Android/Sdk`
  (forward slashes — backslashes broke it).
- **Signing:** wired in `app/build.gradle.kts`, reads git-ignored `keystore.properties`
  (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). **No keystore created yet.**
  Without it, release builds come out unsigned.

## Next up (start here tonight)

1. **Release build + lint:** `./gradlew :app:lintRelease :app:assembleRelease :app:bundleRelease`
   and fix anything lint flags (abortOnError is on).
2. **Create upload keystore** (keep it OUTSIDE the repo, back it up — losing it means a
   Play support reset):
   `keytool -genkeypair -v -keystore ../flip-upload.jks -alias flip -keyalg RSA -keysize 2048 -validity 10000`
   then write `keystore.properties` in the repo root pointing at it. Rebuild `bundleRelease`
   → `app/build/outputs/bundle/release/app-release.aab` is what goes to Play.
3. **See it running.** No AVD exists. Installing a system image was blocked by the
   permission classifier (it needed `sdkmanager` + license acceptance). Either the owner
   runs `! sdkmanager "system-images;android-36;google_apis;x86_64"` and creates an AVD,
   or install the debug APK on a phone via `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
   Then check: flip animation look (shading, seam, bounce, direction of rotation),
   settings sheet open/drag/predictive back, live clock preview transform, every theme,
   all 4 widgets (sizes, colours, Glass, Material You), dimming + wake-tap, night mode.
4. **Store listing:** write `store/listing.md` (title, short + full description),
   `PRIVACY.md` in repo root (the in-app link points to
   `github.com/krish2248/flip-clock/blob/main/PRIVACY.md` — it must exist before release),
   take phone screenshots for Play (needs step 3).
5. Optional polish: Roborazzi/Compose screenshot tests; README.md for the repo.

---

## Architecture (v2)

```
dev/flip/clock/
├── FlipApp.kt / AppContainer.kt   app singletons; mirror sync; widgets refresh on theme change
├── MainActivity.kt                edge-to-edge; window bg from saved theme; holds 1st frame
│                                  until DataStore loads (max 700 ms) → no layout jump
├── time/
│   ├── ClockTicker.kt             wall-clock-aligned 1 Hz ticker (injectable clock for tests)
│   ├── TimeParts.kt               6 digit slots, 12/24 h, leading zero, DigitState packing
│   ├── DateFormats.kt             7 date styles via locale "best pattern" skeletons
│   └── SystemSignals.kt           battery flow, next-alarm flow, AlarmFormat
├── flip/
│   ├── FlipSpec.kt                durations, easings, FlipMath (angles, scrims, shadows)
│   ├── FlipGeometry.kt            card sizing incl. user scale + corner factor
│   ├── DigitFace.kt               GlyphCache: 10 glyphs measured once, ink-centred on seam
│   ├── FlipCard.kt                CardPainter: prebuilt paths/brushes, seam, hinge notches
│   └── FlipDigit.kt               5-layer flip; all animated reads in draw/layer phase
├── settings/
│   ├── SettingsModels.kt          FlipSettings + all option enums
│   ├── SettingsRepository.kt      DataStore, atomic update{}, v1 key migration, sync mirror
│   ├── SheetState.kt              one spring progress drives sheet AND clock preview
│   ├── SettingsComponents.kt      groups, segmented control, chips, theme/font pickers
│   └── SettingsSheet.kt           LazyColumn sheet, drag/nested-scroll/predictive back
├── ui/
│   ├── ClockScreen.kt             root: dim, keep-awake, bars, burn-in shift, live preview
│   ├── ClockViewModel.kt          resync token + ticker restart; per-digit StateFlows
│   ├── ClockFace.kt               digit row; each card reads only its own State
│   ├── Separator.kt / AmPmTag.kt / InfoLine.kt / BatteryBadge.kt
│   └── theme/ Palettes.kt (11 palettes as ARGB ints), DynamicPalette.kt (Material You),
│              Color.kt, Theme.kt (animated colours + derived M3 scheme), Type.kt (6 fonts)
├── util/ Dimming.kt, SystemBars.kt, Sound.kt (synthesised clack)
└── widget/
    ├── WidgetModels.kt            4 kinds, 13 widget themes, per-widget prefs store
    ├── WidgetRenderer.kt          RemoteViews + TextClock; day/night CSL on API 31+
    ├── FlipWidgetProvider.kt      providers + WidgetUpdater (updateAll, requestPin)
    └── WidgetConfigActivity.kt    Compose config screen with live preview
```

Resources: widget layouts + provider XML are **generated** by `tools/widgets.py`
(`python tools/widgets.py app/src/main/res`). Widget preview PNGs and `store/` graphics are
rendered by `tools/images.py` (`python tools/images.py app/src/main/res store`, needs Pillow).
Re-run the scripts instead of hand-editing generated files.

## Key decisions (and why)

- **DigitState packs value + resync token into one Int** → a resume/time-change can never
  deliver the new token with a stale digit, so digits snap instead of animating wrongly.
- **Resync restarts the ticker** (`flatMapLatest`) and re-reads `ZoneId` + system 12/24 h —
  v1 captured the zone once, so a timezone change showed the wrong time.
- **Flip layers never swap composables at p=0.5**; each layer decides what to draw in its
  draw lambda → zero recompositions during a flip.
- **Glyph colour applied at draw time**, layouts cached per font+size → theme animations
  no longer re-measure text every frame (v1 did).
- **Settings sheet = live preview:** the real clock scales/translates (graphicsLayer only,
  no relayout) into the free space while the sheet opens. No blur, so colours are judged true.
- **Widgets use TextClock**, never scheduled updates → always correct, zero battery cost.
- **Dimming actually dims now** (v1 animated a value it never applied). Window brightness
  stepped at 25 Hz (each step is a WindowManager IPC), plus a black overlay for any palette.
- **Sound** plays on landing of minute-ones (or seconds-ones when seconds shown); v1 was
  silent whenever seconds were hidden.
- Still zero permissions, zero network, no third-party libraries beyond AndroidX.

## Options (25+)
Theme (Auto, Material You, Midnight, Paper, AMOLED, Retro, Ocean, Forest, Rose, Sand, Neon) ·
Font (6) · Card shape (3) · Clock size slider · Separator (Dots/Blink/Bars/None) ·
Hour format (System/12/24) · Leading zero · Seconds · Flip speed · Bounce · Sound ·
Date on/off + 7 styles · Next alarm · Battery · Keep screen on (Always/Charging/Never) ·
Dim when idle + delay (5 choices) + level (3) · Night mode (Off/When dim/Always) ·
Burn-in shift · Hide system bars (Never/Landscape/Always) · Reset to defaults.
Widgets: Flip Clock, Flip Clock & Date (+next alarm), Flip Calendar, Flip Mini — each with
theme (13 incl. Same-as-app & Glass), background opacity, hour format, date toggle;
pinnable from inside the app.

---

## Session history

### Session 1 — 2026-08-24
- Scaffolded project per spec, wrote all 22 source files, debug + release builds green.

### Session 2 — 2026-08-25
- Verified builds, wrapper config. Marked ready for on-device testing.

### Session 3 — 2026-11-18
- Owner asked for: smooth polished UI, perfect animations, correct info, many more
  options, home-screen widgets, Play-Store-ready structure, push to GitHub.
- Audited v1; bugs found & fixed: dimming no-op; stale timezone; resume could snap-then-
  animate a stale digit; no flip sound with seconds hidden; date line popped in after
  first tick (layout jump); unlocalised date order; status-bar icons invisible on light
  theme; theme wash re-measured text every frame; touch-to-wake never fired (sibling
  hit-testing); themed (monochrome) icon would render blank.
- Rewrote flip engine, settings, screen; added palettes, fonts, widgets, config activity,
  backup rules, Android 12 splash, new adaptive + themed icon, store icon + feature graphic.
- Upgraded toolchain to SDK 36 / AGP 8.13 / Kotlin 2.2.20 / Compose 2025.10.01.
- Added 23 JVM unit tests (TimeParts, FlipMath, FlipGeometry, Ticker, DateFormats) — all pass.
- Git: local folder was a zip download; re-attached to `origin/main` history and pushed.
- Stopped before release build/lint/signing and before any on-device run (see Next up).
