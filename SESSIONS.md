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

- **Spec phase reached:** not started (about to begin Phase 0)
- **Builds?** No project scaffold yet — directory contains only this file + the spec.
- **Known blockers:** none yet (environment check pending).

## Next up

1. Verify environment: JDK 17, Android SDK (platform 35, build-tools), Gradle 8.9.
2. Scaffold project per spec §2 / §4: `settings.gradle.kts`, `build.gradle.kts`
   (root + app), `gradle/libs.versions.toml`, wrapper, manifest, themes.
3. Phase 0 skeleton → static clock face with correct geometry (`FlipGeometry.kt`).
4. Then follow spec §12 build order (Phases 1–7).

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
    SharedPreferences for cold-start (edge case #11).
  - Snap-on-resume rule via `snapToken`; never animate backwards/more than one step.
- Created `SESSIONS.md` (this file).
- **Next:** environment check, then Phase 0 scaffold.
