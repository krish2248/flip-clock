# Flip — Session Log

> Context file for whoever works on this next (human or agent). Read **Current state**
> and the latest session entry first, then **Next up**. Update at the end of every session.
>
> Project: **Flip**, a split-flap flip clock for Android (Kotlin + Jetpack Compose).
> Original spec: `02-flip-clock-SPEC.md` — v2 deliberately goes beyond it (widgets,
> themes, more options) at the owner's request; where they conflict, this file wins.
> Package: `dev.flip.clock` · Repo: https://github.com/krish2248/flip-clock

---

## Current state (end of Session 5 — 2026-11-18)

- **Version:** 2.1.0 (versionCode 3). Adds Timer + Stopwatch modes, floating navigation,
  swipe between modes, screen saver (DreamService), launcher shortcuts.
- **Builds:** ✅ unit tests (35), ✅ `lintRelease`, ✅ `assembleRelease`, ✅ `bundleRelease`
  (see Session 5 entry for exact results).
- **Seen rendered:** ✅ via Robolectric screenshots (`store/screens/*.png`, real MainActivity,
  native graphics). ❌ Still never run on a real device/emulator — animation feel, sound,
  widgets, dream and shortcuts are unverified on hardware.
- **Toolchain:** JDK 17 (Temurin), Gradle **8.14.3**, AGP **8.13.0**, Kotlin **2.2.20**,
  Compose BOM **2025.10.01**, compileSdk/targetSdk **36**, minSdk 26, Robolectric **4.16**
  (tests only). `local.properties` → `sdk.dir=C\:/Users/sonik/AppData/Local/Android/Sdk`
  (escaped colon; lint's PropertyEscape check requires it).
- **Signing:** wired in `app/build.gradle.kts`, reads git-ignored `keystore.properties`.
  **No keystore created yet** → release builds are unsigned.

## Next up

1. **Create upload keystore** (owner should do this so passwords stay with them; keep it
   OUTSIDE the repo and back it up):
   `keytool -genkeypair -v -keystore ../flip-upload.jks -alias flip -keyalg RSA -keysize 2048 -validity 10000`
   then `keystore.properties` in the repo root. Rebuild `bundleRelease`.
2. **Run on a phone:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Check:
   flip feel, timer chime (alarm stream), swipe between modes, controls auto-hide, settings
   sheet + live preview in all 3 modes, widgets, screen saver (Settings → Display → Screen
   saver → Flip), long-press icon shortcuts, dimming + wake-tap, night mode, landscape.
3. **Play screenshots:** `./gradlew :app:testDebugUnitTest -Pscreenshots --tests "*ScreenshotTest*"`
   regenerates `store/screens/`. Real-device screenshots are better for the listing (the
   Robolectric ones have no status bar).
4. **Known limitation — background timer:** zero permissions means no notification and no
   exact alarm. The chime plays while the process is alive (screen is kept on while a timer
   runs); if Android freezes the app, the timer shows "Time's up" on return (silent if more
   than 60 s late). If the owner wants a reliable background alert: POST_NOTIFICATIONS +
   a foreground service or `setAlarmClock` (needs SCHEDULE_EXACT_ALARM) — this breaks the
   "no permissions" store claim, so it's the owner's call.
5. Optional: landscape screenshot test; tablet layout pass.

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
├── tools/
│   ├── ToolStates.kt              AppMode; Stopwatch/Timer as pure immutable data (elapsedRealtime)
│   └── ToolsController.kt         process singleton: mode, timer, stopwatch, chime; persisted
│                                  to prefs "flip_tools" (reset on reboot via BOOT_COUNT)
├── dream/FlipDreamService.kt      screen saver; supplies Lifecycle/ViewModel/SavedState owners
├── ui/Chrome.kt                   ModeSwitcher pill, floating gear, ControlButton, RepeatButton
├── ui/ToolPanes.kt                TimerPane / StopwatchPane on the same ClockFace + flip cards
├── util/ Dimming.kt, SystemBars.kt, Sound.kt (synthesised clack), Chime.kt (timer bell)
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

- **Navigation (v2.1):** tap = show/hide floating chrome (mode pill + gear), auto-hides
  after 5 s; swipe ←/→ = change mode; settings moved behind the gear (tap no longer opens
  it). Transport buttons hide with the chrome only while running, so idle screens stay usable.
- **Timer/stopwatch never use wall-clock time** — elapsedRealtime only, so timezone / clock
  changes can't corrupt them. Display ticks are scheduled to the exact second boundary
  (no per-frame polling); only the stopwatch hundredths redraw per frame.
- **ToolsController lives in AppContainer, not a ViewModel**, so a running timer survives
  rotation, mode switches and leaving the screen; state is persisted for process death.
- **Live preview extracted** into `rememberLivePreview()` so all three modes glide/shrink
  beside the settings sheet.

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
- Still zero permissions, zero network, no third-party libraries beyond AndroidX
  (Robolectric is test-only).

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

### Session 4 — 2026-11-18 (evening)
- No network this session. Release APK + AAB built with lint-vital skipped.
- Wrote `PRIVACY.md` and `store/listing.md`.

### Session 5 — 2026-11-18 (night)
- Owner asked: review everything, add features, make it aesthetic / easy to navigate /
  optimized, commit + push.
- First real lint run: 3 errors fixed (NewApi on windowLayoutInDisplayCutoutMode,
  PropertyEscape in local.properties, SuspiciousIndentation) + perf warnings (lambda
  `offset` in SegmentedControl, KTX `edit`/`toUri`/`toDrawable`).
- Added Timer, Stopwatch, mode switcher + swipe navigation, screen saver, launcher
  shortcuts, timer chime, Robolectric screenshot test, README, 12 new unit tests.
- Fixed pre-existing layout bug: clock face filled the full width, so burn-in shift pushed
  the first card off-screen → 18 dp side margin.
- Version 2.1.0 (code 3).
