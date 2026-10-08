# LUMI Travessias v0.3.0 — direct Android operation

## Update and activation

Install LUMI-Travessias-v0.3.0.apk over LUMI Orbit. The package remains com.edward.lumi, with versionCode 4 and the original signing certificate. Android Keystore alias and SharedPreferences name are unchanged. Do not uninstall if preserving the key. Old space mission caches/statuses are migrated away; Vault ID, key and daily counters remain. Old space scores are left untouched in their separate browser storage key.

Open Configurações → Área dos responsáveis → Conectar OpenAI. Existing keys work without re-entry. Testing classifies a bundled bridge drawing and creates/reuses the Vault before planning a river. When the parent has already configured a Vault, opening the new game can prepare the first challenge directly.

No backend is deployed or required. All provider calls use fixed HTTPS paths at api.openai.com. The native dialog protects key entry from screenshots, encrypts the key with Android Keystore AES-GCM, and never exposes it to JavaScript. Backups and WebView debugging are disabled. This is a personal-device BYOK app, not a distribution mechanism for shared developer keys.

## Classification contract

POST /v1/decisions with gpt-6-luna, one user input containing fixed input_text and input_image with an inline PNG data URL. The question name is solution; allowed choices are bridge, boat, jump, unclear. The image is treated as untrusted content and cannot supply commands to the application. Only the allowlisted choice and bounded numeric confidence affect play.

Native bridge input accepts only attempt_id plus image. Maximum serialized body 360,000 characters; PNG data URL maximum 350,000 characters; dimensions 16–768 pixels. The client produces 512×320 black-on-white strokes. No camera, photo-picker or full-screen capture is involved. An empty/tiny gesture is caught before sending.

No JSON resource ID is expected in a Decisions response. HTTP x-request-id is optional diagnostic evidence. Confidence <0.55 becomes unclear. A refusal, invalid schema or network error cannot become a successful classification. Native results are cached by attempt ID to avoid duplicate billable calls for an unchanged attempt.

## Challenge contract

Agents receives up to eight bounded history records: attempted solution, source (Decisions/manual), local success, and the prior challenge parameters. Drawings are not sent to Agents. Manual attempts performed while disconnected stay in local game history and are not retroactively uploaded when connecting.

The agent returns exactly width/current/cargo/theme/focus/reason. Width is [0.24,0.50]; current calm/fast; cargo boolean; theme meadow/sunset/night; focus bridge/boat/jump; reason first/repeat_gently/try_new/more_room. Local validation verifies that focus can cross according to the game rules. Native code recalculates each outcome rather than trusting a JavaScript success flag.

A sequence number associates each prepared challenge with the history that produced it. Results generated from an older sequence are discarded, and a new plan can be scheduled after cleanup. GET does not consume a challenge; recording an outcome invalidates the cached challenge. This prevents speculative planning from ignoring the player's latest solution.

Managed sessions use gpt-6-astra, a small openai_hosted environment and disabled networking. The prompt instructs Python generation/validation. The app verifies connected environment status, a completed root turn and validated final output; it does not independently audit every tool execution. The reusable empty Vault is attached, with no dummy credentials.

## Limits and resource lifecycle

- Persistent local daily quotas remain 24 Decisions requests and 12 session requests per UTC day, scoped to the key. Reinstallation/clearing data resets local counters; they are not server-enforced dollar budgets.
- One planner at a time. Planning polls for up to 110 seconds plus in-flight requests. Classification uses 10-second connection and 35-second read timeouts; other provider requests use an 18-second read timeout.
- Session DELETE after success/failure; known pending IDs are retried on restart or a new service test. A 404 on deletion counts as already cleaned up.
- A process kill or POST timeout before receiving a session ID can leave a remote resource requiring Platform inspection. There are no blind billable POST retries.
- Removing/changing the key attempts cleanup with the old key. The empty Vault remains in the account until manually removed.
- Sandbox completion does not mean a permanently running environment. The parent panel's “validado e encerrado” is intentional.

## Privacy and offline behavior

The parent’s instruction authorizes recognition by explicitly pressing Experimentar. Only drawing strokes leave the device for Decisions. Avoid personal data in drawings. The app does not persist raw images, log API keys or collect names, voice, location or device identifiers. Native TTS reads fixed game instructions; availability depends on the installed engine/voice.

When AI is unavailable, the user explicitly selects bridge/boat/jump. UI and history label this as manual. Local challenges, game rules, rendering, sound and local progress remain functional. The game does not claim local image recognition.

The parent gate is a casual child-access barrier, not authentication. Provider data retention and billing follow the account settings. Review applicable children's-product requirements before public distribution.

## Verification boundary

Native contracts and browser flows were tested with fixtures. The prior user's screenshot established v0.2.0 Session/Environment/Vault operation, not the new v0.3.0 visual classifier's accuracy. Live recognition/adaptive prompting and physical Android/TTS checks remain to be performed with the user's configured account/device.

Signing secrets remain outside the repository. CI secrets: LUMI_KEYSTORE_BASE64 and LUMI_STOREPASS. server/ is an unused legacy reference.
