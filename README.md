# KissKH WebView

A lightweight Android WebView wrapper for the KissKH website, designed to work on phones and Android TV.

## Features

- WebView-based KissKH site; no native recreation of the catalogue/player.
- Editable HTTPS base URL. Bookmarks/history store KissKH-relative paths, so changing the base domain changes resolved links automatically.
- Android TV detection with forced landscape and DPAD navigation/focus highlighting.
- Phone/tablet touch support.
- Double-back-to-exit when there is no WebView history.
- Native bookmarks and direct series/episode URL/path opening.
- ZIP backup/import for app-managed settings, bookmarks, history, filter-list cache and custom rules.
- Local filter-list blocker with EasyList, EasyPrivacy, AdGuard URL Tracking Protection, Peter Lowe, URLHaus and phishing lists.
- Filter lists cache locally and are refreshed automatically about every 24 hours when a network connection is available; manual update is available from Settings.
- HTTPS-only app navigation and reduced WebView file/content access.
- Third-party cookies disabled by default.
- WebView debugging disabled in release/debug builds shipped by this project.

## Build

The repository intentionally does not require a machine-wide Android/Gradle installation for GitHub builds. The included GitHub Actions workflow installs JDK 17, Android SDK 35 and Gradle 8.10.2, then builds `assembleDebug`.

Push a tag such as `v1.0.0` to build the debug APK and attach it directly to the GitHub Release. A manual workflow run also creates the normal Actions artifact.

## Notes

The filter engine intentionally implements a conservative subset of common network-filter syntax. Cosmetic CSS rules are ignored because WebView request interception can block network resources but cannot safely implement every browser-extension cosmetic rule. A custom allow/block rule can be supplied from Settings.

KissKH can change its domains and site internals. The wrapper therefore keeps the base origin configurable and does not embed the site's API or media implementation.
