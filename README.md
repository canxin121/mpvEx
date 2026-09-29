![banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# mpvExtended
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/canxin121/mpvEx.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/canxin121/mpvEx/releases/latest)
[![GitHub all releases](https://img.shields.io/github/downloads/canxin121/mpvEx/total?logo=github&cacheSeconds=3600)](https://github.com/canxin121/mpvEx/releases/latest)
[![Privacy Policy](https://img.shields.io/badge/Privacy%20Policy-View-brightgreen?logo=shield)](https://canxin121.github.io/mpvEx/privacy-policy.html)


> **This repository is a personal fork of [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx).**
> Everything below describes the app itself; the upstream project is the original, and issues with this
> fork's build belong in [this fork's Issues](https://github.com/canxin121/mpvEx/issues).

**mpvExtended is a fork of [mpv-android](https://github.com/mpv-android/mpv-android), built on the libmpv library. It aims
to combine the powerful features of mpv with an easy to use interface and additional
features.**

- Simpler and Easier to Use UI
- Material3 Expressive Design
- Advanced Configuration and Scripting
- Enhanced Playback Features
- Picture-in-Picture (PiP)
- Background Playback
- High-Quality Rendering
- Network Streaming
- File Management
- Completely free and open source and without any ads or excessive permissions
- Media picker with tree and folder view modes
- External Subtitle support
- Zoom gesture
- External Audio support
- Search Functionality
- SMB/FTP/WebDAV support
- Custom Playlist management support

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/canxin121/mpvEx/issues) section.**

---

## Installation

### Stable Release
Download the latest stable version from the [GitHub releases page](https://github.com/canxin121/mpvEx/releases).

[![Download Release](https://img.shields.io/badge/Download-Release-blue?style=for-the-badge)](https://github.com/canxin121/mpvEx/releases)

This fork has not published a release of its own yet, so the badge above renders empty. Until it does,
the upstream listed under [Acknowledgments](#acknowledgments) is the place to get a build.

### Preview Builds
For testing purposes only

[![Download Preview Builds](https://img.shields.io/badge/Download-Preview%20Builds-red?style=for-the-badge)](https://canxin121.github.io/mpvEx/)

---

## Showcase
<div class="image-row" align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/player.png" width="98%" />
</div>

<div class="image-row" align="center" justify-content="space-between">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/folderscreen.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/videoscreen.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/about.png" width="23.5%"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/pip.png" width="23.5%"/>
</div>

<div class="image-row" align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/framenavigation.png" width="48.5%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/chapters.png" width="48.5%" />
</div>

---

## Building

### Environment variables

Open **Settings → Advanced → Environment variables** to see the built-in
variable and its current value:

| Name | Value |
| --- | --- |
| `MPVEX_CONFIG_DIR` | The MPV configuration storage folder selected in Advanced settings. A folder on primary device storage appears as a filesystem path; other document providers appear as their original tree URI. The variable is unset until a folder is selected. |

The built-in name is read-only. It is set before mpv loads scripts and C
plugins. The player copies configuration files from this selected folder to
its internal mpv directory before initialization. A document-provider URI is
not a filesystem path and cannot be passed directly to ordinary file APIs.
You can add your own process variables on the same settings page for
mpv, Lua scripts, and C plugins. Every value is written into the process
environment before libmpv starts, so a plugin reads it with `getenv()`. C
plugins have no settings of their own — select the `.so` files under
**Settings → Advanced → Manage C Plugins** and configure them through these
variables. Reopen the player to apply custom changes; restart the app to update
thumbnail services. Settings exports include custom values.

#### Variable references

A custom value can insert another variable:

| Syntax | Result |
| --- | --- |
| `${NAME}` | The value of another custom variable, or of a built-in one. |
| `${NAME:-fallback}` | The same, but the fallback text is used when the variable is unset or empty. The fallback may itself contain references, such as `${NAME:-${MPVEX_CONFIG_DIR}/cache}`. |
| `$$` | A literal dollar sign, so `$${NAME}` produces the text `${NAME}`. |
| `$` followed by anything else | Kept as written. `$HOME` and `https://host/$path` are not references. |

Only `${NAME}` is a reference. Shell forms such as `$NAME`, `${NAME-default}`,
`${NAME:+x}`, and `${NAME:0:2}` are not expanded; write the braces exactly as
shown.

References are resolved immediately before the player hands the environment to
mpv, for mpv, Lua scripts, and C plugins alike, so nothing reaches mpv
unresolved. A value whose references cannot be resolved (an unknown name, a
cycle, or a malformed reference) is not exported at all, and the Environment
variables page shows the reason next to that variable. Every other variable is
unaffected. Deleting a folder in Advanced settings unsets
`MPVEX_CONFIG_DIR`, so values referencing it report an error until you pick a
folder again or supply a `${NAME:-fallback}`.

References are looked up in your own custom values and in the built-in
variables. Nothing else is consulted, and no reference text is ever left
behind for mpv or a plugin to see.

### Mobile input.conf shortcuts

Configure on-screen buttons for mpv key bindings and trigger them from the
player's More menu or shortcut side panel. See [Mobile shortcuts](docs/mobile-shortcuts.md).

### Lua scripts

Select and edit scripts from **Settings → Advanced → Manage Lua Scripts**.
Scripts can also register actions for mobile shortcut buttons. See [Lua scripts](docs/lua-scripts.md).

### Prerequisites

- JDK 17
- Android SDK with build tools 34.0.0+
- Git (for version information in builds)

### APK Variants

The app generates multiple APK variants for different CPU architectures:

- **universal**: Works on all devices (larger size)
- **arm64-v8a**: Modern 64-bit ARM devices (recommended for most users)
- **armeabi-v7a**: Older 32-bit ARM devices
- **x86**: Intel/AMD 32-bit devices
- **x86_64**: Intel/AMD 64-bit devices

---

## Releases

### Setting Up Release Signing

Never commit the keystore or its passwords. `keystore.properties` and
`app/release/` are both git-ignored.

#### Signing local release builds

Create the keystore once:

```bash
mkdir -p app/release
keytool -genkeypair -v \
  -keystore app/release/release.jks \
  -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 \
  -alias mpvex -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=mpvEx, OU=mpvEx, O=mpvEx, L=Unknown, ST=Unknown, C=CN"
```

Then write `keystore.properties` in the repository root, pointing at it:

```properties
storeFile=app/release/release.jks
storePassword=...
keyAlias=mpvex
keyPassword=...
```

When that file exists, `assembleStandardRelease` (and the `preview` builds
derived from it) signs with it, and the APKs are named without `-unsigned`.
Keep the same keystore and the same `applicationId` for every release:
Android only accepts an update that is signed by the same key as the installed
app, so losing the keystore means users must uninstall before reinstalling.

#### Signing GitHub Actions builds

CI signs the unsigned APKs with `apksigner` after assembling, so it needs the
same key in four repository secrets:

1. Navigate to your repository on GitHub
2. Go to **Settings** → **Secrets and variables** → **Actions**
3. Add the following repository secrets:

| Secret Name              | Description                                          |
|--------------------------|------------------------------------------------------|
| `SIGNING_KEYSTORE`       | Base64-encoded keystore file (`.jks` or `.keystore`) |
| `SIGNING_KEY_ALIAS`      | The alias name used when creating the keystore       |
| `SIGNING_STORE_PASSWORD` | Password for the keystore file                       |
| `KEY_PASSWORD`           | Password for the key (can be same as store password) |

#### Encoding Your Keystore

To encode your keystore file to base64:

**Linux/macOS:**

```bash
base64 -i your-keystore.jks | tr -d '\n' > keystore.txt
```

**Windows (PowerShell):**

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-keystore.jks")) | Out-File -FilePath keystore.txt -NoNewline
```

Copy the contents of `keystore.txt` and paste it as the value for the `SIGNING_KEYSTORE` secret.
Delete `keystore.txt` afterwards; it holds the key in plain text.

### Creating a Release

1. Update `versionCode` and `versionName` in `app/build.gradle.kts`
2. Commit the changes
3. Create and push a tag:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
4. GitHub Actions will automatically build, sign, and create a draft release

### Creating a Preview Release

1. Create and push a preview tag:
   ```bash
   git tag -a v1.0.0-preview.1 -m "Preview release"
   git push origin v1.0.0-preview.1
   ```
2. GitHub Actions will create a pre-release automatically

---

## Acknowledgments

**Upstream project:** [marlboro-advance/mpvEx](https://github.com/marlboro-advance/mpvEx) — the original
mpvExtended this repository is forked from, together with the projects it in turn builds on. The upstream
author's funding links live in that repository; this fork neither collects nor redirects them.

- [mpv-android](https://github.com/mpv-android)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [Next player](https://github.com/anilbeesetti/nextplayer)
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)

---

## Star History <img src="https://raw.githubusercontent.com/Tarikul-Islam-Anik/Animated-Fluent-Emojis/master/Emojis/Travel%20and%20places/Star.png" alt="Star" width="25" height="25" />

<a href="https://www.star-history.com/#canxin121/mpvEx&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=canxin121/mpvEx&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=canxin121/mpvEx&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=canxin121/mpvEx&type=date&legend=top-left" />
 </picture>
</a>
