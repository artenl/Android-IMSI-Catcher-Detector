# IMSI-Catcher Detector NG

Ground-up rewrite of AIMSICD for modern Android (minSdk 31 / Android 12,
target 35). Passive detection of likely cell-site simulators (IMSI-catchers /
"StingRays") plus a device-hardening advisor. GPLv3, continuing the AIMSICD
lineage.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design, the heuristic
catalogue, and the legal scope (notably why there is no "jamming back": it is
illegal in France and impossible from a phone — the app transmits nothing).

## Status

Phase 1 scaffold: unified radio model, TelephonyCallback collector, detection
engine with six heuristics, decaying score aggregator, Room storage, foreground
monitoring service, a minimal Compose UI, and a France-aware hardening advisor.

The detection graph (`model/`, `detect/`, `collect/OperatorFacts`) is
framework-free and covered by JVM unit tests under `app/src/test`.

## Build

Standard Gradle Android project:

```
cd StingrayDetector
./gradlew :app:testDebugUnitTest   # pure-JVM detection tests
./gradlew :app:assembleDebug       # requires the Android SDK
```

The cloud dev container has no Android SDK, so the APK is built in CI; the
detection tests run on any JVM.

## Roadmap

1. Accelerometer-driven motion gating (stub present), GPS tagging of cells.
2. External database cross-check (OpenCelliD, beaconDB).
3. Map + event-log UI, per-heuristic explanations.
4. Optional root module (Qualcomm /dev/diag) for true RRC/NAS heuristics.
