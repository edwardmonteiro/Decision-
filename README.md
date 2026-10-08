# Decision-

**Novo: [Decision Flights · APK Android](flights/README.md)** — buscador de
passagens com navegador hospedado na OpenAI, Agents, Session, Environment,
Vault e Decisions. Interface minimalista; ajustes na mesma sessão; sem JEV.

# LUMI · Travessias

A minimalist Android drawing puzzle for children. Draw an idea to cross the river; watch Lumi use it. This v0.3.0 update replaces the previous space shooter while preserving the package, signing certificate and encrypted OpenAI key.

## Play on your phone

Install **LUMI-Travessias-v0.3.0.apk** over the previous LUMI app. Do not uninstall first if you want to preserve your key and local settings. Android 9+.

1. See the river: narrow/wide, calm/fast, sometimes with a backpack.
2. Draw a **bridge**, **boat**, or **jump arrow** with one finger. Undo and clear are available.
3. Tap **EXPERIMENTAR**. Decisions receives the drawing image and classifies the intended solution.
4. Lumi builds a bridge, sails, or jumps. A short explanation shows why the crossing worked or needs another idea.
5. Tap **PRÓXIMO DESAFIO**. Agents prepares the next river using the recent solutions and outcomes.

**Ver ideias** shows drawing examples. The speaker button reads the challenge using Android text-to-speech when a Portuguese voice is available. Sound and reduced movement are configurable.

## The AI has a visible job

| Component | Job |
|---|---|
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

Only the drawing image is sent for recognition, not a screenshot of the phone. Do not draw names or personal data. Images are not persisted by the app. Agents receives bounded history entries, not drawing images. Provider retention follows the account's settings. Keys never enter WebView JavaScript and remain encrypted with Android Keystore.

Local daily limits remain 24 Decisions calls and 12 sessions. The connection test consumes one sample-drawing call and can prepare one challenge. Counts are not currency budgets. See [operations](docs/operations.md).

## Build and verify

Requires Java 17+, Android platform API 35+, Android build tools and a private release keystore. No Gradle, game engine or npm dependencies are needed by the Android build.

```bash
node --check app/src/main/assets/game.js
JSON_JAR=/path/to/json-20250517.jar bash scripts/test-direct.sh
# Set ANDROID_JAR, BUILD_TOOLS, LUMI_KEYSTORE, LUMI_STOREPASS. Alias: lumi.
bash scripts/build.sh
```

Output: `build/LUMI-Travessias-v0.3.0.apk`. Never commit the signing material. CI can sign with the original release secrets. `server/` remains the unused v0.1 reference and is not contacted by the app.

## Verification and limits

- Native contract tests cover image request shape, no-ID responses, all classes, uncertainty, invalid schemas, history-conditioned planning, resource cleanup, quotas and data boundaries.
- Browser interaction tests cover drawing, undo, recognition UI, three animations, failed crossings, unclear drawings, manual fallback, next challenge, parent settings and lifecycle hooks.
- Layouts checked at 360×640, 390×844 and 1200×900 with Playwright Chromium. Browser plugin was unavailable for this local surface.
- APK compilation and signature are verified. Same certificate as v0.2.x.
- The new visual Decisions call and adaptive agent prompt were tested with fixtures, not a live key. No physical Android/emulator test was performed here. The user's earlier screenshot verified Sessions/Environment/Vault for the prior release; it does not validate this release's recognition accuracy.

## Source

`app/src/main/assets/`: code-native vector game and UI. `MainActivity.java`: Android transport, TTS and key storage. `DirectAi.java`: classification, planning and validation. `tests/java/`: fixture tests. `docs/design.md`: interaction and visual specification.

Official contracts checked 2026-10-08: [Decisions](https://developers.openai.com/api/reference/resources/decisions/methods/create), [Agents quickstart](https://developers.openai.com/api/docs/guides/agents-api/quickstart), [Hosted environments](https://developers.openai.com/api/docs/guides/agents-api/environments/openai-hosted), [Vaults](https://developers.openai.com/api/docs/guides/agents-api/tools/vaults).

MIT. Original procedural line art and synthesized sound.
