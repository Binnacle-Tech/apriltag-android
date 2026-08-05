# Binnacle implementation roadmap — AprilTag Detector

How to use this: work top to bottom. Each phase lists concrete steps, the files
they touch, and the **tenet** it serves. Before shipping any step, run the two
gates below. Check items off as you go.

---

## The four tenets (the north star)

Every feature is tested against these. If it can't satisfy them, it belongs
outside the app.

1. **Orchestrate, don't absorb.** Drive capable tools by reference; don't
   re-implement what already works. Here: the AprilTag **C detector** and
   **CameraX** are the engines — the app is glue + judgement (framing, overlay,
   settings), not a reinvention.
2. **Reference and attribute.** Keep upstream code, licenses, and credit. Here:
   AprilTag (BSD-2), the fonts (SIL OFL), the original app.
3. **Fail loud, gate clearly.** Errors and blocked states are visible and named,
   never a silent log. Here: camera-permission-denied, camera-open failure.
4. **Show the work.** Surface what the machine is doing. Here: the FPS / latency
   / detection telemetry.

## The two gates (run before every ship)

- **Belonging test:** does this feature satisfy the four tenets? If not, cut it
  or move it out of the app.
- **Greyscale test:** screenshot the screen, remove colour. Every state must
  still read — because each is paired with a second, non-colour channel (a
  glyph, a label, a position, a hatch). Colour alone never carries meaning.

---

## Status legend

- [x] done  ·  [ ] to do  ·  (→ file) where the work lands

---

## Phase 0 — Foundation  ·  *colour tenet*

- [x] Port Binnacle tokens (ink/panel/line/text ramp + accents named by meaning)
      → `res/values/colors.xml`, `res/values-night/colors.xml`
- [x] Theme + toolbar on the ink surface, amber accent, per-mode status icons
      → `res/values*/styles.xml`, `res/values*/bools.xml`
- **Gate:** app chrome reads as Binnacle in light and dark.

## Phase 1 — Typography roles  ·  *typography*

Three faces, three jobs: display = titles, ui = body, **mono = machine data**.

- [ ] Add the five `.ttf` files and font-family resources (→ `res/font/`)
- [ ] Apply `bin_ui` app-wide and `bin_display` to the toolbar title
      (→ `res/values*/styles.xml`, `res/layout/main.xml`)
- [ ] Reserve `bin_mono` for the telemetry readout (used in Phase 3)
- Full steps in `FONTS.md`.
- **Gate:** the app name is display; body is ui; nothing machine-data is set in a
  proportional face yet.

## Phase 2 — The signature: detection overlay accent contract  ·  *colour + "one loud thing"*

The live overlay is this app's **one loud thing** — make it speak the contract.

- [x] Map detection colour to meaning, not decoration:
  - clean decode (`hamming == 0`) → **good** (cyan)
  - error-corrected (`hamming > 0`) → **now** (amber), i.e. "read, but less certain"
  → `DetectionThread.renderDetection()` (uses `ApriltagDetection.hamming`)
- [x] Keep the **non-colour channel**: the tag ID carries identity; corrected
      reads use a **dashed** border (vs solid) and a first-corner **orientation
      dot** — both readable without colour.
- [x] Pull overlay colours from the tokens (`bin_good`, `bin_now`), resolved via
      `ContextCompat.getColor` so they track light/dark — no hard-coded green/red.
- **Gate (greyscale):** clean vs corrected detections are still distinguishable
  with colour removed.

## Phase 3 — Telemetry as a "well"  ·  *show the work + typography*

- [ ] Move FPS / latency / tag-family text into a recessed **well** panel
      (rounded rect, `bin_well` fill, `bin_line` border) anchored bottom-left
      → `res/layout/main.xml`, `DetectionThread`/activity text setup
- [ ] Render the numbers in **`bin_mono`**, muted label + text ramp (drop the
      hard-coded green in `stylizeText()`)
