# Assumptions — Kuro Step 1

1. **Mock clock**: `MockData.NOW` is fixed for deterministic previews; relative times in UI use `System.currentTimeMillis()` at runtime, so “x hr ago” will look live after creation.
2. **No persistence**: all repositories are in-memory. Restart resets to `MockData.initialClones`. Chosen to keep Step 1 stable without Room.
3. **No wrapper jar**: repo ships `gradle-wrapper.properties` only. CI installs Gradle 8.10.2 directly per task constraints (“do not download gradle locally”, build on GitHub Actions).
4. **Dynamic color**: enabled by default but fallback palette (`#0A0E14` etc.) is authoritative for brand. On API <31 dynamic is ignored.
5. **Reduce motion**: disables shimmer, press-scale, and nav transitions (durations → 0). Splash still waits ~900ms for readability.
6. **Icons**: Material Icons Extended only; launcher icon is a vector “K” mark, no binary assets.
7. **Strings**: all static UI chrome lives in `strings.xml`. Dynamic mock names (e.g. “Notes • Work”) are data, not chrome.
8. **Product copy**: avoids claims of real cloning/virtualization. Success copy says “prepared in this preview”.
9. **Branch/PR**: created `kuro/step-1-modern-ui` and PR “Step 1: Modern UI foundation for Kuro” as requested.
