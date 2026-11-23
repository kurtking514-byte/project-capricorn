# Capri — design and motion reference

Measured from the video "Capri 4K Scene Pack _ Upscaled.mp4" (local file, not in the repo).
Words and numbers only: no frames, crops or art from the show are stored in this repo.

## How this was measured (read first)

- Video: 1280×720, **24 fps**, 490.68 s (~11,776 frames). **1 frame = 41.67 ms.** All times below
  come from frame numbers: `ms = frames ÷ 24 × 1000`. "f7520" = frame 7520 = 5:13.3.
- Pass 1: 1 frame every 2 s (246 frames) to find her moments.
- Pass 2: **every 2nd frame** of five stretches (f1968–2206, f3600–3838, f5424–5842, f7056–7714,
  f10272–10810). Because only even frames were seen, **every duration has ±1 frame (±42 ms) of
  error**, and a 1-frame event on an odd frame could be missed. Where this matters it says so.
- Pass 3: hex colors read by script (5×5-pixel median) from full-resolution frames f3700, f7520,
  f7250, f10404, f3640; four close-up crops for the design.
- Scale words: **"panel"** = her green face panel; sizes are in % of panel width (PW), panel height
  (PH), frame width (FW) or frame height (FH). Her head without hair is rarely fully visible, so
  head-height ratios are given only where they could be measured.
- Left out on purpose: poses outside `CapriSkin.ALLOWED` (the open laughing grin at f5666–5704,
  flying/stretched attack shots, frames with many eyes). Another masked, pink-haired character
  appears at 5:24–6:36 and f7420–7498; she is not Capri and is ignored.

### The one-paragraph summary (for an animator who has never seen her)

Capri is a **held drawing that snaps**. In nearly every shot her whole body is frozen for
1.6–7 s: no sway, no breathing, the hair is rigid. Life comes from (1) instant face swaps (eye
shape, eye direction, mouth), each with 0–1 in-between drawings, (2) a talking mouth that is a
separate floating shape cycling 3 drawings, (3) rare 2-frame blinks where the eyes simply
disappear, (4) glitch effects around her (thin horizontal green + crimson bars), and (5) cuts and
camera moves. Smooth easing, overshoot or wobble on her body was **not seen once**.

---

## Part 1 — Design (standing still)

### Palette (sampled, hex)

| Part | Hex | Notes |
|---|---|---|
| Face panel | `#00B144` | range `#00B040`–`#00B33F`; flat, no gradient |
| Eye ring (thin circle) | `#00B144` | same green as the panel (samples `#00AF35`–`#00B13D`) |
| Eye shape fill | `#00030D` | near-black navy |
| Head / body black | `#0E0A16` | also `#01000B`–`#01000F`; dark navy `#201C36` on the body |
| Iris disc | `#0040A0` | (`#003FA0`) |
| Pupil disc | `#B3B3B3` | light grey, **not pure white**; thin dark outline |
| Mouth | `#B2B4B4` | same light grey, thin green outline; closed mouth = green line |
| Offset edge line | `#37197B` | purple, 1–3 px at 1280 w, on the lower-right edges of panel and eyes |
| Hair shards | `#BC002B` | range `#B60020`–`#BF002E`; darker accents `#9E0027`, `#77122F` |
| Hair black shards | `#0E0A16` | tucked between crimson shards as shadow |
| Hair sparkle | near `#FFFFFF` dots | only in some scenes (sky, room); none in the white-field scenes |
| Body crimson panels | `#BB003D` | slightly pinker/cooler than the hair |
| Red/black border on body | `#640327` | dark wine where red meets black |

Note: the flat "white" areas of the show (pupil, mouth, white-field background `#B5B5B5`) are a
light grey. The file does reach 255 in bright sparkle dots, so this grey is intended, not a dim video.

### Overall
- Silhouette: a **tiny, very thin black-and-crimson figure under a huge crimson shard mass**. In the
  full-body sky shot (f7228–7294) the hair mass is ~3× her shoulder width and ~1.5–2× her torso height.
- Height in heads: at least ~8 heads (head without hair). **Exact value unclear**: her feet are out of
  frame in the clearest full-body shots.
- Default stance (f7228–7294, f3600–3668): standing straight, front-facing, legs straight and
  together, arms hanging straight down at her sides, hands at mid-thigh. Weight centered.

### Head
- Small, black, roughly round, with a jagged spiky top that merges into the hair (f10404).
- The green panel covers the upper ~60% of the head front (the eye band). Below it: black lower face
  with a small light-grey jagged "teeth" mouth (f10404, f10720–10810).
- Neck: thin black stalk, ~25% of the head's width.

