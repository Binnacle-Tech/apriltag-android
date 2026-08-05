# Binnacle fonts — setup

The Binnacle type system uses three faces, each with a fixed job:

| Role | Face | Used for |
| --- | --- | --- |
| display | Space Grotesk | Titles, headers, the app name |
| ui | Inter | Body text, labels, buttons |
| mono | IBM Plex Mono | Machine data — timestamps, FPS/latency, hex, paths |

All three are licensed **SIL OFL 1.1** (free to bundle and ship, notices required).

The theme currently builds and runs on system fonts. Follow the steps below to
switch to the real Binnacle faces. Nothing here changes app behavior — only type.

## 1. Download the font files

From Google Fonts, download and rename the static weights into `src/main/res/font/`
(Android resource names must be lowercase, digits and underscores only):

- [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) → `space_grotesk_medium.ttf`, `space_grotesk_bold.ttf`
- [Inter](https://fonts.google.com/specimen/Inter) → `inter_regular.ttf`, `inter_semibold.ttf`
- [IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono) → `ibm_plex_mono_regular.ttf`

## 2. Add the font-family resources

Create these three files in `src/main/res/font/`.

`bin_display.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:app="http://schemas.android.com/apk/res-auto">
    <font app:fontStyle="normal" app:fontWeight="500" app:font="@font/space_grotesk_medium" />
    <font app:fontStyle="normal" app:fontWeight="700" app:font="@font/space_grotesk_bold" />
</font-family>
```

`bin_ui.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:app="http://schemas.android.com/apk/res-auto">
    <font app:fontStyle="normal" app:fontWeight="400" app:font="@font/inter_regular" />
    <font app:fontStyle="normal" app:fontWeight="600" app:font="@font/inter_semibold" />
</font-family>
```

`bin_mono.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<font-family xmlns:app="http://schemas.android.com/apk/res-auto">
    <font app:fontStyle="normal" app:fontWeight="400" app:font="@font/ibm_plex_mono_regular" />
</font-family>
```

## 3. Apply the faces in the theme

In all three `styles.xml` copies (`values/`, `values-v21/`, `values-night/`), add
the UI face app-wide and give the toolbar title the display face.

Add to the `AppTheme` and `PreferencesTheme` styles:
```xml
<item name="android:fontFamily">@font/bin_ui</item>
<item name="fontFamily">@font/bin_ui</item>
```

Add this text appearance and point the toolbar at it:
```xml
<style name="TextAppearance.Binnacle.Toolbar.Title" parent="TextAppearance.Widget.AppCompat.Toolbar.Title">
    <item name="android:fontFamily">@font/bin_display</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textColor">@color/bin_text</item>
</style>
```
Then in `src/main/res/layout/main.xml`, on the `androidx.appcompat.widget.Toolbar`, add:
```xml
app:titleTextAppearance="@style/TextAppearance.Binnacle.Toolbar.Title"
```

(The mono face, `@font/bin_mono`, is for the diagnostics/machine-data readout —
wired up when the overlay/diagnostics pass lands.)

## 4. License notices (required)

Each Google Fonts download includes an `OFL.txt`. Keep them with the app. Add an
attribution entry (e.g. in the README's "Attribution & licenses" section or a
`DEPENDENCIES` file):

> Fonts: Space Grotesk, Inter, and IBM Plex Mono — © their respective authors,
> licensed under the SIL Open Font License 1.1.
