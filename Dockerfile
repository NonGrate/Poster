# Used by hosts that build from the repository (Coolify "Dockerfile" build pack,
# Render, Railway...). It does not build anything: the CI workflow publishes a
# runtime image and this just pulls it, so the host never needs Gradle or the
# Android SDK. Point SOURCE_IMAGE at your registry path (docs/Deployment.md), or
# replace this file with Dockerfile.runtime's contents if you build on the host.
# The CI workflow names the image <owner>/<repository>-server.
ARG SOURCE_IMAGE=ghcr.io/OWNER/REPOSITORY-server:main
FROM ${SOURCE_IMAGE}
