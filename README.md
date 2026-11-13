# Project Capricorn

> *A personality construct that lives in your Android home screen.*
> Offline, free, fan-made tribute to **Cyberpunk: Edgerunners 2**.

Capri is an Android launcher built around Capri (Project Capricorn). She projects herself onto your home screen, and you run your phone through her chatbox, gestures and voice. No account, no internet, no paid AI. Everything stays on your phone.

## Status

🚧 **Early development (Phase 1: basic launcher).**

What works now:
- Set Capri as your home app
- Capri stands on your home screen in her jittery cut-out style, blinks, and reacts when you poke her
- She talks: her lines type out in a speech panel, and her mouth moves while she speaks
- Chatbox ("what do you want to do?"): type an app name to open it, or `alarm 7:30`, `timer 10 min`, `photo`, `apps`, `help`
- App suggestions appear as you type
- **Mini Capri overlay:** she floats over your other apps. Tap her for quick controls (screenshot, flashlight, camera, lock, back to chat), double-tap for a screenshot, long-press to go home, drag to move her. Turn it on by typing `overlay` in the chatbox.
- More chat commands: `flashlight`, `screenshot`, `lock`
- Dock (long-press any app → *Pin to dock*, up to 5)
- Swipe up for the full app list
- Wordless boot: a crimson slash, her shard hair bursts out, she blinks, home fades in

Coming next: gestures on Capri, roaming, psyche moods, more commands, voice, offline AI. See [ANDROID_PLAN.md](ANDROID_PLAN.md) for the full roadmap.

## Install

APK builds will be published under **Releases** once the first version is ready.

### Build it yourself
1. Install [Android Studio](https://developer.android.com/studio).
2. Clone this repo and open the folder in Android Studio.
3. Connect your phone with USB or wireless debugging, then press **Run**.
4. Press your phone's Home button and pick **Capri** as the default home app.

Requires Android 9 or newer.

## Art & skins

This repo contains **no character artwork**. Out of the box Capri is a simple stand-in drawn in code.

To use real sprites on your own phone, put PNG frames in `app/src/debug/assets/skin/` (that folder is git-ignored, so it never gets published). Frame names are listed at the top of `ui/CapriSprite.kt`; the only required one is `neutral.png`. Only use art you have the right to use, and don't share builds that contain art you can't redistribute.

## Credits

- UI look (ink + cyan + crimson + acid, cut corners, transmission panel) is inspired by [CapriCore](https://github.com/CapriChooms), a fan-made Capri desktop companion. No CapriCore code or artwork is included here.
- Font: [Exo 2](https://github.com/googlefonts/Exo-2.0), SIL Open Font License (`third_party/Exo2-OFL.txt`).

## Disclaimer

Fan project. Not affiliated with, endorsed by, or connected to CD PROJEKT RED, Studio Trigger, or Netflix. *Cyberpunk*, *Cyberpunk: Edgerunners* and all related characters belong to their respective owners.

## License

Code is released under the [MIT License](LICENSE).
