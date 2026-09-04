# FreeTunnel for Android

Native Android client using the official TrustTunnel Android adapter. Minimum Android version is 8.0 (API 26).

## Features

- One-tap connect/disconnect and live VPN state.
- Import complete TrustTunnel TOML files or `tt://` links.
- Multiple local configurations.
- Split tunneling by domains/IPs in `general` and `selective` modes.
- Android route exclusions for local/private networks.
- Dark FreeTunnel interface and Russian-first copy.
- Foreground VPN service, network recovery, logs and Android always-on VPN compatibility.

The Android split-tunneling screen mirrors the desktop client's destination-based model. It does not select Android applications individually.

## Build

The core AAR is hosted in GitHub Packages, which requires a GitHub token with `read:packages` even though the project is public:

```bash
cd android
./gradlew assembleRelease -Pgpr.user=YOUR_GITHUB_USER -Pgpr.key=YOUR_GITHUB_TOKEN
```

The result is `app/build/outputs/apk/release/app-release.apk`. Tagged builds named `android-v*` publish the APK and its SHA-256 checksum to GitHub Releases automatically.

For CI, add a repository secret named `GH_PACKAGES_TOKEN` containing a classic GitHub PAT with `read:packages`. The workflow also tries its built-in token, which works when the upstream package grants this repository access.

Public CI APKs use Android's standard development key so they are directly installable. Use a protected long-lived signing key before Play Store distribution.
