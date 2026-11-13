# Capri — Laptop (Windows) Version Plan

Saved 2026-11-11 for later. Capri is a personal companion/launcher: it roams the screen,
has a chatbox, runs commands, and reacts to quick-action gestures.

## Goals
- Feels personal; Capri lives on the desktop and wanders around.
- Works **offline**, costs **nothing**, uses **low RAM**.
- No AI required — AI is optional and local only.

## Platform notes (Windows)
- No real "home screen" to replace, so Capri is an **always-on-top transparent overlay window**
  plus a chatbox (toggle with a global hotkey).
- Tech stack (low RAM): **C# (WPF or WinUI)** or **Tauri** (~30–60 MB).
  Avoid Electron (200 MB+ idle).

## Features (v1)
| Feature | How on Windows |
|---|---|
| Capri roams the screen | Transparent, click-through overlay window with an animated sprite |
| Chatbox | Small window that pops up next to Capri / via hotkey |
| "Open X" | Search Start Menu shortcuts + installed apps, launch by name |
| Screenshot | Built-in capture API or trigger Snipping Tool |
| Alarm | Capri's own alarm/timer system (Windows has no simple alarm API) |
| Take photo | Webcam capture (less natural than on a phone) |
| Quick actions on Capri | Double-click = screenshot, right-click = menu, drag = move, etc. (user-configurable) |

## Command system (no AI)
- Rule-based parser: keywords + patterns, e.g. `open <app>`, `set alarm for <time>`,
  `screenshot`, `take photo`, `timer <n> min`.
- Instant, offline, nearly zero RAM.
- Personality: lots of pre-written responses + moods so Capri doesn't feel robotic.

## Voice (optional, offline, free)
- **Vosk** (~50 MB model) — lightweight.
- **whisper.cpp tiny** — more accurate, a bit heavier.

## Optional AI (free, offline)
- Small local models: **Qwen3 0.6B / 1.7B** or **Gemma 3 1B**, run with **llama.cpp**
  (easiest on Windows: **Ollama**).
- Cost: ~0.5–1.5 GB RAM while loaded.
- Hybrid approach: parser handles known commands; the AI is loaded **only** when Capri doesn't
  understand something (casual chat), then unloaded to free RAM.

## Build order
1. Capri overlay that roams the desktop + chatbox.
2. Typed commands: open app, screenshot, alarm/timer, photo.
3. Gestures on Capri (double-click → screenshot, etc.), configurable.
4. Offline voice (Vosk).
5. Optional on-demand local AI.