### Face panel
- One flat green quad. Front view (f7520): **inverted trapezoid**, top edge ≈ 64% FW in that close-up,
  bottom edge ≈ 74% of the top edge, height ≈ 37% of the top edge (top width : height ≈ 2.7 : 1).
  At other angles it is near-rectangular (f10404: width : height ≈ 1.8 : 1).
- Corners: sharp. Edges: hard, no outline, except the purple offset line `#37197B` along the right
  and bottom edges (looks like a 1–3 px color split).
- It deforms: edges bow (curve) during the squash/stretch (f7624–7646, see Part 2.12) and it is
  shown in perspective when her head is turned (f7148–7210: left eye foreshortened).

### Eyes (open, default)
- Two black lens/almond shapes cut into the panel, each ≈ 42% PW wide; centers ≈ 44% PW apart.
- One thin pointed spike rises from the top inner edge of each eye (up and slightly outward,
  ≈ 20% of eye height long); one or two small spikes point down from the bottom edge.
- Inside each: a thin green ring (diameter ≈ 70% of eye height, stroke ≈ 2% of eye width), a blue
  iris disc (≈ 40% of ring diameter; ≈ 37% PH in the front view), a light-grey pupil disc
  (≈ 50–55% of iris diameter) with a thin dark outline. Flat fills.
- Front view: a thin purple line traces the lower-outer rim of iris and eye (offset, same color split).
- **Narrowed** (f3744–3788): the upper lid becomes a straight line sloping down toward the inner
  corners; ring and iris slide ≈ 15% of eye width toward one side; the lid cuts the top of the ring.
- **Very narrow** (f3790–3828): eye height ≈ 60% of open; pupils further to the side and lower.
- **Closed** (two looks): (a) blink: eyes removed entirely, panel shows plain green with one dark
  curved lid line (f10412, f10420, f10762); (b) smug closed: two drooping arcs (curving down at the
  outer ends) with small spikes, held for seconds (f7648–7672, f5706–5764).
- Brows: none. Nose: none.

### Mouth
- In panel-only shots it **floats as a separate shape just below the panel's bottom edge**, centered
  (gap ≈ 10% PH). Three drawings (f7500–7594):
  - **L** (closed): thin green zigzag line, ≈ 22% PW wide.
  - **S** (small): thin light-grey sliver triangle, same width, ≈ 8% PH tall.
  - **B** (big): light-grey triangle, point down, ≈ 22% PW wide, ≈ 27% PH tall, thin green outline.
- With her full head visible, the mouth is a small light-grey jagged teeth shape on the black lower face.
- At rest: **L** (closed zigzag line).

### Hair
- A mass of straight-edged shards: long thin triangles plus jagged polygons, all flat crimson
  `#BC002B`, no outlines, no gradient. Black shards (`#0E0A16`) sit in the gaps as shadow.
- Spreads out sideways and up from the head like a fan; widest part 3–5× the panel width; the
  longest shards hang down past her shoulders.
- Sparkle texture: dense small white dots over the crimson in some scenes (f7228–7294, f3600–3668),
  none in others (f10392–10810). When present it does not visibly change frame to frame at 320 px
  (unclear at full size).

### Neck, torso, arms, hands, legs, feet
- Very thin, black, with flat crimson geometric panels: a band across the chest, a "brief"-shaped
  panel at the hips, patches on thighs and shins. Small green triangles at chest and hips in some
  shots (f3600–3668).
- Arms: long and thin, hanging straight; reach mid-thigh.
- Hands: thin, claw-like, ~4–5 thin slightly spread fingers (f7250).
- Legs: long, straight, together. Feet: **unclear** (out of frame).

### Line art and shading
- Almost no line art: shapes are defined by flat color edges. Only line: the purple offset edge.
- Shading: 1 tone per part. Body is red vs black only; panel, eyes and hair have no shade tones.

---

## Part 2 — Every movement, body part by body part

Movement format fields: Seen at / Parts (order) / Start → end / Path / Timing / Easing / Size /
Frequency / Emotion / Confidence / Build: (a) existing pose frames + glitch transition, (b) mesh motion
in `ui/CapriMesh.kt`, (c) only with a future Live2D/Spine rig.

### 2.1 Eyes

**Blink**
- Seen at: f10412 (7:13.8), f10420 (7:14.2), f10448–10450 (7:15.3), f10762 (7:28.4); also the squash
  frame f7618 (see 2.12).
- Parts: eyes only (panel stays).
- Start → end: open → eyes removed, plain green + one curved lid line → open.
- Path: none (drawing swap).
- Timing: closed **2 frames = 83 ms** (f10448 case ≥ 4 frames = 167 ms). Closing and opening: **0 frames**
  (no half-closed drawing seen).
