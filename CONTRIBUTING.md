# Contributing

Thanks for helping. This is a community client; it is not affiliated with Selcom Paytech Ltd.

## The spec is the source of truth

Signing, results and webhooks follow [selcom-spec](https://github.com/selcom-developers/selcom-spec). If this client and the spec disagree, the client is wrong, or the spec needs a fix that every client then picks up. Open the change against the spec first.

## Clean room

Write your own code. Do not copy from Selcom's official clients, from other community clients, or from any private integration. Reading public documentation for behaviour is fine.

## Build and test

You need JDK 17 or later and Maven 3.8.1 or later.

```
mvn verify
```

This compiles with `-Xlint:all -Werror`, runs the tests, builds the jar, the sources jar and the javadoc jar. No test calls the network; tests that need an HTTP server use a throwaway server on loopback.

## Test vectors

`src/test/resources/vectors/` is a copy of the spec's vectors, pinned in `SPEC_VERSION`. Refresh it with:

```
scripts/refresh-vectors.sh            # re-copy at the pinned tag
scripts/refresh-vectors.sh v0.2.0     # move to a new tag
```

Never edit the copy by hand. Run `mvn verify` afterwards; every case, reject, response scenario and webhook sample must pass.

## Pull requests

- One concern per pull request, with tests. Behaviour changes need a line in `CHANGELOG.md` under Unreleased.
- Public classes and methods need Javadoc; the build checks it.
- Money moves through `Money` (whole units with an explicit currency). Never use a floating-point number for an amount.
- Never log or print a secret, a PIN, an `Authorization` or `Digest` header, or a card token. Use `Redactor` for anything you log.

## Test data

Only made-up data: phones `2557000000xx`, ids like `ORD-1001` and `TXN-...`, keys like `test-key-0001`, or the sample values printed in Selcom's public documentation. No real credentials, customer data or details of any merchant's integration, ever.
