# Binnacle fonts

The three Binnacle faces are bundled in `src/main/res/font/`, each with a fixed job:

| Role | Family resource | Face | Used for |
| --- | --- | --- | --- |
| display | `@font/bin_display` | Space Grotesk (Medium/Bold) | Titles, the app name |
| ui | `@font/bin_ui` | Inter (Regular/SemiBold) | Body text, labels, buttons |
| mono | `@font/bin_mono` | IBM Plex Mono (Regular/Medium) | Machine data — FPS/latency, hex, paths |

Wiring:

- `bin_ui` is applied app-wide via `android:fontFamily` / `fontFamily` in `AppTheme`
  and `PreferencesTheme` (`res/values*/styles.xml`).
- `bin_display` is applied to the toolbar title via
  `TextAppearance.Binnacle.Toolbar.Title` (`res/layout/main.xml`).
- `bin_mono` is reserved for the telemetry readout — applied in roadmap Phase 3.

## License

All three families are licensed **SIL Open Font License 1.1**. Full notices are
bundled in `licenses/` (`OFL-SpaceGrotesk.txt`, `OFL-Inter.txt`,
`OFL-IBMPlexMono.txt`) and credited in the README.
