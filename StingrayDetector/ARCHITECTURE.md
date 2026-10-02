# IMSI-Catcher Detector NG — Architecture

Working name. GPLv3, continuation of the AIMSICD lineage (translations, status
icons and domain knowledge are reused, so the licence is inherited).

This is a ground-up rewrite targeting current Android (minSdk 31 / Android 12,
target 35 / Android 15). It keeps the *goal* of the original AIMSICD — warn the
user about likely cell-site simulators (IMSI-catchers / "StingRays") — but
every detection path is rebuilt on APIs that still work on a modern phone.

## Scope and honesty

An ordinary Android app cannot read the modem's RRC/NAS signalling, so it
cannot see most of what a dedicated receiver (EFF Rayhunter, an SDR) sees. We
state plainly what each tier can and cannot do, and we never imply certainty we
do not have. Detection is **passive**: the app only observes the radio state
its own phone already exposes. The app transmits nothing onto cellular bands.

### Why there is no "jamming" or "flooding back"

Striking back at an IMSI-catcher (feeding it fake IMSIs, flooding it) is out of
scope on purpose:

- It is not possible from a phone: the baseband is closed and exposes no API,
  root or not, to emit arbitrary signalling.
- Emitting on cellular bands is illegal in France (brouillage), enforced by the
  ANFR, even against an illegal device.
- IMSI-catchers are not always illegal: French state services are authorised
  under the 2015 intelligence law (CSI art. L851-6 ff.). A blind DoS exposes
  the user criminally.

The only lawful, realistic defence is passive detection plus device hardening.

## France-specific design notes

- MCC 208. Operators: Orange (MNC 01/02), SFR (10/13), Bouygues (20/88),
  Free (15/16).
- 2G shutdown is under way (Orange & SFR end 2026, Bouygues & Free ~2026-2028).
  On a French network soon without legitimate 2G, *any* fallback to GSM is a
  strong signal, and turning 2G off on the phone has little downside. The
  hardening advisor weights this heavily when MCC == 208.

## Tiers

| Tier | Needs | What it does |
|------|-------|--------------|
| 1  — standard app | location permission, foreground service | Observe cells via TelephonyCallback / getAllCellInfo, run heuristics, score, alert. This repo. |
| 1b — hardening advisor | nothing | Detect Android version + modem features; guide the user to native protections (2G off, require-encryption, Android 16 identifier/null-cipher notifications) and deep-link into Settings. |
| 2  — external receiver | separate hardware | Document pairing with a passive receiver (Rayhunter). Receive-only, legal. Out of app process. |
| 3  — root module (optional) | rooted Qualcomm device | Parse /dev/diag for real RRC/NAS heuristics (IMSI-request-then-auth-reject, GERAN redirect, SIB6/7 2G priority, EEA0 null cipher). Separate flavour. |

## Module layout (tier 1 + 1b, this scaffold)

```
model/    Unified radio model (RAT, CellSnapshot, verdicts, threat levels)
collect/  TelephonyCallback-based collector + CellInfo -> CellSnapshot mapper
data/     Room: observed cells, events; DAOs
detect/   DetectionEngine + Heuristic SPI + ScoreAggregator
  heuristics/  Individual heuristics (one file each, independently testable)
harden/   HardeningAdvisor: capability detection + Settings deep links
service/  Foreground monitoring service (type: location)
ui/       Compose: status, live cells, event log, hardening checklist
util/     helpers
```

## Detection engine

Each `Heuristic` consumes a `DetectionContext` (current serving cell, recent
history, device motion, operator/SIM facts, external-DB lookups) and may emit a
`HeuristicResult` with a weight and a decaying confidence. The `ScoreAggregator`
sums active results into a `ThreatLevel`. Nothing is a hard boolean: positives
decay over time and distance so the alert clears once the user leaves the area
(this was an explicit TODO in the old CellTracker).

### Heuristic catalogue (tier 1)

Modelled on the Rayhunter heuristics but expressed from what the phone can see:

1. **RatDowngrade** — NR/LTE -> GSM while the phone was immobile and LTE RSRP
   was healthy seconds before. Phone-side echo of Rayhunter's 2G-downgrade.
2. **UnknownStrongCell** — serving CI/NCI never seen in this area yet signal is
   saturated; or TAC/LAC inconsistent with neighbours.
3. **OperatorMismatch** — serving MCC/MNC differs from SIM operator with no
   roaming flag.
4. **EmptyNeighborList** — LTE serving cell reports no neighbours (rebuilt on
   getAllCellInfo, not the removed getNeighboringCellInfo).
5. **SuspiciousPhysicalParams** — LTE bandwidth 1.4/5 MHz (srsRAN/OpenBTS
   default), TA ~0 with saturated signal (emitter within tens of metres),
   unseen PCI/EARFCN.
6. **SignalAnomaly** — per-cell signal statistically off its own baseline,
   movement-filtered (finishes what SignalStrengthTracker started).
7. **RegistrationInstability** — repeated loss of registration / emergency-only.
8. **ExternalDbMismatch** — cell absent from OpenCelliD / beaconDB, or declared
   location far from GPS.

Weights are tunable constants in `detect/HeuristicWeights`; MCC 208 raises the
RatDowngrade weight because of the 2G shutdown.

## Build / CI

Kotlin, AGP 8, Gradle 8.9, version catalog, Room via KSP, Jetpack Compose,
WorkManager, osmdroid. CI will be GitHub Actions with the Android SDK. The
cloud container here has no Android SDK, so this scaffold is validated by
review and by the pure-JVM unit tests under `app/src/test`, not by a full
assemble.
