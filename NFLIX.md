# Nflix Android

Fork of [Jellyfin for Android](https://github.com/jellyfin/jellyfin-android) bound to the Nflix server
(`https://nflix.miracloud.fr`) and to the Mira Connect (Authentik) account system.

## What changes compared to upstream

| Area | Nflix |
| --- | --- |
| Server | Always `https://nflix.miracloud.fr`. Server selection, discovery and the "multiserver" web feature are removed. |
| Sign-in | Native animated login screen: **Mira Connect** (Authentik OIDC, PKCE through AppAuth) or **username/password** (Jellyfin LDAP plugin backed by the same Authentik directory). |
| Session | The native session is injected into jellyfin-web (`jellyfin_credentials`) before it boots. When the web client falls back to its login/server pages (sign out, revoked token), the native login takes over. |
| Branding | App name, every "Jellyfin" string in every locale, launcher/adaptive/monochrome icons, notification icon, splash and logo are generated at build time from `branding/nflix_logo.b64` (`buildSrc/src/main/kotlin/NflixBranding.kt`). Upstream resources are left untouched and overridden by a flavor resource overlay. |
| Identity | Application id `fr.miracloud.nflix`, client name `Nflix Android`, archives `nflix-android-v<version>`. |
| Mira Connect | Opening Nflix from Mira Connect with the boolean extra `fr.miracloud.nflix.extra.MIRA_LOGIN` starts the Mira sign-in immediately. Nflix detects Mira Connect (`fr.miracloud.connect`). |
| Updates | On start Nflix checks the store channel. A newer version shows an animated prompt: **Mettre à jour avec Mira Connect** (opens Mira Connect with `fr.miracloud.connect.extra.UPDATE_PACKAGE`, which updates silently on Android 12+) or, without Mira Connect, a verified (SHA-256) direct download and install. "Plus tard" reminds again after a day. |
| Motion | Animated backdrop, spring logo entrance, staggered reveals, mode transitions, error shake, success animation, cross-fade/scale fragment transitions. |

Playback (ExoPlayer, Chromecast, downloads, media session, Android Auto) is unchanged upstream code.

## Server side

`nflix/server-plugin` contains the **Nflix Auth** Jellyfin plugin (Jellyfin 12, `net10.0`):

- `GET  /NflixAuth/Config` – public OIDC issuer and client id.
- `POST /NflixAuth/Mira` – exchanges an Authentik ID token (RS256, issuer, audience, expiry, single use, group `plan_standard`) for a Jellyfin session of the matching LDAP user.
- `GET  /NflixAuth/Store/<app>/manifest.json`, `/app.apk`, `/icon.png` – Mira store release channels (`nflix`, `mira-connect`), files in `<jellyfin data>/mira-store/<app>/`. Legacy `/NflixAuth/Android/*` maps to `nflix`.
- `PUT  /NflixAuth/Store/<app>/app.apk|icon.png|manifest.json` – publishing, protected by the `X-Mira-Publish-Token` header (token in the plugin configuration). The manifest is only accepted when its SHA-256 matches the uploaded APK, then swapped in atomically.

Authentik: OAuth2 provider/application `Nflix Android` (`nflix-android`), public client, redirect URI
`fr.miracloud.nflix:/oauth2redirect`, scopes `openid profile email`, bound to the group `plan_standard`.

## Build

JDK 21, Android SDK 37:

```sh
./gradlew assembleProprietaryRelease
```

Release signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`
(environment or Gradle properties). The Nflix release key is **private and never committed**; Mira Connect pins its
certificate (`SHA-256 E4C459E9…49866F`). Every update must be signed with the same key.

Releasing is one command (needs the private keys and publish token in `~/nflix/keys`):

```sh
nflix/scripts/release-nflix.sh 1.2.0 "Nouveautés…"        # Nflix
nflix/scripts/release-mira.sh 0.6.0 6 "Nouveautés…"       # Mira Connect (repo frenchyx24/mira-connect-android)
```

They set the version, build, check the signing certificate and publish through `store-publish.sh`. Installed apps
pick the release up by themselves.

## License

GPL-2.0, like upstream Jellyfin for Android (see `LICENSE.md`).
