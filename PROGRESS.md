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
- Screens (visual, fake data): Circle (contacts), Chat (Cerberus/Timer
  bar with hex-eye CerberusMark, bubbles, offline retry bubble, input),
  Settings (text-size slider, rows, wipe button).
- App launcher icon: seed-of-life flower + red ring + plus, transparent
  background. Adaptive icon (`mipmap-anydpi-v26`) for API 26+, legacy
  layer-list fallback (`mipmap-anydpi` -> `drawable/ic_launcher_legacy`)
  for API 24-25.
- Phase 2 security core:
  - `crypto/CryptoManager.kt` — libsodium via lazysodium. Argon2id
    (crypto_pwhash, 16-byte per-vault salt, interactive limits 2 / 64 MiB)
    for PIN->key; crypto_secretbox_easy (XSalsa20-Poly1305, the libsodium
    "secretbox") for vault sealing, framed nonce||ciphertext; crypto_box
    (X25519) keypair per Face. No hand-rolled crypto.
  - `vault/Vault.kt` — one encrypted `vault.dat` + `salt.dat` in
    app-internal storage; VaultData JSON (faces, contacts, settings) via
    kotlinx.serialization. Messages are never persisted. wipe() overwrites
    then deletes (best-effort; flash wear-levelling is not defeated).
  - `vault/VaultManager.kt` — first-run create, unlock, duress. Duress:
    on a failed unlock the reversed input is tried; if it opens the vault
    the real PIN was entered backwards -> wipe + return to first-run,
    silently. No PIN is ever stored. Palindrome PINs rejected so
    reverse != forward.
  - `LockScreen` reworked into first-run (new PIN + confirm + Face name)
    and unlock flows; wrong PIN shows an error and, after 5 tries, an
    escalating countdown lock. Duress silently resets to first-run.
  - Circle now renders contacts from the decrypted vault; the sample
    Circle shows only when the vault has no contacts yet.
  - Unit tests (`app/src/test`, run in CI before the APK build): vault
    round-trip, wrong PIN fails, reverse-PIN detected as duress + wipe,
    palindrome rejected, tampered ciphertext rejected. Run on the host JVM
    via lazysodium-java (same code path as on-device lazysodium-android).

Decisions: "secretbox (XChaCha20-Poly1305)" in the brief is implemented
with libsodium's actual secretbox primitive (XSalsa20-Poly1305) — the
standard, correct choice; XChaCha20-Poly1305 is reserved for the message
crypto_box layer in a later phase. Argon2id uses interactive limits so
unlock stays usable on low-RAM phones. R8/minify stays OFF this phase so
JNA/libsodium aren't stripped; turning it on with keep rules is a later
hardening step.

## Next
- Phase 3: Tor onion transport (tor-android + jtorctl), one onion service
  per Face, My ID/QR, Knock/Accept, My Server screen.
- Later: chat over Tor, Cerberus/Kill timer, file transfer, hardening
  review (FLAG_SECURE, R8 log stripping, data-extraction rules).

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
