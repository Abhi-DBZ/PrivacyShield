# Privacy Shield

**A software privacy screen for any Android phone.** Privacy Shield watches for the moments your
screen could be seen by someone else (the phone turns away from you, or a second face appears)
and hides the screen right away.

> [!IMPORTANT]
> **This is not a hardware privacy display.**
> Some new phones, such as Samsung's Galaxy S26 Ultra with *Privacy Display*, have special screen
> hardware that sends light only straight ahead, so people at your side physically see a dark
> screen while you see it normally.
> **No app can do that.** An app can change *what* the pixels show, but not *which direction*
> their light goes. Privacy Shield takes the software route instead: it **detects the risk and
> hides the screen**. It works on almost any Android phone, but it is not the same thing.

| | Hardware privacy display (e.g. Samsung) | Privacy Shield (this app) |
|---|---|---|
| How it works | Screen hardware narrows the viewing angle | Sensors and camera notice risk, then hide the screen |
| Side viewers see | A dark screen, all the time | Your screen, until the app reacts (usually well under a second) |
| You see | Your screen, normally | Your screen, normally (or a reading strip, if you choose) |
| Needs | A phone with that screen | Any phone with Android 8.0 or newer |
| Battery | None extra | Small; more with the camera features on |

## Features

- **Tilt guard**: remembers how you hold the phone and hides the screen when it turns away from
  you, for example shown to the person next to you or laid flat on a table. Uses the phone's
  motion sensors. You set the angle (15–60°).
- **Peek alert** (optional): uses the front camera to count faces. When a **second face** looks at
  the screen, it hides instantly and gives two short buzzes.
- **Hide when you look away** (optional): hides after 3 seconds with no face in front of the screen.
- **Only in chosen apps**: protect just your banking, chat or gallery apps. Everywhere else the
  shield and camera rest, which saves battery.
- **Reading window**: darkens everything except a strip you read through. Drag the **≡** handle on
  the right edge to move it.
- **Fake screen**: when it hides, show black, a blur, a **clock** or **a picture you choose**, so the
  person looking doesn't notice anything.
- **Rest in landscape**: videos and games in landscape don't set off the tilt guard.
- **Dim filter**: an always-on dark layer that makes the screen harder to read from a distance.
- Quick Settings tile, notification controls, light / dark / pure-black themes and Material You colours.

## Download and install

1. Open **[Releases](https://github.com/Abhi-DBZ/PrivacyShield/releases/latest)** on your phone and download:
   - `PrivacyShield-vX.Y.Z.apk` for almost every phone (64-bit)
   - `…-older-phones.apk` for older 32-bit phones
   - `…-emulator.apk` only for the Android emulator on a PC
2. Open the file. If asked, allow your browser or file manager to **install unknown apps**.
3. Google Play Protect may warn because the app isn't from the Play Store. Tap
   **More details → Install anyway**.

To update, install the newer APK over the old one. Your settings stay.

## First start

1. Tap **Turn on**. Android asks you to allow **Display over other apps**. Find Privacy Shield
   and switch it on. This lets the app cover the screen on top of other apps.
2. Allow **notifications**, so you can see that the shield is on and turn it off from there.
3. Hold the phone the way you normally do when you turn the shield on. That becomes "your position".

Optional, when you switch on these features:
- **Peek alert / Hide when you look away** ask for the **camera**.
- **Only in chosen apps** needs **Usage access** (Settings → Usage access → Privacy Shield). If
  that switch is greyed out, open App info → **⋮** (top right) → **Allow restricted settings**, then try again.

**Quick Settings tile:** pull down Quick Settings, tap the pencil (edit) and drag **Privacy Shield** in.

## Using it

- **Screen hid by mistake?** Double-tap the hidden screen to show it for 30 seconds.
- **Changed how you hold the phone** (lying down, for example)? Tap **This is how I hold it** on
  the hidden screen, long-press the hidden screen, or tap **Recenter** in the notification.
- The tilt guard also resets by itself each time the screen turns on, when you open a chosen app,
  and when you turn back from landscape.

## Privacy

- **No internet permission.** The app cannot send anything anywhere.
- Camera frames are checked **on the phone** (Google ML Kit's on-device face detection, about 3
  frames a second) and dropped straight away. Nothing is saved; there are no photos.
- Android shows its green camera dot while a camera feature is on.
- Usage access is only used to see **which app** is open, never what is in it.

| Permission | Why |
|---|---|
| Display over other apps | Draws the shield, dim filter and reading window |
| Camera (optional) | Peek alert and look-away |
| Usage access (optional) | Only in chosen apps |
| Notifications | The "shield is on" notification with Recenter / Turn off |
| Foreground service, vibrate | Keeps the shield running; buzz on peek |

## Limits

- It reacts to risk; it can't keep the screen dark from the side the way hardware can.
- With the camera features off, it can't notice someone looking over your shoulder while you hold
  the phone normally.
- It can't stop someone taking a photo of your screen.
- Android doesn't let apps cover the notification shade, the lock screen or some system screens.
- It doesn't start by itself after the phone restarts.
- The reading window can be at most 78% dark outside the strip. Anything darker would stop your
  taps from reaching the app underneath (an Android rule).

## Build from source

Requires JDK 17 and the Android SDK (platform 37).

```
./gradlew :app:assembleRelease
```

APKs land in `app/build/outputs/apk/release/`, one per CPU type. Without a `keystore/` folder the
build is signed with the debug key, so it can't update an installed release.

Kotlin, Jetpack Compose (Material 3), CameraX and Google ML Kit face detection. Libraries and their
licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