- Easing: none, hard snap both ways.
- Size: eyes disappear completely.
- Frequency: irregular. Double blink: 2 blinks 8 frames (333 ms) apart (f10412, f10420), then the next
  28 frames later. Many stretches of 4.5–6.6 s with **no blink** (f3670–3828, f5490–5602, f2010–2142).
  A blink follows a head move 2 frames later (f10758 → f10762).
- Emotion: neutral, idle.
- Confidence: medium (only even frames seen; a blink could be 1–3 frames).
- Build: (a) — swap to `idle_blink_closed` for 83 ms with no `idle_blink_half` and no fade.

**Eye narrowing (scheming / side-eye), stepped**
- Seen at: f3670–3828 (2:32.9–2:39.5), f7106 (4:56.1), f5658 (3:55.8).
- Parts: eye shape first, iris/pupil move in the same drawing.
- Start → end: open (A, held 71 f = 2.96 s) → one in-between at f3742 (2 f = 83 ms) → narrowed (B, held
  45 f = 1.88 s) → **snap, no in-between** → very narrow (C, held 39 f = 1.63 s).
- Path: none (drawing swaps). Iris moves ≈ 15% of eye width toward one side, then further and lower.
- Easing: none. Pose-to-pose with 0–1 in-betweens.
- Frequency: a few per scene; each state held 1.6–3 s.
- Emotion: scheming, sizing someone up, teasing.
- Confidence: high (big close-up).
- Build: (a) only if a matching allowed frame exists (`idle_smirk`, `idle_look_left/right`); hard swap.

**Eye direction change (glance)**
- Seen at: f3744, f3790 (pupils to the side), f7612 (glance up), f10720–10756 (looking toward the
  human on her left), f10758 (glance down during a head turn), f5766 (side-eye toward the person on
  the right, after a cut).
- Timing: 0–2 frames (0–83 ms), then held 1.5–3 s.
- Does the head follow? Usually **no** (eyes move inside a still panel). Once (f10758) the head turns
  with it in the same 2 frames.
- Confidence: high for "instant"; medium for the head-follow rule (1 example).
- Build: (a) `idle_look_left` / `idle_look_right`, hard swap.

**Smug closed eyes**
- Seen at: f7648–7672 (1.04 s, while talking), f5706–5764 (2.46 s).
- Open → drooping-arc closed eyes: snap; closed → open: snap (f7674), pupils centered on reopening.
- Emotion: smug, pleased with herself. Mouth keeps talking while eyes stay closed.
- Confidence: high. Build: (a) only if an allowed closed-smug frame exists; otherwise skip.

### 2.2 Face panel
- Does not flicker or change color in normal shots. Holds still while eyes/mouth change.
- Glitch dropout: during heavy glitching the panel shows **no eyes** (blank green) for 2 frames at a time
  (f10352, f10358, f10364), and jumps position by 2–5% FW every 2 frames (f10342–10390).
- Squash/stretch and bowing edges during the bob (2.12).
- Build: (a) for swaps; blank-eye dropout can be drawn in the glitch effect (Part 4).

### 2.3 Mouth (talking)
- Seen at: f7500–7594 (best), f7632–7672, f7148–7210, f7092–7142, f5604–5632.
- Shapes: **L** closed zigzag line, **S** small sliver, **B** big triangle (see Part 1).
- Measured sequence f7500–7594 (each letter = 2 frames):
  `L L L B B L B B S B B S B B L B B S B B S B B L B B S B B S B B S L L L L …`
- Timing: **B held 4 frames (167 ms)**; S or L held 2 frames (83 ms). One cycle B→S ≈ 6 frames = 250 ms.
  The short shape after B is S about 2 of 3 times and L about 1 of 3 times.
- When she stops talking: ends on **L** and holds.
- Variant (f7148–7210): mouth stays on **B** almost the whole time and closes to L for exactly 2 frames
  every 12–24 frames (f7160, f7184, f7196).
- Eyes and panel: **completely still** while talking.
- Easing: none (swaps). On 2s.
- Confidence: high for the pattern; medium for exact shape sizes.
- Build: (a) mouth frames swapped with no fade. The skin's `idle_mouth_small/round/wide` are the
  closest; a "round" mouth was not seen in the show.

### 2.4 Head
**Head turn + tilt**
- Seen at: f10758 (7:28.25).
- Panel shifts ≈ 5% FW sideways and tilts ≈ 10° in **2 frames (83 ms)**, eyes glance down, then a blink
  2 frames later (f10762), then held.
