# Nflix refactor audit — 2026-11-03

## Scope and provenance

This checkout is the `frenchyx24/jellyfin-android` fork, on `master`. Repository HEAD is `043f389869b4a5a432d4fde7f6fc2f2fa7da493a` (Nflix 2.0). Previously unpushed Android 3.0 sources were recovered from the stopped `nflix-build` container and restored as local changes. Recovery containers must not be removed until all source and signing recovery is complete.

The native application retains Compose, upstream playback, Chromecast, downloads, media sessions, Jellyfin authentication and optional web access. No new production signing key was generated. No release was published during this recovery.

## Verified work

- Android debug assembly and unit tests passed after the recovered 3.0 sources and book fixes were applied. Round 3 reports record 26 tests, zero failures, errors or skipped tests.
- A real debug build after browser-login profile parsing and cancellation fixes passed: 31 tests, zero failures, errors or skipped tests. The build wrapper exports test results when available.
- Book fixes cover EPUB namespaces, percent-encoded chapter paths, archive traversal confinement, extraction replacement, entry/expanded-size limits, numeric CBZ ordering, rejected-cache removal and forced redownload.
- Downloads check content length, have a total timeout, and connect coroutine cancellation to the OkHttp call.
- PDF opening and page rendering errors have visible error states and redownload actions.
- Mira bridge metadata and accepted browser ID-token display claims feed the same session fields. HTTPS avatar validation is shared between Home and Profile. Display claims are not permission grants; ID tokens are not persisted by the profile parser.
- Session clearing removes name, login method and Mira display metadata. Password login resets any prior Mira display profile.
- Notice seen IDs persist locally and no longer count as unread after opening the announcement list.
- Horus confirmations no longer promise unimplemented notifications. Empty season selection offers Close.
- Horus request creation now invalidates its actual request cache. Missing Horus identity fails visibly instead of fetching the global requests endpoint. Request screen has manual refresh.
- Release scripts use unique containers, require the existing production signing material, pin package/certificate and check increasing versionCode. Release defaults to beta. Promotion remains separate.
- The build wrapper ran successfully against the real Docker Android builder for a debug build. Release signing remains untested without the original private key.

## Open defects and limits

### P0 — signing, publication and recovery

- Original production keystore, alias/password configuration and publisher credentials are absent from the recovered workspace. Recover the existing files from an authorized secure backup; never replace the key.
- Before distribution, compare the original keystore certificate, distributed APK certificate, package and both channel versionCodes. Refuse any mismatch.
- Store backend still trusts supplied metadata and uses multiple publication operations. Client-script checks do not establish server-side APK verification or atomic publication.
- Stop claiming release script validation from syntax checks alone. Isolated mock execution tests are being added; signed release verification still needs the original key.
- Previous unpushed authentication backend source/tests need recovery from the old verification container workspaces. Repository backend is not evidence that historical backend changes were deployed.
- Real test fixtures recovered with the Android sources contain private server metadata. Do not commit them without sanitization and review.

### P1 — books and identity runtime validation

- No available Android device/emulator has verified EPUB XML parser behavior, PDF disposal, WebView navigation/errors, image loading, or Mira inter-app bridge behavior.
- XML feature flags differ between JVM and Android. JVM security tests alone do not establish safe behavior on Android; instrumented adversarial XML tests remain required.
- Browser avatars appear only when the accepted ID token supplies a valid HTTPS picture claim. Private avatars or missing claims require a verified server/profile endpoint; do not send Jellyfin credentials to arbitrary avatar domains.
- Login cancellation and completion failures need final UI/device validation; cancellation must not be shown as a credential error or clear an existing session.
- Announcement read state is local, not backend-synchronized or account-scoped. Opening the list marks its loaded notices seen; this is not a multi-device inbox.

### P1 — Horus

- Requests still load only the first 40 entries; implement real pagination from backend response metadata with visible next-page errors.
- Request detail needs requester, dates, seasons/episodes, services and permission-gated actions.
- Download speed, size, ETA and import progress must come from actual service endpoints. Never infer percentages from status labels.
- Error state and refresh are not a background notification mechanism.

### P1 — notifications and achievements

- Remote push is not implemented. Need durable backend events, account-bound multi-device registration, token renewal/removal, user/admin permissions and end-to-end delivery while the app is closed.
- Native admin inbox, targeting, history, preferences and deletion are not implemented.
- Achievements UI reads real plugin data, but event-driven unlock toasts and deduplication remain to implement and test.
- Recovered badges and Notice source outside this repository are not proof of a live deployment.

### P2 — home and interface

- Reaudit native/web home parity using actual enabled plugin configuration and API responses.
- Complete recent episodes/books, activity-derived suggestions, history, collections and list behavior without synthetic data.
- Verify touch targets, contrast, French labels, motion reduction, caching, image fallbacks, network cancellation and battery behavior on devices.

## Execution order and acceptance gates

1. Preserve recovered sources and sanitize fixtures before committing any recovered work.
2. Add isolated release-script tests; restore the original signing files securely; harden backend store verification and publication.
3. Finish books and identity runtime tests on Android, including offline, malformed archive, corrupt cache, private-avatar and account-switch cases.
4. Implement Horus pagination and real detail/progress endpoints with parser, permission, pagination and error tests.
5. Implement durable notifications/push and admin tooling; test two devices, app closed, token rotation, logout and unauthorized targeting.
6. Wire real achievement events and deduplicated accessible toasts; test reconnect and repeated events.
7. Complete home parity and interface/performance checks.
8. Build a versionCode strictly above both distributed channels, verify package and production signature, test upgrade without data loss, publish beta and verify remote hash. Promote only after separate approval/testing.

## Distributed state last checked

- Stable: `2.0.0`, versionCode `2000099`.
- Beta: `3.0.0`, versionCode `3000099`.
- Package: `fr.miracloud.nflix`.
- Production certificate SHA-256: `3C7E2866A82ADEA668857EE07B46995975C0096EA1642D590689A9EC1B320A64`.

The full refactor is not complete. Local build success does not establish live deployment or device-level correctness.
