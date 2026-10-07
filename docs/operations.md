# Operations and activation

## Actual delivery state

No OpenAI project key was present in the execution environment. A trusted Codex API-key local-write setup skill was not installed, so no credentials were created, copied, embedded, or requested in chat. No billable service or managed environment was provisioned. The app is fully playable offline and labels disconnected AI honestly.

The remaining activation requires secure account setup, a public HTTPS deployment, Vault provisioning, and live end-to-end acceptance. These are not represented as completed.

## Backend configuration

Run Python 3.10+ using `python server/app.py`, or use `server/Dockerfile`. Put an HTTPS reverse proxy in front. Python's built-in HTTP server is intentionally a **single-instance family pilot**, not a public scalable production endpoint. Enforce upstream connection limits and request-rate limits before public exposure. Persistent storage is required for budgets to survive restarts.

Use the deployment's secret manager, not source code or the APK:

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | Application key for Decisions, Agents and Vault setup; backend only |
| `LUMI_DEVICE_TOKEN` | Random token of at least 24 characters; paired with the parent-owned APK |
| `LUMI_CATALOG_TOKEN` | Different random token of at least 24 characters; read-only catalog credential |
| `LUMI_PUBLIC_URL` | HTTPS backend base URL, standard port 443 or 8443 |
| `LUMI_VAULT_ID` | ID produced by the provisioning script |
| `LUMI_AGENT_MODEL` | Managed-agent model; default `gpt-6-astra`, subject to account availability |
| `LUMI_DB` | Persistent SQLite path; default `server/runtime/lumi.sqlite` |
| `LUMI_MAX_DAILY_RUNS` | Global session request count cap; default 12 per UTC day |
| `LUMI_MAX_DAILY_DECISIONS` | Global Decisions request count cap; default 24 per UTC day |

After secure credentials and an HTTPS endpoint exist, run `python server/provision.py` once. Store its returned vault ID in deployment configuration. The script binds the catalog token to the exact backend hostname using `auth.networking.allowed_hosts`. Session creation additionally restricts sandbox networking with `allowed_domains`.

Open the APK → settings → Área dos responsáveis → complete the gate → enter the backend URL and **device token**. The app rejects a token beginning with `sk-`. `GET /health` confirms that required configuration exists, not that account entitlements or live inference have succeeded.

Do not paste secrets into a chat, issue, or commit. The parent gate prevents casual child access; it is not an authentication boundary. Server bearer authentication is the actual boundary.

## API surface

| Method | Route | Token | Result |
|---|---|---|---|
| GET | `/health` | Device | Configuration status, no secrets |
| POST | `/v1/runs` | Device | Local run ID; asynchronous agent planning |
| GET | `/v1/runs/{id}` | Device | Mission/status for the owner |
| POST | `/v1/runs/{id}/decision` | Device | One validated, cached answer per wave |
| DELETE | `/v1/runs/{id}` | Device | Close run and request sandbox cleanup |
| GET | `/catalog` | Catalog | Approved mission catalog only |

No arbitrary proxies, free-text prompts, administrative routes, browsing tools, purchases or outbound communications are exposed to the APK.

## Boundaries and costs

- Two simultaneous runs maximum; one application token represents one family deployment.
- Two Decisions requests per game, issued near seconds 14 and 34. Changes apply between waves.
- Decisions answers cannot set physics arbitrarily. Pace multipliers are 0.76, 1.0, or 1.15.
- `bright` is capped to `steady` unless the child has at least four stars and zero collisions.
- Request body limit: 8 KiB. Typed numeric-only game metrics; reject extra fields.
- Sessions have a 120-second application lifetime. Planner deadline is 85 seconds plus an in-flight provider request timeout.
- Delete managed sessions after planning succeeds/fails, on client close, and on expiration. Janitor retries cleanup every 30 seconds.
- A provider timeout during session creation can leave an unconfirmed remote session. There is no blind POST retry; reconcile the Platform session list. A restart during an external request has the same limitation.
- Count quotas are not dollar-denominated spend guarantees. Set provider project spend limits before live use.
- Logs contain event codes and run IDs, never credentials or child names. No photos, voice, location or personal identifiers are collected.
- Local game score remains on the phone. Backend run/decision records expire after 24 hours; sanitized audit after seven days.
- OpenAI retention is provider-controlled. Verify the account's data controls and applicable children's-product requirements before public distribution.
- Aggregate telemetry is enabled only after the parent enters the backend configuration. No connected mode is shipped by default.

## Live acceptance checklist (pending)

1. Confirm actual project access to Decisions and Agents APIs.
2. Provision the Vault and verify that catalog credentials are never returned by read APIs.
3. Start a game; verify an actual managed session/environment in the Platform console.
4. Verify authenticated catalog GET and completed root turn, then an allowlisted mission appears for the next round.
5. Verify a real Decisions `choice` response and its request ID; ensure the bounded change occurs at a wave boundary.
6. Disable network while playing; movement, sound and scoring must continue immediately.
7. Verify rate limits, cleanup and provider billing in the real deployment.
8. Install on the target Samsung and verify system bars, touch, background/resume, audio and frame pacing.

## Signing

The private release keystore and its password are not in this repository. Retain both to sign updates with the same certificate. CI secret names are `LUMI_KEYSTORE_BASE64` and `LUMI_STOREPASS`. Do not publish signing material in GitHub artifacts or releases.
