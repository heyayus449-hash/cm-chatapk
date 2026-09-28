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
- CI: `.github/workflows/build.yml` — setup-java 21 temurin (lazysodium-java
  used by the host unit tests needs JDK 21+), runs the unit tests then builds both
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

- Phase 3.1 Tor foreground service:
  - Deps: info.guardianproject:tor-android:0.4.9.5 (0.4.9.6+ demand
    compileSdk 36/37 which AGP 8.7.3 rejects, so pinned to 0.4.9.5 which
    has no compileSdk floor) + jtorctl 0.4.5.7 + kotlinx-coroutines 1.9.0.
    A transitive kotlin-stdlib 2.3.0 is force-pinned to 2.1.0 to match the
    compiler.
  - `tor/TorService.kt`: foreground service (min-importance "Active"
    notification) that starts + binds the library TorService, listens for
    its status broadcasts, and polls the control port
    (status/bootstrap-phase) for %. Exposes `TorService.status:
    StateFlow<TorStatus>` = Starting / Connecting(%) / Online / Offline.
  - Manifest: INTERNET, FOREGROUND_SERVICE(+DATA_SYNC), POST_NOTIFICATIONS;
    service declared with foregroundServiceType=dataSync.
  - Circle header shows a status dot + label (grey Offline / orange
    Connecting x% / green Online) and a "first launch can take 1-3 min"
    note while connecting. Tor is started on unlock.
  - NOTE: debug APK ~47 MB because tor-android bundles the tor binary for
    all 4 ABIs; a release ABI split is a later step. Tor reaching ONLINE,
    the notification, and bootstrap-% wiring can only be verified on a real
    device (no emulator in CI); this commit is compile- + unit-test-green.

- Phase 3.3/3.4 cores (testable, done):
  - `crypto/CmId.kt` — CM-ID = "cm1:" + base32([onionLen][onion][32-byte
    identity pubkey]); encode/decode with a self-contained RFC4648 base32.
  - `crypto/CryptoManager` — added crypto_box seal/open (X25519 +
    XChaCha20? no: crypto_box = X25519+XSalsa20-Poly1305) for frames.
  - `transport/Frame.kt` — FrameType enum (KNOCK, KNOCK_ACCEPT, MSG, ACK,
    STATUS, ERASE_CHAT, PING, PONG) + FrameCodec seal/open (nonce||cipher
    of [type][payload]); unopenable frames dropped.
  - `transport/Transport.kt` — length-prefixed read/write, SOCKS5-through-
    Tor connect with UNRESOLVED host (Tor resolves .onion), loopback
    ServerSocket. Socket paths are compile-only until wired on device.
  - Tests now 8/8: + CM-ID round-trip, malformed CM-ID rejected, frame
    seal/open round-trip, tampered frame rejected.

- R1 complete (onion service + My Server), compile + test green:
  - Face gained onionKey/onionAddress (v3 endpoint key), stored in the
    vault SEPARATE from the messaging identity key (Home Node friendly).
  - `tor/ServerController.kt`: publishes a v3 onion (ADD_ONION via jtorctl,
    virtual port 80 -> random loopback ServerSocket) for the active Face;
    reuses the stored key or asks Tor to generate one (NEW:ED25519-V3) and
    returns it to persist. ServerStatus StateFlow Off/Starting/Online/
    Failed; stop (DEL_ONION + close), restart, and self-test (connect to
    own onion through Tor, OK/FAIL + ms).
  - TorService exposes controlConnection() + socksPort().
  - `ui/screens/MyServerScreen.kt`: status steps, onion address, Face,
    live uptime, Start/Stop/Restart + Self-test. Reached from Settings.
  - AppNav now keeps the PIN for the session (needed to save the vault),
    starts the server when Tor is ONLINE, and persists a freshly-generated
    onion key back into the vault.
  - Onion accept loop currently accepts + closes (frame handling is R2).

- R2 (transport + My ID + Knock), compile + test green:
  - ZXing added (core 3.5.3 + journeyapps zxing-android-embedded 4.3.0).
  - CryptoManager: crypto_box_seal / seal_open (anonymous sealed box) for
    KNOCK, since the knocker isn't in the Circle yet so there's no shared
    key; everything else stays crypto_box between known identities.
  - transport/MessageService: onion accept loop -> read length-prefixed
    frame -> sealedOpen (KNOCK) -> KnockPayload -> incomingKnocks flow;
    sendKnock connects SOCKS5-through-Tor and writes a sealed KNOCK;
    accept/decline. ServerController.onIncoming hands sockets to it.
  - Messages.kt: KnockPayload / TextPayload / StatusPayload (JSON).
  - ui/screens/MyIdScreen: CM-ID (from active Face onion + identity pubkey)
    as selectable text + a ZXing QR bitmap + Copy + Share.
  - ui/screens/KnockScreen: paste a CM-ID or scan a QR (zxing ScanContract
    camera), pick a nickname, Send Knock. Circle shows incoming knocks
    with Accept/Decline.
  - Tests 9/9: + knock sealed-box round-trip (only the recipient's key
    opens it).
  - Device-only: camera scanning and real Tor delivery of the KNOCK are
    not exercised in CI. KNOCK_ACCEPT delivery + persisting accepted
    contacts land with R3 chat.

## Next
- R3 chat: 1:1 text over Tor (MSG/ACK), RAM-only; offline retry bubble;
  Erase (ERASE_CHAT); self-timer; status; last seen (per chat); Team Hour.
  Wire KNOCK_ACCEPT + save accepted contacts to the vault.
- R4 guardians/files/tools/hardening. R5 (optional) Bouncy Castle swap.
- On-device: Tor ONLINE, onion publish, My Server self-test, a real
  knock A<->B (not verifiable in CI — no emulator).
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
