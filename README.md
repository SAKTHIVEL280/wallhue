# Wallhue — Material You wallpaper colors for Samsung One UI

**Pick a wallpaper, tap Apply, and Google / stock Android apps instantly follow its Material You colors.**
No root. Nothing running in the background. Works on Samsung Galaxy phones with One UI (Android 14+).

Wallhue is a free, open-source alternative to always-on color tools: it sets your wallpaper, extracts
the best seed colors from it, and hands them to Android's own Material You (Monet) engine — only when you tap Apply.

## Features
- 🎨 **Wallpaper-based Material You colors** for Google apps (Messages, Phone, Clock, Photos, Gboard…)
- 🖼️ Set the wallpaper on **home, lock, or both** from the same screen
- 🎯 Choose from up to **4 seed colors** extracted from your wallpaper (Material Color Utilities)
- ↩️ **One-tap reset** restores your original system colors exactly
- 🔋 **Zero background usage**: no service, no listener, no battery drain
- ✨ Built with **Material 3 Expressive**: expressive motion, shape morphing, loading indicator
- 🔓 **No root**: one-time permission grant via [Shizuku](https://shizuku.rikka.app/) or adb, and it survives reboots

## Install
1. Download the latest APK from [Releases](https://github.com/SAKTHIVEL280/wallhue/releases).
2. Open Wallhue and tap **Setup needed**, then grant the permission once:
   - **With Shizuku:** start Shizuku, then tap *Grant with Shizuku*, or
   - **With a computer:** `adb shell pm grant dev.fl3xy.wallhue android.permission.WRITE_SECURE_SETTINGS`
3. Pick a wallpaper, choose a color, and tap **Apply wallpaper & colors**.

## How it works
Wallhue quantizes the wallpaper (Celebi quantizer + Score from Material Color Utilities), and writes the chosen
seed to Android's `theme_customization_overlay_packages` setting. The system's own Monet engine then generates
the full tonal palette. That's the same mechanism the stock wallpaper picker uses. Reset restores the previous value.

## Requirements
- Android 14 or newer (tested on Samsung Galaxy S25, One UI on Android 17)
- Shizuku **or** adb, once

## Build
```
./gradlew assembleRelease
```

## License
MIT

---
Keywords: Material You, Monet, dynamic color, wallpaper colors, One UI, Samsung, Pixel colors, Android 17, no root, Shizuku, ColorBlendr alternative, theming
