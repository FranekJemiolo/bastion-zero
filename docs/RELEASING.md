# Releasing

## Android
CI uploads a debug APK on every push to `main`. Release signing (keystore in GitHub Secrets) is not configured yet.

## iOS / TestFlight (hook only)
`ios-build.yml` has a `testflight` job that runs on `v*` tags and skips unless `ASC_KEY_ID` is set. To finish it you need, as repo secrets: App Store Connect API key (`ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY`), a distribution certificate and provisioning profile. Then add `xcodebuild archive` / `-exportArchive` and an upload step. Never commit signing material to this public repository.
