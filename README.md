# Decision-

**Novo: [Decision Flights · APK Android](flights/README.md)** — buscador de
passagens com navegador hospedado na OpenAI, Agents, Session, Environment,
Vault e Decisions. Interface minimalista; ajustes na mesma sessão; sem JEV.

# LUMI · Travessias

A minimalist Android drawing puzzle for children. Draw an idea to cross the river; watch Lumi use it. Version 0.4.0 adds live spoken interaction to the drawing puzzle, preserving the package, signing certificate and encrypted OpenAI key.

## Play on your phone

Install **LUMI-Travessias-v0.4.0.apk** over the previous LUMI app. Do not uninstall first if you want to preserve your key and local settings. Android 9+.

1. See the river: narrow/wide, calm/fast, sometimes with a backpack.
2. Draw a **bridge**, **boat**, or **jump arrow** with one finger. Undo and clear are available.
3. Tap **EXPERIMENTAR**. Decisions receives the drawing image and classifies the intended solution.
4. Lumi builds a bridge, sails, or jumps. A short explanation shows why the crossing worked or needs another idea.
5. Tap **PRÓXIMO DESAFIO**. Agents prepares the next river using the recent solutions and outcomes.

**Ver ideias** shows drawing examples. The speaker button reads the challenge using Android text-to-speech when a Portuguese voice is available. Sound and reduced movement are configurable.

## Talk to Lumi

Tap **Falar**, read the audio disclosure, then **LIGAR MICROFONE** and allow Android microphone access. Uses the API key already connected on your phone. Speak naturally in Brazilian Portuguese; tap **Desligar** to stop.

- “Fica de noite” / “quero um pôr do sol”: changes the sky.
- “Água mais calma” / “rio mais largo”: changes the river and crossing conditions.
- “Deixa mais fácil” / “quero um desafio difícil”: adjusts width, current and backpack together.
- “Tira a mochila” / “desfaz essa mudança”: changes cargo or undoes the last voice change.
- “Me dá uma dica”: reads the actual current river before offering a short hint.
- “Desliga a voz”: ends the conversation.

The existing drawing stays intact. Changes only apply while drawing, not during recognition, an animation, menus or background operation. The resulting river remains solvable. The next completed attempt includes these conditions in Agents' history. Voice does not replace Decisions' image classification or draw the solution for the player.

**GPT-Live-1** carries full-duplex voice over WebRTC, using **Bossa** (Brazilian Portuguese). Its managed **GPT-6 Luna** Responses backend interprets requests and calls three bounded game tools. Android performs the authenticated SDP exchange; no shared key, browser API key, separate server, Android speech recognizer or STT→LLM→TTS chain is used for this conversation.

Limits: six voice starts per UTC day, up to two minutes each. Voice is billed at $0.05/minute plus backend usage; WebRTC initialization can incur 15 seconds of voice charges. The device stops capture/playback immediately on Stop, backgrounding, API failure or time limit. The app does not save recordings or captions; Live `store` is false. Provider retention still follows the account settings. Updates preserve your key; access to GPT-Live-1 must be available on that key.

## The AI has a visible job

| Component | Job |
|---|---|
| GPT-Live-1 + Luna | Real spoken conversation, validated local game changes and contextual hints. Voice changes are labelled in the scene. |
| Decisions (`gpt-6-luna`) | Classify a real 512×320 PNG drawing as bridge, boat, jump or unclear. No speed adjustment or placeholder classification. |
| Agents / Session (`gpt-6-astra`) | Generate the next bounded river configuration using up to eight previous attempts and outcomes. |
| Environment | Hosted small sandbox, network disabled. The agent is instructed to use Python to validate its configuration. The app also validates every field and solution feasibility locally. |
| Vault | Reuses the existing real empty Vault and attaches it to sessions. No external credentials are required; it does not store player history or the API key. |
| Android | Captures strokes, encrypts the API key, sends HTTPS requests, validates outputs, animates the crossing, applies game rules and saves local progress. |

The scene labels whether its challenge came from **Agents** or local rules. The result labels whether **Decisions** recognized the drawing or the player chose a solution manually. The parent panel shows real service statuses, request/session IDs and recent attempts.

Decisions' response has `answers`, `model` and `usage`; it does not require a resource `id`. This retains the v0.2.1 fix. HTTP `x-request-id`, when present, is captured for diagnostics.

## Game rules

| Idea | Crossing rule |
|---|---|
| Bridge | Connects both banks for every supported river. |
| Boat | Works in calm water; fast current brings Lumi safely back. |
| Jump | Works when width ≤ 0.32 and there is no backpack. |
| Unclear | Keeps the drawing available and asks for more detail. |

