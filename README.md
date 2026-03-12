# 📋 ClipVault — Native Android Clipboard Manager

**Version 2.0 · Targets Android 16 (API 36) · Min Android 8.0 (API 26)**

---

## Features
- 🫧 **Floating bubble** — draggable overlay that snaps to screen edges
- 🔄 **Auto clipboard capture** — monitors every text/link/number you copy
- 📸 **Auto screenshot detection** — saves screenshots as they appear in gallery
- 📋 **History** — up to 300 items with timestamps
- 🔍 **Search & filter** — Text / Links / Numbers / Images / Screenshots
- ⚡ **One-tap Copy** — in both the main app and the floating panel
- 📤 **Share** — share any clip via any installed app
- 🔁 **Boot persistence** — service restarts automatically after reboot

---

## How to Build the APK

### Prerequisites
- **Android Studio Hedgehog (2023.1.1)** or newer  
  Download: https://developer.android.com/studio
- JDK 17 (bundled with Android Studio)
- Android SDK with **API 36** platform (install via SDK Manager)

### Steps

1. **Open the project**
   - Launch Android Studio → "Open" → select the `ClipVault` folder

2. **Sync Gradle**
   - Studio will prompt "Gradle sync needed" → click **Sync Now**
   - Wait for all dependencies to download (~2 min first time)

3. **Build the APK**
   
   **Option A — Debug APK (easiest, installable directly):**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

   **Option B — Command line:**
   ```bash
   cd ClipVault
   ./gradlew assembleDebug
   ```

4. **Install on your phone**
   - Enable "Install from unknown sources" on your Android device  
     (Settings → Apps → Special app access → Install unknown apps → Files/Chrome → Allow)
   - Copy the APK to your phone and tap to install  
     **OR** connect via USB and run:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## First-Time Setup on Android

When you first open ClipVault:

1. **Allow "Display over other apps"** — required for the floating bubble
   - The app will open Settings automatically; toggle the permission ON
   
2. **Allow Notifications** — for clipboard capture alerts (Android 13+)

3. **Allow Media access** — for screenshot detection

The floating 📋 bubble will appear on your screen. **Drag it anywhere.**

---

## Architecture Notes

| File | Purpose |
|------|---------|
| `ClipVaultService.kt` | Foreground service: clipboard listener + screenshot observer + floating overlay |
| `MainActivity.kt` | Main history UI with search, filters, copy & share |
| `ClipAdapter.kt` | RecyclerView adapters for both main list and floating panel |
| `ClipEntry.kt` | Data model + SharedPreferences storage (up to 300 items) |
| `BootReceiver.kt` | Restarts service after device reboot |

---

## Android 16 Compatibility

- `targetSdk 36` with `enableOnBackInvokedCallback` for predictive back gesture
- `TYPE_APPLICATION_OVERLAY` for the floating window (required since API 26)
- `foregroundServiceType="specialUse"` per Android 14+ requirements
- Tiered media permissions: `READ_MEDIA_IMAGES` (API 33+), `READ_MEDIA_VISUAL_USER_SELECTED` (API 34+), `READ_EXTERNAL_STORAGE` (≤API 32)
- Modern `ActivityResultContracts` API — no deprecated `startActivityForResult`
