# Security policy

## Reporting a vulnerability

Please report security problems privately, not in a public issue. Use GitHub's private vulnerability reporting for this repository (the "Security" tab, then "Report a vulnerability"). Include what you found, how to reproduce it, and which version.

You can expect an acknowledgement within a few days. We will fix confirmed problems and credit you if you want it.

## Scope

This library signs requests, sends them, classifies responses and verifies webhooks. Reports about signing, secret handling, logging of secrets or personal data, webhook verification and unsafe retry behaviour are in scope.

Problems in Selcom's gateway or API are not ours to fix; report those to Selcom through their developer portal at <https://developers.selcommobile.com/>.

## Never post secrets

Do not put real API keys, secrets, PINs, tokens or customer data in issues, pull requests or test fixtures. If you leaked a credential, rotate it with Selcom.

Not affiliated with Selcom Paytech Ltd.
