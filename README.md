# Speed Trio – Karoo extension

Custom Karoo data fields, each **double width / single height**, showing three values side by side.

| field | left | middle | right |
|---|---|---|---|
| **Speed Trio** | average speed (`MPH`) | current speed | ride distance (`MI`) |
| **Power Trio** | average power (`AVG`) | 3 s power | left/right balance (`L/R`) |
| **Gear Trio** | gear, e.g. `48-13` (`GEAR`) | heart rate | cadence (`CAD`) |

* Middle value: Avenir-style **bold italic**, digits 77 % of the full digit height, width scaling with it (`TrioSpec.MID_RATIO`; the full height is the field minus a 2 % margin top/bottom).
* Left / right values: **bold italic** (same face as the middle), digits **2/3** of the full digit height (`TrioSpec.SIDE_RATIO`, not affected by the 90 % middle), with a small regular-italic caption underneath.
* No header row, so the numbers get the whole field height.
* Two slanted separator lines (leaning 9 degrees like the italics, 75 % of the field height, centred top to bottom) sit between the values. They are experimental: `TrioSpec.SEPARATORS = false` removes them (and gives the space back to the side values).
* Speed Trio: average speed and distance show 2 decimals, current speed 1. Units and captions follow the
  rider's Karoo profile (mph + mi, or km/h + km).
* Power Trio: whole watts; balance is `left/right` percent (e.g. `49/51`) using the live power balance stream.
  `PowerTrioDataType.kt` shows where to swap in the 3 s or ride-average balance.
* Speed Trio also shows a **flashing red dot in the upper-left corner while any camera is recording**
  (1 s on / 1 s off, the fastest a Karoo view can update). It subscribes to the Insta360 extension's
  `recording_state` stream (`insta360cam::recording_state`, 1 while ANY saved camera is recording), so nothing
  changes in that app; it just needs to be installed. Other camera extensions that publish the same
  convention can be added to `CameraRecording.STREAMS`. The page-editor preview shows no dot.
* Gear Trio: gear is chainring teeth - cog teeth from the Karoo's shifting data; if tooth counts aren't
  configured it falls back to the gear numbers (e.g. `2-5`); `--` without shifting data.

## Layout rules

All text is horizontally condensed to 80 % of its natural width (`TrioSpec.NARROW`); heights are unchanged.

The middle value never shrinks and always keeps one decimal. Left and right values keep two decimals and stay
at the full 2/3 height only up to a field height of about **73 px** on a 480 px wide screen. Taller than that they
shrink together (down to 45 % of their 2/3 height, about **118 px**), and below that they are squeezed
horizontally so everything still fits. The unit captions keep one fixed size (13 % of field height) and sit
centred under their value.

All numbers live in `TrioSpec` in `TrioRenderer.kt`.

## Font

Avenir is a licensed font, so it is not bundled. The extension looks for these files in
`app/src/main/assets/fonts/` and uses them if present:

* `avenir-bold-oblique.ttf` (middle value, e.g. Avenir Heavy Oblique / Bold Oblique)
* `avenir-oblique.ttf` (side values, e.g. Avenir Book Oblique / Roman Oblique)

If they are missing it falls back to the bundled **Nunito Sans** Italic / Bold Italic (SIL OFL,
see `NunitoSans-OFL.txt`), a free geometric sans with Avenir-like proportions.

## Installing

Same routes as the Insta360 extension. Grab `karoo-speedtrio-<version>.apk` from the Releases page.

**Karoo 3: Hammerhead Companion app.** On your phone, open the Releases page, long-press the APK link and share it
with the Hammerhead Companion app (or download it first and share the file). Tap **Install** on the Karoo when the
prompt appears; the Karoo has to be on and on Wi-Fi.

**Karoo 2: adb.** With the Karoo in developer mode: `adb install -r karoo-speedtrio-<version>.apk`

There is no launcher icon or settings screen; the fields appear in the data field picker. Every build is signed with
the same committed keystore (`app/shared-debug.keystore`, Android's public debug credentials, the same one the
Insta360 extension uses), so updates install over the top.

Then on the Karoo: profile > edit page > add a data field > **Speed Trio**, **Power Trio** or **Gear Trio** >
place it at full (double) width.

## Building (GitHub Actions)

Built the same way as KarooInsta360Ctl: `.github/workflows/build.yml` runs `gradle --no-daemon assembleRelease` on
JDK 17 / Gradle 8.13 (AGP 8.13.2, Kotlin 1.9.24, compileSdk 34, minSdk 26).

* Every push to `main` (or a manual run) uploads the APK as a run artifact.
* Pushing a tag like `v1.0.0` also attaches it to a GitHub Release.
* karoo-ext is on GitHub Packages and needs auth even though it is public: add the classic PAT (`read:packages`)
  as the repository secret **`KAROO_EXT_TOKEN`**.

Local build: put `gpr.user=...` and `gpr.key=...` in `~/.gradle/gradle.properties`, then `gradle assembleRelease`.
The APK lands in `app/build/outputs/apk/release/`.

## Preview

`python3 tools/preview.py` renders `preview_speed.png`, `preview_power.png` and `preview_gear.png` using the same layout rules (needs Pillow).