These are explicit puzzle rules, not a real-world physics simulator. The model recognizes the drawing; deterministic local rules determine the result. Raw model confidence below 0.55 becomes unclear. This is a conservative application threshold, not calibrated accuracy. Recognition errors can be corrected manually and are labelled accordingly.

The game does not execute model-generated code on the phone. Agents may vary width, current, backpack, theme and a proposed viable focus. Its final answer must pass the local six-field validator.

## Connect

**Configurações → Área dos responsáveis → Conectar OpenAI**. Enter your own API key in the protected native dialog. Existing v0.2.x keys remain usable. No private backend or hosting is needed. The game can run manually without internet, but cloud recognition/planning require API access and billing.

Only the drawing image is sent for recognition, not a screenshot of the phone. While voice is explicitly enabled, microphone audio and river conditions also go to OpenAI. Do not draw names or personal data. Images are not persisted by the app. Agents receives bounded history entries, not drawing images. Provider retention follows the account's settings. Keys never enter WebView JavaScript and remain encrypted with Android Keystore.

Local daily limits: 24 Decisions calls, 12 Agents sessions and 6 voice starts. The connection test consumes one sample-drawing call and can prepare one challenge. Counts are not currency budgets. See [operations](docs/operations.md).

## Build and verify

Requires Java 17+, Android platform API 35+, Android build tools and a private release keystore. No Gradle, game engine or npm dependencies are needed by the Android build.

```bash
node --check app/src/main/assets/game.js
node --check app/src/main/assets/live-voice.js
JSON_JAR=/path/to/json-20250517.jar bash scripts/test-direct.sh
# Set ANDROID_JAR, BUILD_TOOLS, LUMI_KEYSTORE, LUMI_STOREPASS. Alias: lumi.
bash scripts/build.sh
```

Output: `build/LUMI-Travessias-v0.4.0.apk`. Never commit the signing material. CI can sign with the original release secrets. `server/` remains the unused v0.1 reference and is not contacted by the app.

## Verification and limits

- 54 drawing/planner and 40 voice native fixture assertions pass. Voice tests cover fixed config, bounded payloads, quota, duplicate starts, failed cleanup and cancellation during session creation.
- Native contract tests cover image request shape, no-ID responses, all classes, uncertainty, invalid schemas, history-conditioned planning, resource cleanup, quotas and data boundaries.
- Voice browser fixtures cover WebRTC setup, completed tool calls, all 12 commands, undo, hints, drawing preservation, duplicate/partial/invalid events, busy guards, captions, permission/API errors, background stop and spoken stop. These simulate transport events; they do not measure live speech quality or latency.
- Browser interaction tests cover drawing, undo, recognition UI, three animations, failed crossings, unclear drawings, manual fallback, next challenge, parent settings and lifecycle hooks.
- Layouts checked at 360×640, 390×844 and 1200×900 with Playwright Chromium. Browser plugin was unavailable for this local surface.
- APK compilation and signature are verified. Same certificate as v0.2.x.
- GPT-Live audio/command behavior, visual Decisions and the adaptive agent prompt were tested with fixtures, not a live key. No physical Android/emulator test was performed here. The user's earlier screenshot verified Sessions/Environment/Vault for the prior release; it does not validate this release's recognition accuracy.

## Source

`app/src/main/assets/`: code-native vector game and UI. `MainActivity.java`: Android transport, TTS and key storage. `DirectAi.java`: classification, planning and validation. `LiveVoice.java` + `live-session.json`: fixed voice session config, quota, native lifecycle. `live-voice.js`: WebRTC, captions and bounded tool execution. `tests/java/`: fixture tests. `docs/design.md`: interaction and visual specification.

Official contracts checked 2026-10-08: [Decisions](https://developers.openai.com/api/reference/resources/decisions/methods/create), [Agents quickstart](https://developers.openai.com/api/docs/guides/agents-api/quickstart), [Hosted environments](https://developers.openai.com/api/docs/guides/agents-api/environments/openai-hosted), [Vaults](https://developers.openai.com/api/docs/guides/agents-api/tools/vaults).

Voice contracts checked 2026-10-08: [GPT-Live WebRTC](https://developers.openai.com/api/docs/guides/voice-webrtc?api=live), [delegation and tools](https://developers.openai.com/api/docs/guides/live-delegation), [session lifecycle and Bossa voice](https://developers.openai.com/api/docs/guides/live-conversations). Official changelog dates GPT-Live-1 availability to September 10, 2026.

MIT. Original procedural line art and synthesized sound.
