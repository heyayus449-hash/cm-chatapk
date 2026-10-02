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

- R3 (chat), compile + test green:
  - chat/ChatModels + ChatStore: RAM-only per-contact threads (never
    written to disk), message state SENDING/SENT/DELIVERED/OFFLINE,
    self-timer (off/30s/5m/1h counted from SEEN), last-seen buckets
    (<=60 "recently", <=180 "a while ago", else nothing), peer status,
    Team Hour, purgeExpired, clearAll.
  - MessageService: sendText/retry/sendErase/sendStatus + KNOCK_ACCEPT;
    handleIncoming now also boxOpens MSG (-> store + ACK), ACK (->
    DELIVERED), STATUS (-> peer status), ERASE_CHAT (-> erase) from known
    contacts. Accepted knocks are persisted to the vault as contacts
    (ContactRec gained cmId); Circle lists real contacts and chats key on
    the contact CM-ID.
  - ChatScreen rebound to ChatStore: real bubbles with delivery labels,
    dashed red Offline-Retry bubble with a once/30s countdown, self-timer
    chips, Erase (both sides), Team Hour line, peer status + last seen.
  - Tests 12/12: + last-seen bucketing, self-timer expiry, timer labels.
  - Device-only: real Tor delivery/ACK, and Team Hour cross-device sync
    (currently local; rides a later frame). Status is sent to contacts,
    never your own is shown.

- R4 (guardians / hardening / tools / metadata), compile + test green:
  - R4a guardians: GuardLogic (pure, tested) + GuardController — Cerberus
    idle wipe (touch/onResume resets; reopening from recents counts),
    Kill Timer, first-one-wins; on expiry silent RAM wipe (chats, tools),
    stop server + Tor, kill process; vault stays. Chat eye = real armed.
  - R4b Wipe Everything Now: confirm -> delete vault+salt+caches, clear
    RAM, fire ACTION_DELETE (uninstall) intent. Reverse-PIN unchanged.
  - R4c hardening: FLAG_SECURE, allowBackup=false + data-extraction/backup
    rules excluding all, R8 minify + resource shrink for release with
    keep/strip rules (Log stripped; JNA/lazysodium/tor/zxing/serialization
    kept). Release APK builds under R8 (~39 MB).
  - R4d tools dock (off by default, per-tool Settings toggle, all offline):
    Calculator (arithmetic), Notes (RAM-only, wiped on close/wipe),
    Converter (length/volume/mass). Calculator + Converter unit tested.
  - R4e metadata scrub: MetadataScrubber.stripJpeg removes APP1 (Exif/GPS/
    XMP) keeping APP0/JFIF + image data (pure, unit tested); AppSettings
    metadataScrub (default ON) + shareLastSeen (default ON) toggles. Added
    FILE_OFFER/CHUNK/DONE frame types.
  - Tests 19/19.
  - Deferred/device-only: real 100 MB file transfer over Tor (frame types
    reserved, UI not built — RAM/app-cache-chunk choice to be made on
    device); Team Hour cross-device sync; last-seen/status/metadata-scrub
    wiring into the live send path; FLAG_SECURE blanking, uninstall intent,
    minified-release runtime, and process kill are all device-only.
  - R5 (Bouncy Castle swap) intentionally NOT done — would risk the green
    crypto; revisit only with device testing.

- Diagnostics + onion ADD_ONION robustness + Cyrillic label:
  - diag/Diag: RAM-only ring buffer (~200), levels D/I/W/E, StateFlow;
    mirrors to Logcat in debug (R8 strips it in release); dropped
    undecryptable frames logged as COUNT only (never content/peer).
    Cleared on app close and every wipe path (Cerberus/Kill/Wipe Now).
    Settings -> Diagnostics screen (newest-first, Copy-all, Clear).
  - diag/CrashCatcher: DEBUG-PHASE aid — a default uncaught-exception
    handler writes ONE crash file to app-internal storage; shown on the
    Diagnostics screen next launch then deleted; wiped by every wipe path.
    Flag CrashCatcher.ENABLED (currently true) — MUST be set false / removed
    before any real-safety release (it is the only on-disk exception trace).
  - Onion: ADD_ONION runs off the main thread (ServerController IO scope,
    so no ANR); logs the command + full parsed reply keys. **Fix:** jtorctl
    returns the reply under keys `onionAddress` (base32 host, no scheme) and
    `onionPrivKey` ("ED25519-V3:..."), NOT "ServiceID" — that key mismatch
    was the whole publish bug. Now reads those keys; onion = addr + ".onion".
    Stores the priv key verbatim and reuses it next run (recreate falls back
    to the stored address if the reply omits it). A missing onionAddress /
    5xx is surfaced as a clear Failed(...) error. Priv-key blob never logged.
  - Errors wired into Diag at Tor status, onion publish/self-test, knock
    send, and dropped frames. Launcher label -> Cyrillic "СM-Chat"
    (sorts to the bottom); app_name stays Latin CM-Chat.
  - Debug + release (R8) both build; tests 19/19.

## Signing (still needs the repo owner)
The workflow is now wired for stable-key signing (see "Signing TODO" below
for the exact click-by-click steps). Until the four keystore secrets are
added in GitHub, release APKs build **unsigned** (`app-release-unsigned.apk`)
and nothing secret is stored in the repo. Test with the debug APK meanwhile.

