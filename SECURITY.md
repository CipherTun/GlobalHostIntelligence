# Security Policy

## Supported versions

Security fixes are primarily targeted at the current `main` branch and the latest published release.

Because GHI is under active development, older releases may stop receiving security fixes when a newer release changes the affected component.

## Reporting a vulnerability

Please **do not open a public GitHub issue for an undisclosed security vulnerability**.

Use GitHub's private vulnerability reporting/security advisory mechanism when it is available for this repository:

https://github.com/CipherTun/GlobalHostIntelligence/security/advisories

If private vulnerability reporting is unavailable, contact the repository maintainer privately through the GitHub profile associated with:

https://github.com/CipherTun

When reporting a vulnerability, include:

- A concise description.
- Affected version/commit.
- Reproduction steps or proof of concept.
- Expected behavior.
- Actual behavior.
- Security impact.
- Any relevant logs or screenshots that do not contain secrets or personal information.

Please allow reasonable time for investigation and remediation before public disclosure.

## Secrets

Never include any of the following in an issue, pull request, log, screenshot or public repository commit:

- API keys.
- Access tokens.
- Passwords.
- Signing keys.
- Keystore files.
- Private certificates/keys.
- Personal credentials.
- Private infrastructure details.

If a secret has already been exposed, rotate/revoke it immediately and report the exposure privately.

## Scope

Security reports may include, but are not limited to:

- Remote code execution.
- Authentication/authorization issues.
- Sensitive data exposure.
- Insecure handling of credentials or keys.
- Dangerous parsing or deserialization.
- Web/network request vulnerabilities.
- Android component/export vulnerabilities.
- Dependency vulnerabilities with a demonstrated security impact.
- Build/release pipeline vulnerabilities.

Reports about third-party infrastructure or public data sources should include enough information to distinguish the third-party issue from an issue in GHI itself.
