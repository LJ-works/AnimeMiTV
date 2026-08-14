# Issue #41 acceptance checklist

- [ ] Install the app on an Android TV device or emulator and confirm the launcher shows the Anime1 mark.
- [ ] Confirm the Android TV home screen uses the 16:9 Anime1 banner without clipping or visible stretching.
- [ ] Verify the launcher icon at mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi densities.
- [ ] Confirm both `LAUNCHER` and `LEANBACK_LAUNCHER` entry points resolve to the expected icon/banner resources.
- [ ] Run `./gradlew test` and `./gradlew assembleDebug` successfully.

The source mark is Anime1's highest-resolution browser/app icon, `android-chrome-256x256.png`, fetched from `https://anime1.me/`.
