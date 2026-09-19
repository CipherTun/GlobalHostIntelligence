# Contributing to GlobalHostIntelligence

Thank you for contributing to GHI.

## Before you start

- Check existing issues and pull requests before opening a new one.
- Keep changes focused.
- Do not commit secrets, keystores, API keys or private credentials.
- Do not remove existing functionality unless the change explicitly requires it.
- Preserve the Android/Go build architecture unless the change is specifically about the build system.

## Pull requests

A useful pull request should include:

- What changed.
- Why it changed.
- Affected Android/Go components.
- Testing performed.
- Relevant build/run information.
- Screenshots for meaningful UI changes.

## Android changes

For Android UI changes:

- Keep Compose code readable and modular.
- Preserve accessibility and usable touch targets.
- Avoid unnecessary blocking operations on the main thread.
- Test navigation and configuration changes.
- Include screenshots when the visual result materially changes.

## Network tooling

Network features should be:

- Bounded.
- Explicit about their scope.
- Resilient to unavailable third-party sources.
- Careful with timeouts and concurrency.
- Designed for systems the user is authorized to investigate.

## Build verification

Before submitting a build-related change, run the most relevant available checks.

For release changes, verify:

- The Android project compiles.
- The matching Go/AAR component is used.
- Release signing is handled through GitHub Actions secrets.
- No signing material is committed.
- No unexpected R8/minification output is introduced when the release workflow requires it to remain disabled.

## Commit messages

Prefer short, descriptive commit messages, for example:

- `feat: add DNS record inspection`
- `fix: handle empty discovery source`
- `docs: improve release instructions`
- `build: update Android dependency`

## Code of conduct

Be respectful, constructive and focused on the technical work.
