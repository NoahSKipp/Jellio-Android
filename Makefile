# Release mechanics, shared with Jellio-TV: bumps app/build.gradle.kts's
# versionName (versionCode follows from it) and releases on GitHub.

release:
	@# Resumable: run it again (or with VERSION=x.y.z) after a failure part
	@# way; DRY_RUN=1 prints what it would do.
	scripts/release.sh

.PHONY: release
