# LUMI Orbit

A fast, friendly Android vector arcade game for children. Portrait, one-finger controls, short expeditions, original synthesized sound and procedural line art.

**v0.2.0 runs on the phone and connects directly to OpenAI. No separately deployed backend is required.** The signed APK plays offline immediately. To enable cloud features, a parent enters their own OpenAI API key in the native Android connection dialog. The app then makes real API calls and reports each service's result and resource IDs.

No key was available during development, so live account access and inference remain unverified. Compile, signature, gameplay and fixture tests do not establish live activation.

## Install and connect

1. Install `LUMI-Orbit-v0.2.0.apk` on Android 9 or newer. It uses the same signing certificate as v0.1.0 and can update it.
2. Open **Configurações → Área dos responsáveis**, answer the parent gate, then choose **CONECTAR OPENAI**.
3. Enter your API key in the protected native Android dialog and select **Conectar e testar**. API access, internet and account billing are required.
4. The app tests Decisions, creates or reuses a Vault, and prepares a mission with a managed Session and hosted Environment. The panel shows errors if the account lacks access or quota.
5. Play while the mission prepares. Validated mission data appears in the next available expedition.

The API key is encrypted using Android Keystore, excluded from backups, and never passed to game JavaScript. This is a personal-device, bring-your-own-key app; no shared developer secret is embedded in the APK. A mobile device cannot guarantee protection of an API key against a compromised operating system. For public distribution with centrally funded access, use a server-mediated architecture.

## Play

- Drag to move. Shooting and aim assistance are automatic.
- Approach golden stars to rescue them. Collect eight to complete the expedition.
- The shield protects for three seconds and recharges in ten.
- Rounds last up to 60 seconds or three collisions. Every ending encourages another try.
- Three bounded patterns: star garden, comet stream and moon rings.
- No ads, trackers, microphone, camera, location or free-text interaction with children.

## OpenAI integration

| Component | Actual job |
|---|---|
| Decisions | `POST /v1/decisions`, model `gpt-6-luna`: select gentle, steady or bright pace from numeric game metrics, at most twice per round |
| Session | `POST /v1/agents/sessions`, model `gpt-6-astra`: prepare an eight-star flight pattern, accepted only from a completed root turn |
| Environment | Small `openai_hosted` sandbox with networking disabled; the agent is instructed to generate and validate its numeric pattern using Python |
| Vault | A real reusable Vault is created and attached through `vault_ids`. It is empty because this game requires no external-service credentials |
| Android | Local rendering, physics, sound, progress, API orchestration, encrypted key storage and strict validation |

The OpenAI Vault does **not** hold the main API key. Android Keystore protects that key locally. Sessions and environments run in OpenAI's cloud; gameplay runs on the phone. The app verifies environment connectivity and validated final output, but does not claim to audit every tool execution.

Only an approved mission ID, eight bounded lane positions and a bounded speed multiplier can enter gameplay. No generated code or unrestricted text runs or displays in the game. Offline rules cover network failures. Daily local caps are 24 Decisions calls and 12 sessions; they are request limits, not dollar budgets.

## Source map

- `app/src/main/assets/`: shipped Canvas game and interface.
- `app/src/main/java/com/edward/lumi/MainActivity.java`: Android shell, native key dialog, Keystore and fixed-host HTTPS transport.
- `app/src/main/java/com/edward/lumi/DirectAi.java`: direct Decisions/Agents/Vault orchestration, quotas, validation and session cleanup.
- `tests/java/com/edward/lumi/DirectAiTest.java`: native orchestration contract tests using an in-memory transport.
- `docs/operations.md`: setup, resource lifecycle, limits and remaining device/live verification.
- `server/`: legacy v0.1 optional backend reference. **The v0.2 APK does not call it.**
- `scripts/build.sh`: Android compilation and release signing.

## Build and test

Requires JDK 17+, Android platform API 35+, Android build tools 35+, Python 3.10+ and a private signing keystore. No Gradle, game engine or npm packages are needed by the APK build. After the tools are installed, compilation is offline.

```bash
node --check app/src/main/assets/game.js
# Standalone JVM tests require org.json:json:20250517 (Android provides org.json at runtime).
JSON_JAR=/path/to/json-20250517.jar bash scripts/test-direct.sh
python3 -m unittest discover -s tests -v  # legacy backend regression tests
# Set ANDROID_JAR, BUILD_TOOLS, LUMI_KEYSTORE and LUMI_STOREPASS.
# Keystore alias: lumi. Never commit signing material.
bash scripts/build.sh
```

Output: `build/LUMI-Orbit-v0.2.0.apk`. CI requires the original signing secrets to produce compatible updates. A browser preview can serve `app/src/main/assets/`; the secure native connection dialog exists only in the APK.

## Verification boundary

- Compiled against API 36; minimum API 28, target API 35. Release signature verified.
- Native orchestration tested with fixtures: provision, completed-turn handling, mission bounds, quota, errors, cleanup and Decisions caching.
- Browser gameplay checked for touch movement, shield, pause, complete rounds, replay, saved progress and responsive layouts.
- No physical Android device/emulator test or live OpenAI request was performed during this delivery. The parent panel performs real account checks after connection.

## Official contracts

Checked 2026-10-07: [Decisions](https://developers.openai.com/api/docs/guides/decisions), [Agents quickstart](https://developers.openai.com/api/docs/guides/agents-api/quickstart), [Sessions](https://developers.openai.com/api/docs/guides/agents-api/sessions), [Events](https://developers.openai.com/api/docs/guides/agents-api/sessions/events), [Hosted environments](https://developers.openai.com/api/docs/guides/agents-api/environments/openai-hosted), [Vaults](https://developers.openai.com/api/docs/guides/agents-api/tools/vaults).

MIT. Original vector art and synthesized audio.
