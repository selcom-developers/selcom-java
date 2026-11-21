# Changelog

All notable changes to this project are recorded here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.1] - 2026-11-21

### Changed

- The project is now named selcom4j: Maven coordinates `io.github.selcom-developers:selcom4j`, repository `selcom-developers/selcom4j`, User-Agent `selcom4j/<version>`. The Java package is unchanged.

## [0.1.0] - 2026-11-21

### Added

- Configuration with an explicit, defaultless environment (`SANDBOX` or `PRODUCTION`), credentials, vendor, float PIN, optional separate checkout credentials, and per-operation-class timeouts.
- Pure request signer (`RequestSigner`) and a replaceable `Signer` interface, with HS256 implemented.
- `Transport` interface and a default implementation on `java.net.http.HttpClient`, with `NOT_SENT` and `UNKNOWN_OUTCOME` error classification and no automatic retry.
- One typed method per v1 operation: checkout (create order, create order minimal, wallet payment, card payment, order status, cancel order, list orders, stored cards, delete card) and payouts (utility payment, lookup and query, wallet cash-in, name lookup and query, vendor balance).
- Result classification into `SUCCESS`, `PENDING`, `AMBIGUOUS`, `FAILED` and `NOT_FOUND`, with the HTTP status and raw body kept.
- Webhook verification (`WebhookVerifier`) following the spec 0.2.1 rules, with a configurable window and replay store, and payload parsing.
- Log redaction helper (`Redactor`).
- Tests that run the shared spec vectors (signing cases and rejects, response scenarios, webhook samples) from a pinned copy; `SPEC_VERSION` and `scripts/refresh-vectors.sh`.
- GitHub Actions CI on Java 17, 21 and 25.

[Unreleased]: https://github.com/selcom-developers/selcom4j/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/selcom-developers/selcom4j/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/selcom-developers/selcom4j/releases/tag/v0.1.0
