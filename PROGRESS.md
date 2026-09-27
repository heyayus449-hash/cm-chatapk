# CM-Chat — build progress

Native Android rebuild of CM-Chat (Kotlin + Jetpack Compose). Private
1:1 messenger in the same category as Briar / Cwtch. This file tracks
what's done, what's next, and decisions made so a fresh session can
continue.

## Stack / decisions
- Kotlin 2.1.0, Jetpack Compose (BOM 2024.12.01), AGP 8.7.3, Gradle 8.11.1.
- minSdk 24, targetSdk 34, compileSdk 35. Single `:app` module, package
  `org.cmchat.app`, single-activity + state-based `AppNav`.
- Font: Nunito (OFL) bundled as static regular/semibold/bold TTFs in
  `res/font`; license in `licenses/Nunito-OFL.txt`.
- Theme: Material3 dark, palette in `ui/theme/Color.kt`.
- CI: `.github/workflows/build.yml` — setup-java 17 temurin, builds both
  `assembleDebug` and `assembleRelease`, uploads `cm-chat-debug-apk`
  (installs on any phone, debug-signed), `cm-chat-apk` (release, unsigned
  for now), and the gradle build log. Green as of the icon commit.
- Release signing: not set up yet (release APK is unsigned). Stable-key
  signing via GitHub secrets is a later step; see "Signing TODO" below.

## Done
- Project skeleton, theme, Nunito font, glowing CM-Chat logo.
- Screens (visual, fake data): Lock (PIN keypad), Circle (contacts),
  Chat (Cerberus/Timer bar with hex-eye CerberusMark, bubbles, offline
  retry bubble, input), Settings (text-size slider, rows, wipe button).
- App launcher icon: seed-of-life flower + red ring + plus, transparent
  background. Adaptive icon (`mipmap-anydpi-v26`) for API 26+, legacy
  layer-list fallback (`mipmap-anydpi` -> `drawable/ic_launcher_legacy`)
  for API 24-25.

## Next
- Phase 2 (security core): libsodium (lazysodium-android + jna),
  Argon2id PIN->key, XChaCha20-Poly1305 secretbox vault, first-run PIN +
  Face creation, unlock flow with escalating delay, duress reverse-PIN
  wipe (protects the local vault under coercion — standard security
  feature), wire Circle to vault data, unit tests in CI.
- Later phases: Tor onion transport, chat over Tor, Cerberus/Kill timer,
  file transfer, hardening review.

## Signing TODO (needs the repo owner to click in GitHub)
Release APKs are currently unsigned. To ship a stable-key signed release
so new versions install over old ones, the owner must add repo secrets
(Settings -> Secrets and variables -> Actions -> New repository secret):
KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Never commit
the keystore to this public repo. Steps will be detailed when signing is
wired into the workflow.

## Known issues
- Text input and the settings slider are visual-only until later phases.
- Release APK unsigned until signing secrets are added.