- Easing: none seen (one drawing). Overshoot: none.
- Confidence: medium (one example). Build: (a) pose swap; small rotation could be (b).

**Held tilt**
- f7148–7210: panel rotated ≈ 10° clockwise, held 63 f = 2.6 s while talking.
- f2010–2142: panel tilted ≈ 25–30°, held 133 f = 5.5 s (perched at someone's shoulder).
- No nods and no bobbing while talking were seen. The head **never** moves while the mouth talks.
- Build: (a) or a static rotation in (b) (rotation applied once, not animated back and forth).

### 2.5 Neck and shoulders
- No shrugs, raises or tension changes seen in the measured stretches. **Unclear / not observed.**

### 2.6 Torso
- Breathing: **none visible** in any held shot (f3600–3668 2.9 s, f7228–7294 2.8 s, f10454–10626 7.2 s).
- Leaning, twisting, bounce: not seen while standing.
- Build: nothing to build — the torso should stay still.

### 2.7 Arms
- Default: hanging straight down at her sides (f7228–7294). Not seen moving in the measured stretches
  (a human hugging her at f10628–10718 moves; she does not). **Arm gestures: not observed.**

### 2.8 Hands
- Thin claw-like fingers, slightly spread, at mid-thigh. Not seen moving. **Waves: not observed.**

### 2.9 Hips and legs
- Weight shifts, steps: **not observed**. Legs straight and together in every standing shot.

### 2.10 Feet
- **Unclear** (out of frame in the clear full-body shots).

### 2.11 Hair
- Secondary motion: **none**. The hair is a rigid held drawing in every calm shot: f7228–7294,
  f7356–7414 (2.5 s), f10404–10810. It does not lag, sway, overshoot or settle.
- Only during heavy glitching (f10342–10390) are the shards **re-drawn every 2 frames** ("boil"):
  shard outlines jump to new shapes/positions each 83 ms, for ~2 s, then freeze again.
- Confidence: high.
- Build: (b) set hair ripple to 0. The boil needs alternate hair drawings or a code effect inside the
  glitch (Part 4).

### 2.12 Whole body

**Idle loop** (what she does when nothing happens)
- She holds one drawing for **1.6–7.2 s** (measured holds: 1.63, 1.71, 1.88, 2.46, 2.6, 2.9, 2.96, 4.6,
  4.7, 5.5, 6.6, 7.2 s). In that time the only changes are: an occasional 2-frame blink, an eye-shape or
  glance swap (0–83 ms), or the talking mouth.
- No sway, no breathing, no hair motion, no weight shift.

**Squash-blink bob** (her only body-like motion)
- Seen at: f7612–7630 (5:17.2–5:18.0), face-panel-only shot.
- Parts: panel first; eyes change inside it.
- Steps: (1) f7612–7616, 6 f = 250 ms: panel shrinks ≈ 15% and drops ≈ 10% PH, eyes narrow and glance
  up. (2) f7618, 2 f = 83 ms: **squash** — panel flattened to a thin curved band ≈ 35% of normal height,
  eyes closed lines (this is the blink). (3) f7620–7622, 4 f = 167 ms: small panel again, eyes wide and
  round. (4) f7624–7630, 8 f = 333 ms: panel grows to ≈ 120% of the start size (comes toward camera),
  top and bottom edges bow outward. (5) f7632+: holds at the new size and talks.
- Total 18 f = 750 ms. Each step is a separate drawing, on 2s; no in-between easing, no overshoot back.
- Emotion: perking up to say something.
- Confidence: medium (one example). Build: (b) a one-shot scale sequence (not a spring), or (a) if frames exist.

**Entering the screen**
- Glitch-in (f10274–10314, 1.7 s): she appears as a dimmed crimson silhouette surrounded by horizontal
  glitch bars (Part 4), then a **hard cut**.
- Zoom-in (f10338, 2 f): her face rushes toward the camera with radial motion blur, then settles
  (f10340) into a glitching close-up (f10342–10390, 2 s) that calms to clean (f10404+).
- Build: glitch = code effect; zoom blur = code effect (flat motion streaks, no glow).

**Leaving the screen**: hard cuts only (to black f7212–7226, 16 f = 667 ms; to flat grey f10316–10330).

**Squash/stretch, hops, bounces**: only the one squash-blink bob above. No hops or bounces.

---

## Part 3 — Timing and style of motion

- **Frame style:** her drawings change **on 2s** (every 2 frames = 83 ms) or are held for seconds.
  Mouth: on 2s with the big shape on 4s. Glitch effects: new layout every 2 frames. Nothing on 1s was
  confirmed (only even frames were checked).
- **Easing:** none on her own body. Changes are hard swaps with 0 or 1 in-between drawing. Overshoot: 0%.
- **Anticipation:** one case — the squash-blink bob dips (shrinks + drops) for 250 ms before growing.
  Otherwise none.
- **Holds:** 1.6–7.2 s per drawing (list in 2.12). Expressions: 1.0–3.0 s. Full idle holds up to 7.2 s.
- **Snappy vs fluid:** fastest change 0 frames (snap); slowest change the 18-frame (750 ms) bob. No
  slow eased motion exists to compare.
- **Personality in motion** (concrete):
  - Scheming look: eyes open 3.0 s → 83 ms in-between → lids slope down toward the inner corners, iris
    slides ≈ 15% of eye width to one side, hold 1.9 s → snap to narrower (60% height), hold 1.6 s.
  - Smug: eyes snap to drooping closed arcs and stay closed 1.0–2.5 s while the mouth keeps talking.
  - Cocky talk: head tilted ≈ 10° and held 2.6 s, mouth hanging open on the big triangle most of the
    time, closing for only 83 ms every 0.5–1 s.
  - Indifference: she stays perfectly still (up to 7.2 s) while other characters move around or touch her.

---

## Part 4 — Effects

**Horizontal bar glitch (her signature glitch)**
- Seen at: f10274–10314 (1.7 s, on dark grey), f10342–10390 (2 s, full frame), traces to f10402.
- Shape: thin **horizontal bars** sticking out sideways (both directions) from her body. 3–12 per frame;
  sometimes 8–10 stacked into a dense block beside her face.
- Bar height 0.3–2.5% FH; length up to ≈ 33% FW.
- Colors: bright green (≈ `#00B144`) and crimson (≈ `#BC002B`); grey streaks in the full-frame version.
- Re-rolls: new random layout **every 2 frames (83 ms)**.
- Irregular: some frames almost clean (f10284, f10300, f10304) — it comes in bursts, not a steady rate.
- Fade-out: density drops over ~2 s (heavy f10342–10380, light f10382–10390, a few 1-px bars near the
  top to f10402), then clean.
- Her body during it: the hair boils (re-drawn every 2 frames), the panel jumps 2–5% FW and sometimes
  loses its eyes. The body is not cut into offset slices.
- Trigger: her appearing / taking over the screen.
- Confidence: high. Build: code effect drawn over the sprite.

**Full-screen vertical smear glitch**
- f5430–5488 (2.46 s): one **held** image of vertical drips/smears (crimson, navy, black, white, light
  cyan) with a tiny green mark (her face) inside. Hard cut in and out. Confidence: high.

**White vertical bars**
- f7680–7714: thin white vertical bars (≈ 0.6–6% FW wide) flash on at random x positions for 2 frames
  and vanish; 1–3 at a time, every 2–6 frames. Background black. Confidence: medium.

**Crimson energy streaks and droplets**
- f2160–2172: small crimson dots pop on/off every 2–4 frames around her.
- f2180–2196: thin crimson streak lines shoot out horizontally from her, new shapes every 2 frames.

**Red flashes**
- Full-screen crimson frame with white sparkle at 3:20–3:22 and a full-screen red frame with horizontal
  scanlines at 2:26 were seen in Pass 1 only. **Duration, count and gaps: unclear** (not frame-counted).

**Hard cuts**
- To black: 16 frames (667 ms) between shots (f7212–7226). To flat light grey `#B5B5B5`: 15 frames
  (f10316–10330).

**Glow, scanlines, screen shake**
- Glow: none on her. Screen shake: none seen. Color split: only the thin purple offset line on panel
  and eye edges (static, always on).

---

## Quick numbers for an animator

| Thing | Value |
|---|---|
| Base frame time | 41.67 ms (24 fps); her drawings change on 2s (83 ms) |
| Idle hold | 1.6–7.2 s, completely still |
| Blink | eyes gone 83 ms, no half-lid; irregular; sometimes double (333 ms apart); often right after a head move |
| Expression swap | 0–83 ms (0–1 in-between), then hold 1.0–3.0 s |
| Talking | B 167 ms / S or L 83 ms, ~250 ms per cycle, ends on L; eyes and head still |
| Head turn | ≈ 10° + 5% FW shift in 83 ms, then blink |
| Squash-blink bob | 750 ms total: dip 250, squash 83, small 167, grow to 120% 333 ms |
| Hair | rigid; boils every 83 ms only during glitches |
| Glitch bars | 3–12 thin green/crimson horizontal bars, re-roll every 83 ms, bursts fading over ~2 s |
