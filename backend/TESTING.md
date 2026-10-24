# Backend testing

See the [application verification guide](../docs/APPLICATION_TESTING.md) for Java/FFmpeg regressions, fresh-schema migration checks, real API workflows, private fixtures and browser playback verification.

The application uses Java 25 preview features. Full context tests require configured PostgreSQL, Kafka and storage; real workflow tests also require development SMTP/Mailhog. Never run fixture-creating suites against production data.
