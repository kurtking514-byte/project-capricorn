# Capri — working notes for Claude

Android launcher built around Capri (Project Capricorn). Kotlin + Jetpack Compose.
Full feature list and roadmap: [ANDROID_PLAN.md](ANDROID_PLAN.md). Windows version (later): [LAPTOP_PLAN.md](LAPTOP_PLAN.md).

## How to work (one small step per prompt)

The user wants **small steps**. One prompt = one step from the checklist below, then stop.

- **Always ask first, with options.** Before any action (editing files, building, installing on the phone,
  publishing the preview, running commands), say what you'd do and give the user options to choose from.
  Never start something on your own, even if it seems obviously helpful. Explaining or answering is fine without asking.

- Do **only** the step the user names (or the next unchecked one). Do not roll into the next step.
- Before starting, say which step you're doing in one line. After finishing, tick it here and stop.
- Keep context small:
  - Read only the files the step needs. Use Grep or read a line range instead of whole files when you can.
  - **Never open the sprite PNGs** in `app/src/debug/assets/skin/` (33 big frames — reading images fills context fast).
  - Don't run a full build after every tiny edit; build once at the end of the step.
  - Don't spawn sub-agents unless the user asks.
- Design changes (how Capri looks or moves): change the **web preview first**
  (https://claude.ai/artifact/RmuNRE6DApvY4ajvwwydrM), get the user's OK, then port to the app as its own step.
- Pure functionality (commands, permissions, bug fixes) can go straight into the app.
- Commits: only when the user asks. No Claude/AI attribution lines in commits or PRs.

## Where we are (2026-11-17)

- Phase 0 (setup) and Phase 1 (basic launcher) are **done** and run on the Realme 12 Pro+.
- Phase 2 (Capri appears) is **mostly written** but **not committed and not tested on the phone**.
  Uncommitted files: `CapriOverlayService.kt`, `MiniCapriView.kt`, `BootOverlay.kt`, `CapriSprite.kt`,
  `HomeScreen.kt`, new `CapriMesh.kt`, plus README / ANDROID_PLAN edits.
  These hold the smooth mesh motion, the streak boot, the free-standing home Capri with idle activities,
  the speech bubble above her head, and the HUD clock.

## Building from the terminal

Gradle 8.10 fails on Android Studio's bundled Java 25 (error is just "25.0.4"). Use the JDK 21 instead:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew assembleDebug --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Key files

| File | What it does |
|---|---|
| `MainActivity.kt` | Launcher activity (HOME intent) |
| `ui/HomeScreen.kt` | Home: Capri, speech bubble, HUD clock, chatbox, dock |
| `ui/BootOverlay.kt` | Wordless boot animation |
| `ui/CapriSprite.kt` | Loads skin frames, poses, blink, talking mouth |
| `ui/CapriMesh.kt` | Warped-mesh drawing: sway, breathing, hair ripple, squash/stretch |
| `ui/CapriBody.kt`, `ui/CapriFace.kt` | Code-drawn stand-in (used when no skin is present) |
| `ui/AppDrawer.kt` | Swipe-up app list with search |
| `capri/CapriBrain.kt` | Chat command parser + Capri's replies |
| `overlay/CapriOverlayService.kt`, `MiniCapriView.kt`, `QuickActions.kt` | Mini Capri floating over other apps (accessibility service) |
| `data/AppRepository.kt`, `data/Prefs.kt` | Installed apps, dock, settings |

## Phase checklist

Tick `[x]` when a step is finished. Each line is meant to be **one prompt**.

### Phase 2 — Capri appears (finish + verify)
- [x] 2.1 Build the current uncommitted code; fix compile errors only (2026-11-17: builds clean, no fixes needed)
- [x] 2.2 User tests boot on the phone (2026-11-17: it runs, but the animation is wrong → redesign in 2B)
- [x] 2.2a Removed ALL sitting poses (`sit_floor_neutral`, `sit_floor_thinking`, `sit_edge_neutral`) from her idle activities: the user reads them as legs spread. Never add sitting poses back
- [x] 2.2e Idle variety: added standing poses (`hand_on_hip_smirk`, `arms_crossed_annoyed`, `idle_shrug`, `idle_smirk`), she changes every 4–8 s, holds 6–10 s, never the same pose twice in a row
- [x] 2.2c Removed the shard burst ("confetti") from the app entirely: boot landing and every time she speaks. Never add particle bursts back
- [x] 2.2d (built 2026-11-17, not yet seen on the phone; `drawPoseGlitch` in `ui/CapriSprite.kt`) Glitch-out transition on every pose change in the app (sliced, crimson split, ~0.1 s), replacing the crossfade. Same look as the glitch in the Boot Lab
- [x] 2.2f Chat command `poses` shows every pose once, 2 s each (2026-11-18). Why the user saw only 2 poses: after every line she holds the mood 4.5 s + waits 4–8 s, and each poke/message restarts that clock
- [x] 2.2g (2026-11-18) Whitelist: skin loader only keeps `CapriSkin.ALLOWED` frames. Removed crouch, recline ×2, chin rest, lean in, and every odd-eyes frame. Idle rotation is now 7 standing poses; `poses` shows 10
- [ ] ~~2.2b Lean against the screen edge~~ dropped: leaning poses are no longer allowed
- [ ] 2.3 User tests home Capri (motion, idle activities, speech bubble); fix what they report
- [ ] 2.4 User tests the mini overlay; fix what they report
- [ ] 2.5 Commit Phase 2 (when the user asks)

### Phase 2B — Boot redesign (user feedback 2026-11-17)
What's wrong now: the confetti/shard burst, and her full body zooming around the screen (looks fake).
What the user wants: **unpredictable red flashes like in the show** → she brings her hand forward
toward the screen → **red lightning** from her hand makes the **pinned apps** appear → there's a **background**.
Do steps B1–B4 in the **web preview only**; each step is one prompt and needs the user's OK.
Boot preview is its own small page: `preview/boot-lab.html`, published at https://claude.ai/artifact/DLCcXgzW7v2C52C94FDXWs
(republish the same file path to update it). From B2 on it uses an original stand-in figure; the user's real sprites appear only in the app.
- [ ] B1 (built 2026-11-17, waiting for the user's OK) Preview: remove confetti and the body zoom. Black screen, 3–5 hard red flashes at random times and in random shapes (full screen, slash, partial band)
- [ ] B2+B3 (rebuilt 2026-11-18, waiting for the user's OK) Preview: after the flashes she glitches in, **raises her hand up**, then **3–4 red lightning bolts at once strike down from the top of the screen** onto each pinned app, app by app in random order (like thunder hitting the ground); each slot flashes crimson, the app pops in and glitches. Opening flashes stay short (3–5 hits, 30–90 ms): the user does not want the boot longer. (User corrections: no sweep, no hand toward the camera, no fingers, arm must not stretch.) In the app use `idle_wave_raise`
- [ ] B4 ~~Preview: background~~ → replaced by step 4.6 (the phone's own wallpaper). For now the boot just ends on the home screen as it is
- [x] B5a (2026-11-18, compiles; not yet seen on the phone) `ui/BootOverlay.kt` rewritten: random flashes + glitch-through, she glitches in at her home spot, pose-glitch to `idle_wave_raise`, short hold. Old streak boot and the code-drawn boot are gone; with no skin only the flashes play
- [x] B5b (2026-11-18, compiles; not yet seen on the phone) Lightning onto the dock: 3–4 bolts per pinned app from the top, app by app in random order; each dock slot flashes crimson, the app pops in and glitches. `ui/BootDock.kt` shares slot positions + strike times; dock slots use `Modifier.bootSlot(i)`; the boot's black stops above the dock during the strikes
- [x] B5c Chat command `boot` replays the whole boot (the boot otherwise only plays when the app starts; pressing Home does NOT replay it)
- [x] B5h The boot appears instantly (no fade-in: the fade hid the fast flashes when replayed with `boot`); each flash 0.05–0.09 s
- [x] B5f Sync fix: bolts and slot flashes share one clock (`BootDock.time`, set by the boot every frame; `struck` holds exact scheduled hit times). First bolt lands exactly on the hit, others within the flash. Slot crimson flash is 0.07 s (was 0.04 s, too short to see)
- [x] B5e The chatbox is struck by lightning (bolts spread along it), flashes crimson, pops in and glitches, then stays in place (`BootDock.CHAT`)
- [x] B5g (2026-11-18, user was angry about this) **Chatbox and ALL pinned apps are struck at the SAME moment** — never stagger them or put the chatbox first. Boot sped up to about 1 s total (faster flashes, glitch-in 0.12 s, arm up 0.10 s, strike 0.04 s later)
- [x] B5d With no pinned apps, the dock shows 4 empty "+" slots instead of the "long-press any app to pin it here" text (tap one = app list). They're real dock slots, so the lightning reveals them and they stay. (The boot's own drawn placeholders are now only a fallback.)
- [ ] B6 User tests the new boot on the phone. NOTE 2026-11-18: Android Studio's Run was NOT installing new builds (phone still had the 17:12 build); check `adb shell dumpsys package io.github.kurtking514_byte.capricorn | grep lastUpdateTime` after installing

### Phase 2C — Make her feel alive (user picked these 2026-11-18; one step per prompt, ask before each)
- [x] C1 (2026-11-18, compiles; not seen on phone) Follows your finger: any touch on the home screen (`Modifier.trackGaze()` + `CapriGaze`) makes her look toward it for 1.5 s. Left 40% of screen → `idle_look_left`, right 40% → `idle_look_right`, middle → neutral. If the directions look swapped on the phone, swap them
- [x] C2 (2026-11-18, compiles; not seen on phone) Poke reactions, allowed poses only: 1 poke → hand on hip smirk ("sass"), 2 quick pokes → shrug, 3+ quick pokes (within 1.5 s of each other) → arms crossed annoyed, press-and-hold → idle_smirk ("smug"). Each tier has its own original lines in `CapriBrain.poke()` / `hold()`
- [x] C3 (2026-11-18, compiles; not seen on phone) Red hit on commands: when a chat reply actually does something (`CapriBrain.done()` → `consumeActed()` → `commandId`), the home screen shows full-screen crimson 70 ms, gap 40 ms, crimson slash 60 ms; her new pose glitches in. Apps opened from chat launch 180 ms later (`launchSoon`) so the hit is visible
- [x] C4 (2026-11-18, compiles; not seen on phone) Time of day (`CapriTime` / `DayPeriod` in `ui/CapriSprite.kt`, checked once a minute): morning 5–10 brisk (idle timing ×0.7, sway ×1.1), day normal, evening 18–23 slower (×1.3, sway ×0.85), night 0–4 drowsy half-closed eyes (×1.6, sway ×0.6). At night, 25 s with no poke/chat → she dozes standing, eyes closed; a poke wakes her annoyed (`CapriBrain.wakeUp()`). No lying down
- [x] C5 (2026-11-18, compiles; not seen on phone) Phone reactions, home screen only, after the boot: charger plugged in → smug smirk + line; battery 15% or lower (not charging) → arms crossed + line, once until it goes back above 20% or is plugged in; new notification → she looks toward the top (`idle_look_left`) and says which app it is from (never reads the text), at most one every 10 s. Notifications need Notification access: chat command `notifs` opens it (`overlay/CapriNotifListener.kt`). Any reaction wakes her if she was dozing

### Phase 3 — Commands & gestures
- [ ] 3.1 Gestures on home Capri: double-tap screenshot, triple-tap lock
- [ ] 3.2 Gestures: swipe up flashlight, swipe down silent mode
- [ ] 3.3 Gesture settings screen (remap each gesture)
- [ ] 3.4 Chat commands: call contact, wifi/bluetooth settings, music pause/next
- [ ] 3.5 Live search in chatbox also shows contacts and settings

### Phase 4 — Her world
- [ ] 4.1 Psyches (moods) by time of day / battery — preview first
- [ ] 4.2 Psyche-specific reply lines in `CapriBrain`
- [ ] 4.3 Glitch effect on psyche switch — preview first
- [ ] 4.4 In-character permission onboarding
- [ ] 4.5 Realme battery / auto-launch guide
- [ ] 4.6 Phone wallpaper as the home background (user asked 2026-11-18, for later; current background looks bad). Split into:
  - 4.6a Show the system wallpaper behind the launcher: `android:windowShowWallpaper=true` + transparent window background in the theme, and remove the solid home background in `HomeScreen.kt`. No permission needed, live wallpapers work, nothing is read from storage
  - 4.6b Readability: flat semi-transparent ink panels behind the clock, speech bubble and chatbox (flat color, no gradient), with a strength setting
  - 4.6c Chat command `wallpaper` that opens Android's wallpaper picker (`Intent.ACTION_SET_WALLPAPER`)
  - 4.6d Boot: after the last red flash, the wallpaper is what appears behind her
  - Don't read the wallpaper image with `WallpaperManager.getDrawable()`: it needs storage permission on new Android and isn't needed

### Phase 5 — Convenient extras (one feature per step)
- [ ] 5.1 Smart suggestions (usage stats)
- [ ] 5.2 Notification bubbles
- [ ] 5.3 Vault (hidden apps)
- [ ] 5.4 Focus mode
- [ ] 5.5 Widgets
- [ ] 5.6 Music controls
- [ ] 5.7 Backup / restore

### Phase 6 — Voice
- [ ] 6.1 Add Vosk + model download
- [ ] 6.2 Push-to-talk (long-press Capri) with on/off switch
- [ ] 6.3 "Hey Capri" wake word with its own switch

### Phase 7 — Offline AI
- [ ] 7.1 Add llama.cpp (or MediaPipe) and model download in settings
- [ ] 7.2 Capri personality prompt; unknown chat goes to the AI
- [ ] 7.3 Load on demand, unload when idle

### Phase 8 — GitHub release
- [ ] 8.1 README screenshots with the stand-in skin, skin guide
- [ ] 8.2 Release APK on GitHub

## Ideas backlog (not scheduled yet; the user picks what becomes a step)

Making her feel alive:
- She looks toward your finger when you touch the screen (`idle_look_left` / `idle_look_right`)
- Red flash takeover whenever she runs a command (opening an app, alarm, screenshot)
- Short glitch tear on her sprite when she changes pose
- Phone buzz (haptics) in time with the red flashes
- Optional sound effects with an on/off switch (boot crackle, flash hits)
- Short re-entry animation when you come back home from an app (not the full boot)
- Background that shifts with the time of day
- A "reach toward the screen" frame added to the local skin to make B2 look better

## Rules that never change

- Never bundle official or CapriCore art in the repo. Real sprites live only in the git-ignored `app/src/debug/assets/skin/`.
- **Safe standing poses only, forever.** The app uses only the frames in `CapriSkin.ALLOWED` (`ui/CapriSprite.kt`):
  neutral, smirk, hand on hip, arms crossed, shrug, thinking, look left/right, wave (+ blink/mouth/wave building blocks).
  Never add sitting, crouching, lying-down, leaning or chin-rest poses, or frames with odd eyes
  (surprised, excited, attentive, sleepy, skeptical, laughing, annoyed, point, happy = open grin). Never suggest them again.
- Flat colors, no gradients/glow. Smooth motion, no stop-motion snaps. Full body on home, no box around her.
- Capri's voice: playful, cocky, teasing. Never polite-assistant tone.
- Package ID `io.github.kurtking514_byte.capricorn` must never change.
