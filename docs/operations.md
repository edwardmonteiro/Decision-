# LUMI Travessias v0.5.0 — direct Android operation

## Update and activation

Install LUMI-Travessias-v0.5.0.apk over the existing LUMI app. The package remains com.edward.lumi, with versionCode 6 and the original signing certificate. Android Keystore alias and SharedPreferences name are unchanged. Do not uninstall if preserving the key. Old space mission caches/statuses are migrated away; Vault ID, key and daily counters remain. Old space scores are left untouched in their separate browser storage key.

Open Configurações → Área dos responsáveis → Conectar OpenAI. Existing keys work without re-entry. Testing classifies a bundled bridge drawing and creates/reuses the Vault before planning a river. When the parent has already configured a Vault, opening the new game can prepare the first challenge directly.

No backend is deployed or required. All provider calls use fixed HTTPS paths at api.openai.com. The native dialog protects key entry from screenshots, encrypts the key with Android Keystore AES-GCM, and never exposes it to JavaScript. Backups and WebView debugging are disabled. This is a personal-device BYOK app, not a distribution mechanism for shared developer keys.

## Curriculum operation

`levels.js` defines six levels and three goals per level. `lumi-levels-v1` stores six star counts and selected level independently of existing game preferences/history. Bounds and sequential unlocks are normalized on load. There is no migration of historical generic wins into stars.

Every challenge owns its level, round, allowed solution rule, original mechanics, award flag and failure count. A new round follows earned progress; one round earns at most one star. Replays cannot farm progress. Recognition correction revokes an award owned by that result before re-evaluation. Failed crossings never revoke earlier stars.

Crossing success remains the existing deterministic native-compatible rule. Curriculum-goal success is local and may differ: a bridge physically crosses a boat lesson but does not complete its learning goal. The UI makes this explicit. The native outcome retains physical success and the actual six-field river; no new API payload fields are introduced.

Agents candidates remain strictly validated, then are projected into the lesson's width band and required current/cargo. Focus is replaced with a feasible solution satisfying the lesson. The UI attributes both Agents and local rules. Late initial results cannot overwrite a started drawing, attempted lesson or awarded stage. Level changes invalidate pending fetches. Duplicate next taps are guarded while waiting for outcome synchronization.

Mechanical voice changes enter free play; original width/current/cargo equality controls star eligibility. Cosmetic theme changes stay eligible. Return to challenge and undo restore the original conditions. Two failures (including uncertain recognition) reveal a lesson-specific drawing hint. The voice get_river result includes level, goal, star count, eligibility and this hint.

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

The parent’s instruction authorizes recognition by explicitly pressing Experimentar. Only drawing strokes leave the device for Decisions. Avoid personal data in drawings. The app does not persist raw images, log API keys or collect names, location or device identifiers. Voice is streamed only after explicit activation, as described below. Native TTS reads fixed game instructions; availability depends on the installed engine/voice.

When AI is unavailable, the user explicitly selects bridge/boat/jump. UI and history label this as manual. Local challenges, game rules, rendering, sound and local progress remain functional. The game does not claim local image recognition.

The parent gate is a casual child-access barrier, not authentication. Provider data retention and billing follow the account settings. Review applicable children's-product requirements before public distribution.

## Live voice contract

POST `/v1/live/sessions` JSON contains a native-owned `session` config and `transport:{type:"webrtc",sdp}`. The browser supplies only the SDP and a validated six-field river. The API key stays in native HTTPS. A successful response provides `session.id` and `transport.sdp`; only these and the model label cross the bridge. The WebRTC data channel is created before SDP generation; ICE gathering completes before sending. HTTP creation starts Live: no `session.start` is sent. UI readiness requires `session.started`.

Model `gpt-live-1`, voice `bossa`, `store:false`, Responses delegation to `gpt-6-luna` with low reasoning and three strict tools: `change_river`, `get_river`, `end_voice`. The config is not served as a browser asset. Frontend data-channel client events are limited to session close, instructions/context append and Responses continuation/results. No browser session reconfiguration, web search, generated code or arbitrary URL execution is exposed.

`change_river` accepts one enum command. UI independently validates it and blocks changes outside drawing, during a pointer stroke, in menus and in the background. It preserves strokes, repairs the proposed focus if necessary, invalidates a stale initial Agents response, and records up to ten local undo snapshots for this river. Completed game attempts retain voice-modified conditions in their normal outcome payload. No audio/transcript is sent to the Agents planner.

Read function calls from nested `response.output_item.done`, collect them by delegation/current response, and apply only after `response.completed`. Partial arguments, failed/incomplete responses, duplicate call IDs, invalid tools and extra parameters cannot mutate the scene. Send one `response.item.create` output per executed call and explicitly continue with `response.create`. At most 24 function calls per conversation. This guard and prompts bound game actions; speech quality and request understanding remain model-dependent.

## Microphone and voice lifecycle

`RECORD_AUDIO` is requested from the Falar flow. The WebChrome permission gate accepts only audio capture for the fixed local HTTPS origin, with a native activation flag, foreground state and Android permission. No camera permission. Android TTS is suppressed while the live conversation is active. Microphone capture uses echo cancellation/noise suppression. If autoplay is blocked, a visible button retries playback.

A native timer caps activation at 120 seconds; a WebView timer also ends media after 115 seconds of capture. Six creation attempts per UTC day per key are persisted; failures count and there are no blind POST retries. The first two-minute ceiling includes setup, so usable conversation can be shorter. Quotas are local safeguards, not billing guarantees.

Stopping disables/stops local tracks and pauses/detaches playback immediately, sends `session.close`, and attempts native HTTP hangup on the known ID. The app keeps the event channel briefly for `session.closed`; that event confirms finalization. Otherwise it closes transport without claiming confirmed final server usage. Known pending IDs survive failed native cleanup and are retried before allowing another creation. Cancelled in-flight creations are closed if their ID arrives later. Like Agents, a lost creation response without an ID cannot be cleaned up by the application. Native cleanup and process-kill behavior need physical-device verification.

The native permission dialog may temporarily pause the activity. Before permission resolves, there is no audio capture; the voice startup can survive that pause. Once active, backgrounding, opening a game menu, changing/removing the key, spoken stop, errors and timeouts stop the voice. Captions exist only in bounded memory and are cleared on cleanup. Live store=false does not replace the provider's data-retention policies.

## Verification boundary

Native contracts and browser flows were tested with fixtures. The prior user's screenshot established v0.2.0 Session/Environment/Vault operation, not the new v0.5.0 visual classifier's accuracy. Live GPT-Live speech, recognition/adaptive prompting and physical Android/WebRTC/permission/TTS checks remain to be performed with the user's configured account/device.

Signing secrets remain outside the repository. CI secrets: LUMI_KEYSTORE_BASE64 and LUMI_STOREPASS. server/ is an unused legacy reference.
