# KupuProxy v1.3.3.9

## 🔧 Release fix: restore the app after the 1.3.3.8 refactor

This release cleans up the compile break introduced by the previous release prep and restores the app to a buildable state.

### What was fixed
- Restored the missing `MainActivity` app flow after the accidental refactor split
- Restored intent constants used by `CheckFileActivity`, `ProxyListActivity`, and `ProxyLoadingActivity`
- Fixed locale application on app start so the saved language is applied correctly
- Kept the update checker and background notification path intact
- Removed the destructive Room migration fallback so local data is preserved again

### Quality checks
- `testDebugUnitTest` now passes after the restore work
- Update notification permission guard remains in place for Android 13+
- Export-to-file helper is preserved for file sharing flows

### Notes
This build is a maintenance release. It is meant to get the app back to a stable, releasable state before the next functional changes.
