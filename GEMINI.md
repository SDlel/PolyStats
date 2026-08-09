# PolyStats Engineering Notes

PolyStats is a Kotlin/Compose Android app for following Polymarket markets with personalized filtering and Samsung Now Bar-oriented live notifications.

## Current Priorities

1. Stability
2. User experience
3. Battery efficiency
4. Visual polish
5. Maintainability

## Implementation Notes

- Do not reintroduce legacy network-speed logic.
- Keep Samsung Now Bar behavior aligned with the original NowbarMeter bypass: Samsung ongoing-activity metadata, special-use foreground service, promoted ongoing notification, short critical text, and the whitelisted `com.kakao.taxi` installed package id.
- Keep Home Feed and Now Bar filtering separate.
- Prefer extending `PreferenceProfile`, `MetricRange`, and `ScoringWeights` for personalization features.
- Keep refresh intervals adaptive: live events can refresh faster; inactive markets should refresh slowly.
