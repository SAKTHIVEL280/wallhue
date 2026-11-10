# Wallhue

Pick a wallpaper, tap apply, and Google / stock Android apps follow its Material You colors — while One UI keeps its own palette. Nothing runs in the background.

- Kotlin + Jetpack Compose, Material 3 Expressive (expressive motion, MaterialShapes, LoadingIndicator)
- Colors extracted with Material Color Utilities (Celebi quantizer + Score, Tonal Spot scheme)
- Applied as fabricated overlays on `android` through [Shizuku](https://shizuku.rikka.app/) (no root)

## Use
1. Start Shizuku (wireless debugging or `adb`), open Wallhue and grant access.
2. Pick a wallpaper, choose a seed color, tap **Apply wallpaper & colors**.
3. The reset button removes Wallhue's color overlays.

Requires Android 14+.

## Build
```
./gradlew assembleDebug
```
