# Security

Please do not publish credentials, private proxy endpoints or a working exploit in a public issue.

Report vulnerabilities through GitHub **Security → Advisories → Report a vulnerability**,
if private vulnerability reporting is enabled for this repository. If it is unavailable,
contact the maintainer through the official [KupuProxy channel](https://t.me/KupuProxy)
without posting exploit details publicly; ask for a private reporting route.

Include the affected version/platform, impact, reproduction steps and a minimal sanitized example.
No response-time SLA is promised. Use the latest release; older releases are not maintained separately.

Public proxies are operated by third parties. HTTPS feed validation and availability checks do not
make a proxy trusted. Never use public proxies as a replacement for end-to-end encryption.

Release APKs must preserve the existing signing certificate. Keystores, passwords and API tokens
must not be committed. See [release signing](docs/releases/SIGNING.md).
