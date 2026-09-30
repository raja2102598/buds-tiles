# Buds Tiles

[![Build](https://github.com/raja2102598/buds-tiles/actions/workflows/build.yml/badge.svg)](https://github.com/raja2102598/buds-tiles/actions/workflows/build.yml)

Quick Settings tiles for **noise cancelling** and **transparency** on OnePlus,
OPPO and realme earbuds, with no HeyMelody, no account and no root.

Samsung phones control Galaxy Buds straight from the Quick Settings panel, but
other brands need their companion app. Buds Tiles adds two tiles you can put
next to Wi-Fi and Bluetooth:

| Tile | Tap | Tap again |
|---|---|---|
| **Noise cancelling** | switches the earbuds to noise cancelling | switches it off |
| **Transparency** | switches the earbuds to transparency | switches it off |

The tiles show their state as a subtitle (`On`, `Off`, `Switching…`), which
appears when you use the large tile size in One UI and other launchers.

The app itself shows the current mode and battery, lets you switch modes, and
lets you choose the noise-cancelling strength the tile uses.

## Compatibility

| Earbuds | Status |
|---|---|
| OnePlus Buds 3 | Tested |
| OnePlus Buds 4 | Same protocol, [confirmed by QuickBuds](https://github.com/spizganed/QuickBuds) |
| Other OnePlus / OPPO / realme buds using HeyMelody | Likely; please report |

Android 8.0 or newer. Tested on a Samsung phone with One UI.

## Install

Download `buds-tiles-<version>.apk` from the
[latest release](https://github.com/raja2102598/buds-tiles/releases/latest)
and install it. Updates from later releases install over it.

1. Open **Buds Tiles**, allow the Bluetooth permission and choose your earbuds.
   They must already be paired in Android's Bluetooth settings.
2. Tap **Add Noise cancelling** and **Add Transparency** (Android 13+), or add
   the tiles manually from the Quick Settings edit screen.
3. If the earbuds can't be reached, force-stop HeyMelody. Only one app can hold
   the control channel at a time.

## Automation

The app has three shortcuts: **Noise cancelling**, **Transparency** and
**Noise control off**. Long-press the app icon to see them. Automation apps can
run them too:

- **Samsung Modes and Routines:** in a routine's *Then* actions, choose the
  app action / app shortcut option, pick Buds Tiles, then the shortcut.
- **Tasker, MacroDroid and similar apps:** start an activity with one of these
  intent actions (package `io.github.raja2102598.budstiles`):

  | Action | Mode |
  |---|---|
  | `io.github.raja2102598.budstiles.action.NOISE_CANCELLING` | Noise cancelling |
  | `io.github.raja2102598.budstiles.action.TRANSPARENCY` | Transparency |
  | `io.github.raja2102598.budstiles.action.OFF` | Off |

Shortcuts run in the background with no screen; a short message appears only
if the earbuds can't be reached.

## How it works

The earbuds speak a small binary protocol over a classic-Bluetooth RFCOMM
channel. Each tap opens the channel, runs a two-message handshake, sends the
set-mode command, waits for the earbuds to acknowledge it and disconnects, all
in about a second.

The full protocol, including the dead ends worth avoiding, is written up in
[docs/PROTOCOL.md](docs/PROTOCOL.md).

## Known limitations

- The tiles know the current mode from the last time the app talked to the
  earbuds. If you change the mode with a stem gesture, the tiles catch up on
  your next tap or when you open the app.
- Only noise control and battery are implemented. EQ, gestures and firmware
  updates still need HeyMelody.

## Building

Requires JDK 17 and the Android SDK (platform 34).

```bash
./gradlew :app:testDebugUnitTest   # protocol tests, run on the JVM
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Releases are built by GitHub Actions: pushing a tag such as `v1.0.1` runs the
tests, builds an APK signed with the key stored in the repository secrets
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_PASSWORD`) and publishes a release.

Code layout:

```
app/src/main/java/io/github/raja2102598/budstiles/
  protocol/   frame building and parsing, no Android dependencies
  bluetooth/  RFCOMM session and the async API used by the UI
  data/       settings
  tile/       Quick Settings tiles
  ui/         main screen
```

## Credits

The protocol was reverse-engineered by others before us:
[QuickBuds](https://github.com/spizganed/QuickBuds) (RFCOMM transport and
set-mode values, verified on OnePlus hardware),
[OppoPodsManager](https://github.com/Zhaoyi-ya/OppoPodsManager) and
[cracked-oneplus-buds](https://github.com/AasheeshLikePanner/cracked-oneplus-buds).
This app is an independent implementation; no code was copied.

Icons are from [Material Symbols](https://fonts.google.com/icons) (Apache 2.0).

Not affiliated with OnePlus, OPPO, realme or Samsung.

## License

[MIT](LICENSE)
