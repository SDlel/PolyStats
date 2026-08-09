# PolyStats

Real-Time Polymarket Intelligence for Android.

PolyStats turns the original prototype into a premium market-monitoring app focused on fast Polymarket discovery, personalized ranking, and Samsung Now Bar-ready live market updates.

## Product Surface

- Personalized market feed with search, category labels, probability, 24H movement, volume, liquidity, trader count, and charts.
- Preference profiles: Balanced Intelligence, Crypto Trader, Politics Enthusiast, Sports Fan, and AI Watcher.
- Advanced ranking model with category priority, volume, liquidity, trend, activity, favorites, pins, and discovery mode.
- Separate Home Feed and Samsung Now Bar filters, so the lock screen can show stricter high-signal markets than the full app.
- Bookmarking controls for favorites, pinned markets, muted markets, hidden markets, and locked Now Bar market.
- Live Event Mode when markets become highly active.

## Samsung Now Bar Strategy

Samsung does not currently expose a broad public Now Bar SDK for arbitrary apps. One UI restricts Now Bar access behind internal allowlists. PolyStats restores the proven NowbarMeter strategy:

- A foreground market-monitoring service.
- Samsung ongoing-activity manifest metadata.
- `FOREGROUND_SERVICE_SPECIAL_USE` plus a foreground-service subtype property.
- `NotificationCompat.setShortCriticalText(...)`.
- `NotificationCompat.setRequestPromotedOngoing(true)`.
- Installed package id spoofed as `com.kakao.taxi`, while source namespace remains `com.polystats.android`.

On an S24 FE running One UI 7 or newer, eligibility still depends on Samsung firmware policy, notification permission, battery policy, and whether the system honors ongoing activities for third-party apps.

## Architecture

- Kotlin
- Jetpack Compose + Material 3
- MVVM
- Repository pattern
- Hilt dependency injection
- Coroutines + Flow
- DataStore preferences
- Adaptive refresh scheduling inside the market monitor service
- Polymarket Gamma `/events` API as the primary source with bundled fallback market data

Key packages:

- `domain/`: market models, filters, scoring weights, profiles, ranking engine
- `data/network/`: Polymarket API data source
- `data/preferences/`: DataStore-backed user state
- `data/repository/`: feed and Now Bar state orchestration
- `services/`: foreground monitor, boot receiver, notification builder
- `ui/`: Compose app shell and market surfaces

## Releases
Find the latest app apk in the releases page!

## Notes

This rebuild intentionally removes the old network-speed meter behavior, overlay windows, and quick setting tiles. To match the working NowbarMeter bypass, the installed Android package id is intentionally `com.kakao.taxi`.