## Invisible mode
- Settings toggle (AppSettings.invisibleMode, default OFF). When ON, the onion
  server is stopped and the accept loop refuses every incoming connection, so
  any probe (message, retry, buzz) sees us as OFFLINE. Outbound (SOCKS through
  Tor) is unaffected — you can still start conversations. Turning it off
  re-publishes the onion. Closes the "retry reveals a hidden-online user" leak.
- Note: our transport is one-shot per connection, so a reply "within an open
  session" isn't a separate path — invisible simply drops all inbound.

## BUZZ + scout listener
- New FrameType.BUZZ (12). Fire-and-forget: no ack, no retry, no read state,
  no content — so it can't act as a presence detector. Send has a 5-min
  per-contact cooldown (BuzzPolicy.SEND_COOLDOWN_MS).
- Receiver "Accept Buzz" frequency (Settings, taps to cycle): 1h / 12h / 24h /
  Once only. "Once only" = after one buzz from a person, no more accepted until
  you send them a message (BuzzPolicy.onMessagedContact clears it).
- Receive with the chat open: screen shake (Animatable offset) + vibration.
- Notifications are generic like original CM-Chat: a BUZZ = "Activity", a new
  MESSAGE = "Notification" (only when that chat isn't on screen). No sender
  name / no content by default; a setting ("Show sender name on alerts") can
  switch on the nickname. Cleared on every wipe path + on app close.
- Buzz UI: ⚡ Buzz chip in the chat (shows cooldown), long-press a contact in
  the Circle.
- Scout listener (buzz-through-when-closed): BuzzListenerService, a minimal
  foreground service that survives swipe-away with its own minimal "Listening"
  notification. On swipe (TorService.onTaskRemoved -> LifecycleController):
  if "Let a Buzz reach me when closed" is ON (default) and not Invisible, Tor +
  onion stay up, RAM is cleared, MessageService enters buzzOnlyMode (only a
  BUZZ -> "Activity"); else full close (stop server + Tor, clear RAM). Force-
  stopping in Android Settings kills even the listener (fully dark). Returning
  to foreground (onResume) ends buzz-only mode and stops the listener.
- Device-only: the actual swipe-survival, Tor reachability, shake/vibration,
  and notifications need a phone (CI verifies compile + crypto only).

## Rename CM-ID -> CMC-ID
- ID prefix `cm1:` -> `cmc1:` (CmId.PREFIX); all user-facing labels now say
  "CMC-ID" (My CMC-ID screen + Settings row, Knock hint/error, QR desc).
  No live users / no back-compat. Tests updated; 19/19.

## Next
- On device (two phones): PIN + Face, wait for Tor "Online", My ID/QR,
  Knock/Accept, chat both ways, offline retry, erase, self-timer, status,
  reverse-PIN wipe, My Server self-test. Then decide the file-transfer
  RAM-vs-cache approach and finish R4 files + live presence/scrub wiring.
- Later: chat over Tor, Cerberus/Kill timer, file transfer, hardening
  review (FLAG_SECURE, R8 log stripping, data-extraction rules).

## Signing TODO (needs the repo owner to click in GitHub)
The build + CI are wired: `app/build.gradle.kts` reads a keystore from the
`CMCHAT_KEYSTORE` env var (gated on the file existing), and the workflow
decodes it from a secret and passes the passwords in. So all that's left is
adding four secrets. **The keystore is never committed** — it lives only as
a GitHub secret. Do this once:

1. **Make a keystore** (on your own computer, needs a JDK/`keytool`):
   ```
   keytool -genkeypair -v -keystore cmchat.keystore \
     -alias cmchat -keyalg RSA -keysize 2048 -validity 10000
   ```
   It asks for a keystore password and a key password (you can use the same
   one) and a name/org (anything). Keep `cmchat.keystore` and the passwords
   somewhere safe and private — losing them means future versions can't
   install over old ones. **Do not add the keystore to git.**

2. **Base64-encode it** into a text blob for the secret:
   - macOS/Linux: `base64 -i cmchat.keystore | tr -d '\n' > cmchat.b64`
   - Windows PowerShell:
     `[Convert]::ToBase64String([IO.File]::ReadAllBytes("cmchat.keystore")) > cmchat.b64`
   Open `cmchat.b64` and copy all of it.

3. **Add the four secrets** on GitHub: repo → **Settings** → (left menu)
   **Secrets and variables** → **Actions** → **New repository secret**.
   Add each of these (name exactly, value = yours), clicking "Add secret"
   after each:
   - `KEYSTORE_BASE64` — the whole base64 blob from step 2
   - `KEYSTORE_PASSWORD` — the keystore password from step 1
   - `KEY_ALIAS` — `cmchat` (or whatever `-alias` you used)
   - `KEY_PASSWORD` — the key password from step 1

4. **Re-run the build**: Actions tab → latest run → "Re-run all jobs" (or
   just push any commit). The release artifact `cm-chat-apk` will now be
   `app-release.apk` (signed). Delete `cmchat.b64` afterwards.

If the secrets are absent the build still succeeds; the release is just
unsigned. Never paste the keystore or passwords into code, commits, or issues.

## Known issues
- Text input and the settings slider are visual-only until later phases.
- Release APK unsigned until signing secrets are added.
