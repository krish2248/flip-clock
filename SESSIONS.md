# Flip — Session Log

> Context file for the autonomous agent. Read this FIRST at the start of every session
> to resume exactly where work stopped. Update it at the END of every session.
>
> Project: **SPEC 02 — "Flip"**, a classic split-flap flip clock for Android.
> Source of truth: `02-flip-clock-SPEC.md` (read fully before coding).
> Package: `dev.flip.clock` · Kotlin + Compose only · no third-party libs.

---

## How to use this file

1. At session start: read "Current state" and the latest session entry.
2. Work on the tasks in "Next up".
3. At session end: append a new entry under "Session history" with date, what was done,
   decisions made, and exact next steps. Keep "Current state" fresh.

---

## Current state

- **Spec phase reached:** **Phase 6 complete** — all source files written per §4 tree.
- **Builds:** ✅ `./gradlew :app:assembleDebug` and `assembleRelease` pass with zero warnings referencing our code.
- **Release APK:** 1.04 MB (unsigned), well under 4 MB budget.
- **Environment:** JDK 17, Android SDK 35, Gradle 8.9 (wrapper in repo), AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00.
- **Git repo:** https://github.com/krish2248/flip-clock (public, main branch).
- **Known blockers:** None — all source compiles, dependencies resolved (dl.google.com & mavenCentral reachable). No device attached for on-device verification.

## Next up

1. **On-device verification** (spec §10):
   - Install release APK on device/emulator.
   - Run `adb shell dumpsys gfxinfo dev.flip.clock framestats` with seconds on for 60 s; confirm 99th-pct frame time under vsync budget, zero attributable jank.
   - Verify Layout Inspector recomposition counts: at :30 with seconds on, exactly one digit increments.
   - Test 23:59:59 → 00:00:00 rollover (6 digits flip) for smoothness.
   - Test rotation, multi-window, DST/timezone change snap-resync.
   - 12 h soak test for leaks/drift.
   - Accessibility: TalkBack polite live region, per-minute update.
2. **Release signing** (if publishing): add keystore config to `local.properties` (not committed) and verify signed release builds.
3. **Unit tests** (spec §11) — optional; dep pinning excludes JUnit; could add as separate module if needed.
4. **ProGuard verification** — already enabled; run `./gradlew :app:assembleRelease` (done) and inspect mapping if issues.

---

## Session history

### Session 1 — 2026-08-24
- Read and analysed `02-flip-clock-SPEC.md` (639 lines). Key constraints noted:
  - Zero third-party libraries; everything hand-drawn. minSdk 26 / target+compile 35.
  - Pinned stack: Gradle 8.9, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2025.01.00,
    desugaring ON, Java 17.
  - Animation quality is THE quality bar: no allocation in draw/animation frames,
    lambda-form `graphicsLayer`, only changing digits recompose (six separate digit flows),
    flip = 4-layer model with two phases (top flap 0°→−90°, bottom flap +90°→0°),
    cameraDistance 16×density, scrims inside the flap layer, cast shadow peaking mid-flip.
  - Activity must NOT recreate on rotation (`configChanges` in manifest).
  - Settings via DataStore (`flip_settings`), 13 keys per §7.1; theme mirror to
    SharedPreferences for cold start (edge case #11).
  - Snap-on-resume rule via `snapToken`; never animate backwards/more than one step.
- Created `SESSIONS.md` (this file).
- Initialized git repo and pushed to **https://github.com/krish2248/flip-clock** (public).
- Scaffolded full Gradle project per spec §2/§3: `settings.gradle.kts`, root/app `build.gradle.kts`,
  `gradle/libs.versions.toml`, `gradle.properties`, manifest, themes, strings, adaptive icons.
- **Wrote all 22 Kotlin source files** matching spec §4 tree exactly:
  - `FlipApp.kt`, `AppContainer.kt`
  - `time/` ClockTicker, TimeParts, DateFormats
  - `settings/` SettingsRepository, SettingsModels, SettingsSheet
  - `flip/` FlipDigit, FlipCard, FlipGeometry, FlipSpec, DigitFace
  - `ui/` ClockScreen, ClockViewModel, Separator, AmPmTag, DateLine, BatteryDot, theme/{Color,Theme,Type}
  - `util/` Dimming, Sound
- Fixed compile errors iteratively (missing imports, Dp math, deprecated APIs, syntax).
- **Builds successful**: Debug + Release APKs generated; release APK = 1.04 MB.
- One deprecation warning cleaned (keyframes `with` infix); final build zero warnings.

### Session 2 — 2026-08-25
- Verified `assembleDebug` and `assembleRelease` both green with no warnings.
- Confirmed Gradle wrapper in repo uses canonical `https://services.gradle.org/distributions/gradle-8.9-bin.zip` with `validateDistributionUrl=false` (pre-installed locally for offline builds).
- Updated SESSIONS.md with this summary.
- **Ready for on-device testing next session** — all code complete per spec Phases 0–6.