- [ ] Style the tag-family readout as a **status pill** (eyebrow label, tracked
      uppercase) rather than plain green text
- **Gate:** machine data is mono and lives in one clearly-bounded panel.

## Phase 4 — Fail loud, gate clearly  ·  *fail-loud tenet + glyphs*

- [ ] Camera permission denied → a visible **stop** state: a card with the stop
      glyph, a one-line reason, and a "Grant camera access" button (not just a
      `Log.w`)
      → `ApriltagDetectorActivity` (replace the silent `has_camera_permissions == 0`
      path), new `res/layout` for the state
- [ ] Camera-open / bind failure → same treatment (surface `CameraController`
      errors to the UI)
- **Gate:** with the camera permission revoked, the screen explains itself and
  offers the fix.

## Phase 5 — Glyphs (the non-colour channel)  ·  *colour + component kit*

- [ ] Port the four state glyphs from `binnacle-glyphs.svg` to vector drawables
      (`gl_running`, `gl_done`, `gl_held`, `gl_failed`) → `res/drawable/`
- [ ] Use them wherever a state shows: the stop card (Phase 4), the tag-family /
      status pill, any future status
- **Gate:** every state on screen pairs an accent with its glyph.

## Phase 6 — Settings to the component grammar  ·  *layout grammar + voice*

- [ ] Group preferences as **section cards** (panel fill, line border, radius
      `10dp`) → `res/xml/pref_settings.xml` + preference layout/theme
- [ ] Numeric values (sigma, threads, hamming, decimation) shown in **mono**
- [ ] Layout reads top-to-bottom: source → choices → action
- [ ] Voice pass: sentence case throughout; keep names as the only exception
      → `res/values/strings.xml`
- **Gate:** settings look like the same instrument as the main screen.

## Phase 7 — Modes & vision profiles  ·  *modes/vision + colour*

- [ ] Light + dark already ship. Add a **high-contrast** token set
      (`res/values-night` variant or a manual mode)
- [ ] Add **deutan/protan** and **tritan** accent variants (separation carried by
      *lightness*, per the tokens) behind a settings toggle
- [ ] Audit all four modes × profiles with the greyscale test
- **Gate:** every mode/profile passes greyscale; no state collapses.

## Phase 8 — Motion & restraint  ·  *motion*

- [ ] Budget: **one moment per screen.** Decide the single orchestrated motion
      (e.g. a brief detection lock-on pulse) or explicitly none — the overlay
      density already carries life
- [ ] Honour reduced-motion as a floor (respect the system setting)
- **Gate:** at most one thing animates at a time; nothing moves without purpose.

## Phase 9 — Voice, naming & wayfinding  ·  *voice*

- [ ] Sentence-case audit of all user-facing strings (→ `res/values/strings.xml`)
- [ ] Confirm the product name follows the naming register (function-first, a
      real second meaning) if you rename beyond "AprilTag Detector"
- [ ] Settings header / running-head names the section you're in
- **Gate:** the app speaks in one consistent, plain voice.

## Phase 10 — Belonging test & attribution finalize  ·  *all tenets*

- [ ] Write `PRINCIPLES.md` with the four tenets at the top; run the belonging
      test on each feature and record the result
- [ ] Finalize `DEPENDENCIES` / attribution: AprilTag (BSD-2), fonts (SIL OFL),
      original app — notices shipped with the build
- [ ] Final greyscale screenshot audit of every screen
- **Gate:** every shipped feature has passed both gates; all credit is in place.

---

## Suggested order & dependencies

`Phase 1 (fonts)` unblocks the mono work in `Phase 3`. `Phase 5 (glyphs)`
unblocks the full stop-state in `Phase 4` (Phase 4 can ship with a text label
first, then gain its glyph). Everything else is roughly independent — but keep
each phase to a single focused commit so the history stays reviewable, and run
both gates before each push.
