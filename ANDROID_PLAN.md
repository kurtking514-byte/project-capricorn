# Capri — Android Launcher Plan

A fan-made Android home-screen launcher built around **Capri (Project Capricorn)**, the personality construct from *Cyberpunk: Edgerunners 2*. She projects herself onto your phone, roams the home screen, and runs your phone from a chatbox, gestures and voice — fully offline, free, and light on RAM.

> Fan project. Not affiliated with CD Projekt or Studio Trigger. All rights to the character belong to their owners.

---

## Goals

- **Personal:** Capri only "projects" for her linked user — she feels like *your* phone's construct.
- **Offline & free:** no account, no internet, no paid API. Everything runs on-device.
- **Light:** launcher idles at ~30–60 MB RAM. The AI loads only when you chat, then unloads.
- **Cool and convenient:** minimal neon home screen, but every common action is one tap, gesture or sentence away.
- **Shareable:** open source on GitHub; anyone can load their own Capri art.

## Target device

- Primary test phone: **Realme 12 Pro+ 5G** (Snapdragon 7s Gen 2, 12 GB RAM, Realme UI).
- Minimum: Android 9+ (needed for accessibility screenshot). Recommended for AI: 6 GB+ RAM.
- Virtual/"expandable" RAM doesn't count for AI — it's slow storage swap.

## Tech stack

| Part | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| Launcher | `HOME` intent category activity |
| Capri animation | Sprite frames loaded from a skin folder, drawn on a Compose Canvas |
| Screenshot / lock screen | `AccessibilityService` (`GLOBAL_ACTION_TAKE_SCREENSHOT`, `GLOBAL_ACTION_LOCK_SCREEN`) |
| Alarms / timers | `AlarmClock` intents (system clock app) |
| Camera | Camera intent |
| Notifications | `NotificationListenerService` |
| Music controls | `MediaSessionManager` (via notification access) |
| Widgets | `AppWidgetHost` |
| Speech-to-text | **Vosk** (offline, ~50 MB model) |
| Wake word | Vosk keyword spotting ("Hey Capri") |
| Offline AI | **llama.cpp** (or MediaPipe LLM Inference) with **Qwen3 1.7B / 4B** or **Gemma 3 4B** (quantized) |
| Storage | DataStore (settings) + Room (usage stats, vault, suggestions) |
| Build | Android Studio, Gradle, USB debugging on the phone |

---

## Features

### Capri herself
- Projected on the home screen: idles, walks, sits, sleeps, glitches between poses.
- Reacts to taps, pokes and "petting" (long stroke).
- **Psyches (moods):** she's many merged minds, so different personalities show through:
  - calm/efficient (morning), sarcastic/chaotic (night), protective (low battery or too much screen time).
  - short glitch effect when a psyche switches.
- Reacts to phone state: charging, low battery, notifications, time of day.
- **Biochip motif:** unlock/boot shows "Biochip linked. Capri online."; AI loading shows "Merging psyches… ▓▓▓░░"; settings screen styled as "construct config".

### Gestures on Capri (all remappable)
| Gesture | Default |
|---|---|
| Double-tap | Screenshot |
| Long-press | Push-to-talk voice |
| Swipe up | Flashlight |
| Swipe down | Silent mode |
| Drag to screen edge | Camera |
| Triple-tap | Lock screen |

### Chatbox / command bar
- Rule-based offline parser (instant, ~0 MB): "open spotify", "alarm 7:30", "timer 10 min", "photo", "screenshot", "call mom", "wifi off", "pause", "next song".
- Live search as you type: apps, contacts, settings.
- Capri replies in character, with lines that vary by psyche.
- Anything the parser doesn't understand goes to the offline AI (if enabled).

### Home screen
- Minimal: big clock, Capri, chatbox. Dock of 4–5 favorites.
- Swipe up: app drawer (A–Z, search, most-used at top).
- Swipe right: **Capri's room**: weather, battery as her "energy bar", reminders, notes, stats.
- Widgets: weather, music player, calendar.
- Theme: black base, pink + green neon, blue highlights, glitchy terminal-style text. Time-of-day wallpaper shifts.

### Convenient extras
- **Smart suggestions:** she learns your habits on-device ("you always open Spotify at 8. Want it?").
- **Notification bubbles:** Capri drags notifications to you; tap to open.
- **Vault:** hidden apps in her "Arasaka vault", opened with a secret gesture.
- **Focus mode:** she locks down distracting apps for X minutes and stands guard.
- **Music controls:** voice/chat commands plus a small player she holds.
- **Calls & messages:** "call mom", read out who just texted.
- **Backup:** export/import your whole Capri setup (gestures, vault list, psyches, skin choice).

