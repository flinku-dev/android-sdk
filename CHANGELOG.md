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
