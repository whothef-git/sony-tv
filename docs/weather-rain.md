# Weather-card rain

The Weather card's rainy atmosphere uses near-vertical tapered streaks at several depths, speeds and opacities. Slowly drifting, layered noise clouds and a restrained day/night rain tint sit behind the existing glass forecast panels. The original wallpaper and forecast layout remain in place. Static ring-shaped droplets have been removed. Falling drops now trigger brief bottom-edge splashes: four small ballistic droplets and a flattening ripple fade after each impact, with density following rain intensity. These reuse existing particles and paints and appear only during rain on Weather.

Only the current local weather condition code activates rain; hourly/daily rain probabilities do not. Density varies from drizzle/light rain through moderate and heavy rain. Missing/stale current weather and clear conditions do not invent rainfall. Existing non-rain effects and the weather provider are retained.

Rain redraws follow display vsync, and positions use elapsed time with double-precision motion calculations rather than absolute float uptime. Drops wrap outside the viewport. A small gradient streak bitmap is reused, and the seamless 256 × 128 cloud texture is generated once on a short background thread. Draw-time paints, particle seeds and rectangles are reused. No new framework, network endpoint or bundled video asset is needed.

The static sky and forecast views use temporary hardware layers while Weather is visible. Those caches are released when leaving Weather or pausing the activity. Rain frame scheduling also stops when inactive; the cloud worker is interrupted when the view is detached. Other tabs do not display this overlay.

Validation on Sony Android TV, 31 October 2026:

- Debug and optimized normal APKs built successfully.
- All 37 `WeatherRainChecks` passed, covering condition classification, intensity, missing data, actual timed rain motion, bottom splash placement/movement, clear conditions and hidden-state behavior. Conditions in these checks are fixtures, not claimed live observations.
- Drizzle and heavy rain were shown with the existing labelled debug preview and recorded on the actual Weather card. Forecast readings were not replaced with simulated readings.
- Sampled unrecorded frame timing was around 17–18ms on this TV. Heavy-rain UI draw work had a median of about 0.84ms and render-command work about 2.25ms after caching. Screen recording adds substantial overhead; this is not a guarantee of locked 60fps or an exhaustive performance benchmark.
- Preview overrides automatically expire and are disabled in normal builds. The final installed normal APK follows live weather conditions.

Run the targeted checks with `:app:assembleDebugAndroidTest -PtestRunner=tv.widgetv.home.WeatherRainChecks`, then `am instrument -w tv.widgetv.home.test/tv.widgetv.home.WeatherRainChecks`.
