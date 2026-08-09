# PolyStats Architecture

PolyStats is organized around one source of truth: `MarketRepository`.

## Data Flow

1. `PolymarketRemoteDataSource` fetches active Polymarket Gamma `/events` pages and flattens active order-book markets.
2. The repository combines remote markets, search query, active preference profile, and user market state.
3. `MarketRanker` applies filters and scoring separately for Home Feed and Samsung Now Bar.
4. Compose observes `MarketUiState` and renders the feed, Now Bar preview, live event card, profile chips, and action controls.
5. `MarketMonitorService` observes the same state and publishes the highest-scoring Now Bar market through an ongoing notification.

## Personalization

`PreferenceProfile` controls:

- Category priorities
- Home Feed filters
- Samsung Now Bar filters
- Scoring weights
- Discovery mode

`UserMarketState` controls:

- Favorites
- Pinned markets
- Muted markets
- Hidden markets
- Locked Now Bar market
- Rotation enabled state

Pinned markets are sorted first. Muted and hidden markets are excluded. Now Bar content uses its own stricter filter profile so it can stay high signal on the lock screen.

## Battery Strategy

The service uses adaptive refresh:

- Live or highly active markets: faster refresh.
- Quiet markets: slower refresh.

Future production work should move long-period background refresh into WorkManager and keep the foreground service active only when Now Bar monitoring is enabled.

## Samsung Now Bar Compatibility

The implementation uses:

- `com.samsung.android.support.ongoing_activity` metadata.
- A foreground service notification.
- Public lock-screen visibility.
- `FOREGROUND_SERVICE_SPECIAL_USE` and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
- `NotificationCompat.setShortCriticalText(...)`.
- `NotificationCompat.setRequestPromotedOngoing(true)`.
- Installed package id spoofing as `com.kakao.taxi`.

Samsung firmware ultimately decides whether a notification is elevated into the Now Bar on an S24 FE / One UI 7+ device.