### Voice
- **Voice switch** (on/off) for push-to-talk voice commands.
- **Separate "Hey Capri" switch** for always-listening wake word (uses more battery). Option: only while charging / only while screen is on.

### Offline AI (optional)
- Off by default; downloaded as a separate model file from settings.
- Small model (1.7B) for quick replies, bigger model (4B) for real conversations.
- Personality prompt makes her speak as Capri, with psyche-aware tone.
- Also parses messy commands ("wake me up before my 8am class" → alarm 7:00).
- Loads on demand, unloads after idle, so day-to-day RAM stays low.
- Expected on Realme 12 Pro+: ~5–10 words/sec (4B), ~10–20 (1.7B).

### Settings
- Gestures, voice switches, psyches, theme, skin, dock, hidden apps, AI model choice, battery behavior.

---

## Art and skins (copyright-safe)

- **No official artwork is bundled in the repo.**
- The app loads Capri's frames from a **skin folder** (`idle`, `walk`, `sleep`, `glitch`, `react`, …) plus a `skin.json` describing frame counts and timing.
- Users load whatever art they want on their own phone.
- Ships with an **original stand-in skin** in her colors so it works out of the box.
- Fan art can be included only with the artist's permission and credit.
- Skins are swappable, so the community can make their own.

## Practical concerns

- **Battery:** Capri slows to a low-FPS idle or sleeps when not interacting; animation pauses when the screen is off.
- **Realme UI background killing:** first-run guide walks the user through disabling battery optimization / allowing auto-launch for Capri.
- **Permissions onboarding:** Capri explains each one in character before asking: Accessibility, Notification access, Camera, Microphone, Contacts, Phone.
- **Privacy:** nothing leaves the device. No analytics.

---

## Roadmap

| Phase | Deliverable | Rough time |
|---|---|---|
| 0. Setup | Android Studio installed, phone in USB debugging, empty app runs on phone | 1 day |
| 1. Basic launcher | Set-as-home, clock, dock, app drawer + search. **Usable daily.** | 3–5 days |
| 2. Capri appears | Skin loader, stand-in skin, idle/walk animation, tap reactions | ~1 week |
| 3. Commands & gestures | Chatbox parser, open apps, alarms, timers, photo, screenshot, gesture settings | ~1 week |
| 4. Her world | Neon theme, psyches, biochip animations, permission onboarding, Realme whitelist guide | ~1 week |
| 5. Convenient extras | Suggestions, notification bubbles, vault, focus mode, widgets, music, calls/texts, backup | 1–2 weeks |
| 6. Voice | Vosk speech, voice switch, "Hey Capri" switch | ~1 week |
| 7. Offline AI | llama.cpp + Qwen3/Gemma, Capri personality, on-demand loading | 1–2 weeks |
| 8. GitHub release | README, fan disclaimer, MIT license, skin guide, APK in Releases | 2–3 days |

**Total:** ~6–10 weeks at a few hours a day, several days a week. Daily-usable after phases 1–2.

## Progress

- **2026-11-11:** Phase 1 code written (not yet compiled or run, since Android Studio isn't installed yet): launcher activity, neon clock, dock (long-press → pin, max 5), swipe-up drawer with search / frequent / A–Z, pull-down or Back to close, boot sequence. Gradle wrapper JAR and `gradlew` scripts are not in the repo yet; Android Studio uses `gradle-wrapper.properties` directly, and `gradle wrapper` can add them later.

## Phase 0 checklist (do this first)

1. Install **Android Studio** from developer.android.com (includes SDK + Java).
2. On the phone: Settings → About device → Version → tap **Build number** 7 times → enable **USB debugging** in Developer options.
3. Plug phone into laptop, accept the debugging prompt on the phone.
4. Create the Kotlin + Compose project in this folder and run the first build on the phone.

## GitHub release notes

- License: MIT.
- README: what it is, screenshots (with stand-in skin), install via APK from Releases, skin guide, permissions explained, fan-project disclaimer.
- Check CD Projekt's fan content guidelines before release.

## Open questions

- How Capri talks in the show (tone, catchphrases) — needed for her reply lines and AI personality prompt.

## Naming (decided)

- **Repo:** `project-capricorn` (her Arasaka designation) at https://github.com/kurtking514-byte/project-capricorn
- **Package / app ID:** `io.github.kurtking514_byte.capricorn` (hyphen becomes underscore; never change it after release).
- **App name on the phone:** `Capri`.
- **Repo description:** "Project Capricorn: a personality construct that lives in your Android home screen. Offline, free, fan-made tribute to Cyberpunk: Edgerunners 2."
- **First boot screen:** "PROJECT CAPRICORN — biochip linked", then she introduces herself as Capri.
