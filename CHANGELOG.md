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
