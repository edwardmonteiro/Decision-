# Direct Android operation — v0.2.0

## Setup

Install the signed APK. Open Configurações → Área dos responsáveis → solve the gate → CONECTAR OPENAI. Enter your own API key in the native dialog. No backend URL, device token, Python server or hosting account is required.

Connection performs a billable Decisions probe and, subject to access, creates/reuses a Vault and starts mission preparation. The key stays in Android's private preferences encrypted with an Android Keystore AES-GCM key. Backups and WebView debugging are disabled. It is not sent to JavaScript or stored in the OpenAI Vault. Do not put keys in chat, issues or source control.

The arithmetic parent gate prevents casual child access; it is not authentication. Use the device lock to control access. The BYOK design is intended for a parent's personal device, not distribution of a shared secret to customers.

## Real statuses

The parent panel reports Decisions, Session, Environment and Vault independently, including provider resource IDs and the last verification time. HTTP 401, 403, 404 and 429 have distinct messages. A saved key alone does not establish readiness.

Readiness requires a valid Decisions response, a created/verified Vault, a connected hosted environment and a validated mission from a completed root turn. These are historical checks; the completed planning session is promptly deleted to release resources. “Sandbox validado e encerrado” is intentional, not a permanently running environment. A new plan temporarily shows its current preparation state.

The Vault is attached to each managed session and deliberately contains no external credentials. The self-contained sandbox has no network access or external catalog dependency. Adding dummy credentials would serve no purpose.

## Data and gameplay

Only six integer metrics reach Decisions: wave, collected stars, collisions, shots, cleared asteroids and elapsed seconds. Player names, photos, voice, locations and device identifiers are not collected. The agent sees fixed instructions for a numeric mission, not a conversation with the child.

Mission acceptance requires exactly three fields: approved mission_id, eight star_lanes within [0.12, 0.88] and asteroid_speed within [0.8, 1.1]. Neighboring lanes may differ by at most 0.4. Every value is validated natively and again before gameplay. Generated text/code is never executed on Android.

Decisions chooses gentle, steady or bright. Bright is clamped to steady below four collected stars or after any collision. Timing changes apply between waves, so calls cannot block rendering or touch input. No online response is necessary to finish a round.

## Quotas and lifecycle

- Local persistent caps: 24 Decisions requests and 12 session requests per UTC day, scoped to the entered key. Reinstalling/clearing app data resets local counters; these are not provider-enforced spending limits.
- At most two active local rounds and one planner. Per-wave Decisions answers are cached. The next mission is cached until consumed.
- Agent polling lasts up to 110 seconds plus in-flight HTTPS time. Transport connect timeout is 10 seconds and read timeout is 18 seconds. No blind retry of billable POST requests.
- Sessions are deleted after success/failure and on orderly activity teardown. Pending known IDs are retried after restart or a new test. Already-deleted sessions are treated as cleaned up.
- Android may kill the process before cleanup. The saved ID enables retry on reopening; a session-creation timeout before receiving its ID cannot be automatically reconciled. Check the Platform session list after interrupted preparation.
- Changing/removing a key attempts cleanup with the old key. The reusable empty Vault remains in the account. Remove it in Platform if no longer wanted.
- Removals do not promise secure memory erasure or remote history deletion. Provider retention and billing follow the account's settings.
- Check account budgets and service access in Platform. Request caps do not guarantee a maximum currency amount.

## Verification still required on the actual account/device

The repository's tests use fixture responses; no API key was available to the builder. Successful compilation is not a physical-device test. After entering your key, inspect real statuses and IDs, play an AI-labelled mission, and confirm Decisions results and session cleanup in Platform. If an API is unavailable to the account, the app reports it and continues with local rules.

On the target Android phone, verify installation, touch, sound, system bars and background/resume. Disable connectivity mid-round to confirm uninterrupted local play. Review the account's data controls and applicable children's-product requirements before public distribution.

## Legacy backend

`server/` preserves the v0.1 backend implementation and its fixture tests for reference. It is not started or contacted by v0.2. Its deployment variables and catalog-credential provisioning script are unrelated to direct mobile setup.

## Signing

Retain the original private release keystore and password to sign updates. CI secrets: LUMI_KEYSTORE_BASE64 and LUMI_STOREPASS. Never publish these in the repository or release assets.
