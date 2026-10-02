# IMSI-Catcher Detector NG

Ground-up rewrite of AIMSICD for modern Android (minSdk 31 / Android 12,
target 35). Passive detection of likely cell-site simulators (IMSI-catchers /
"StingRays") plus a device-hardening advisor. GPLv3, continuing the AIMSICD
lineage.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full design, the heuristic
catalogue, and the legal scope (notably why there is no "jamming back": it is
illegal in France and impossible from a phone — the app transmits nothing).

## Status

Working build. Unified radio model, TelephonyCallback collector, detection
engine with six heuristics, decaying score aggregator, Room storage,
accelerometer-based motion gating, GPS geo-tagging of cells/events, a
foreground monitoring service, a cyberdeck Compose UI with four alert levels,
a France-aware hardening advisor, and a panic/auto-protect response.

The detection graph (`model/`, `detect/`, `collect/OperatorFacts`) is
framework-free and covered by JVM unit tests under `app/src/test`.

### Panic / protection, and its limits

A screen lock does NOT stop an IMSI-catcher: the modem stays registered and
keeps answering identity requests. Only cutting the radio (airplane mode /
power off) stops ongoing collection, and only from that moment on. So the
`CUT_RADIO` action is the meaningful protective response; `LOCK` is kept only
as an anti-seizure measure and is labelled as such. A normally-installed app
cannot toggle airplane mode or power off the phone: without root, `CUT_RADIO`
raises a full-screen alarm, locks, and opens airplane settings. With root it
cuts the radio outright. The best *preventive* measure is permanent: disable
2G (see the hardening advisor).

## Build

Standard Gradle Android project:

```
cd StingrayDetector
./gradlew :app:testDebugUnitTest   # pure-JVM detection tests
./gradlew :app:assembleDebug       # requires the Android SDK
```

The cloud dev container has no Android SDK, so the APK is built in CI; the
detection tests run on any JVM.

## Release signing

`keystore.properties` (git-ignored) supplies the release key; without it,
release falls back to debug signing so CI still builds. To sign your own
release build:

```
keytool -genkeypair -v -keystore StingrayDetector/release.jks -alias imsicd \
  -keyalg RSA -keysize 2048 -validity 10000
# then create StingrayDetector/keystore.properties:
#   storeFile=release.jks
#   storePassword=...
#   keyAlias=imsicd
#   keyPassword=...
./gradlew :app:assembleRelease
```

## Roadmap

1. External database cross-check (OpenCelliD, beaconDB).
2. Map + event-log UI, per-heuristic explanations.
3. Optional root module (Qualcomm /dev/diag) for true RRC/NAS heuristics.
