## 0.7.0 — 2026-07-21

* `Flinku.resetAll(context)` — testing-only full local wipe (match cache, user id, pending referral, tracked-once flags). Does not change `reset()` behaviour.
* Pending referral index entry is removed when a pending referral record expires by TTL.

## 0.6.0

* Pending referral record (`flinku_pending_referral_{projectId}`) survives `reset()`
* `setUserId` / `qualifyReferral` read pending record (not match cache); track once per `referral_tracked_{projectId}_{userId}`
* Retry track on `configure()` when userId already stored; 30-day pending TTL
* Injectable store/network for unit tests

## 0.4.0

* `Flinku.setUserId` — store app user id and auto-track referrals from cached match `referrerId`
* `Flinku.qualifyReferral` — mark a referred user as qualified for an optional event
* Persist `params` in match cache so referral tracking can read `referrerId`

## 0.3.2 - Support publishable API keys (flk_pk_). Debug-mode warning when a secret key is embedded. Surface Allowed Domains errors clearly.

## 0.3.1 - Add createLinkInstant for instant link creation without waiting for server

## 0.3.1

* Play Install Referrer support for deterministic deferred deep linking
* Clipboard URL check before fingerprint match (clears clipboard after read)
* Added `matchType` to `FlinkuLink`
* `matchWithBody()` helper for custom `/api/match` payloads

## 0.3.0

* Added `FlinkuLinkOptions`, `FlinkuCreatedLink`, and `FlinkuException`
* Added `createLink()` and `createLinks()` (Bearer auth; calls `apiBaseUrl` derived from project `baseUrl`)
* Optional `apiKey` on `Flinku.configure()` for link creation APIs
* `apiBaseUrl` strips the project subdomain (e.g. `https://myapp.flku.dev` → `https://flku.dev`)

## 0.2.0

* Project-based architecture — baseUrl is now your project subdomain URL
* Added params, title, clickedAt, subdomain, projectId to FlinkuLink
* Added timeout configuration (default 5000ms)
* Added retry logic — retries once on network failure
* Added double-match prevention using SharedPreferences
* Added Flinku.reset() for testing
* Subdomain auto-extracted from baseUrl
* match() is now a suspend function — runs on IO dispatcher

## 0.1.0

* Initial release
