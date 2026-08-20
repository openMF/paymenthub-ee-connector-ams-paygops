# paymenthub-ee-connector-ams-paygops

A Payment Hub EE connector for **PaygOps**, the account management system of Solaris Offgrid: it
checks with PaygOps whether a customer payment is acceptable, and tells PaygOps once the money has
actually been collected.

[![License](https://img.shields.io/badge/License-MPL--2.0-blue.svg)](LICENSE)

## What it does

- Validates a payment against PaygOps before the money moves, so a payment for an unknown or
  ineligible customer is refused early.
- Confirms the payment to PaygOps after collection, which is what actually credits the customer.
- Answers the paybill validation call at `POST /api/v1/paybill/validate/paygops` and the
  confirmation call at `POST /api/paymentHub/Confirmation`.
- Runs Zeebe (Camunda) workers for the `transfer-validation-paygops` and
  `transfer-settlement-paygops` job types.

## How it fits into Payment Hub EE

Payment Hub EE runs each payment as a Zeebe (Camunda) workflow. PaygOps is the AMS side of that
flow: it owns the customer accounts and decides whether a payment is valid. When the workflow
reaches the validation step, this connector's Zeebe worker calls the PaygOps API and reports back
yes or no; after the money is collected, the settlement worker calls PaygOps again to confirm it.
The connector is the translator between the two, and keeps no state of its own.

## Tech stack

- Java 21
- Spring Boot 3.4 (Web, Actuator)
- Apache Camel 4 (routes for validation, confirmation and the callbacks)
- Zeebe / Camunda workers (via the Zeebe Java client)
- Gradle build
- Depends on `paymenthub-ee-bom` for versions

## Build and run

    ./gradlew clean build          # compiles and runs the tests
    ./gradlew bootRun              # runs the connector locally
    docker build -t paymenthub-ee-connector-ams-paygops .

The connector listens on port 5000 for the Camel REST routes and 8080 for Spring Boot and the
actuator endpoints. It expects a Zeebe broker at `zeebe.broker.contactpoint` (`localhost:26500` by
default).

Two settings must come from the environment, because they point at a real PaygOps tenant:

- `PAYGOPS_BASE_URL` — the PaygOps host.
- `PAYGOPS_AUTHHEADER` — the bearer token used on every call. There is no default, so the
  connector will not start until you set it. That is on purpose: an empty token starts up looking
  healthy and then fails on every call to PaygOps.

## Branches

- `dev` is the active development branch — all PRs should target `dev`.
- `main` holds released versions.

## Contributing

See [contributing.md](contributing.md), our [Code of Conduct](CODE_OF_CONDUCT.md) and the [security policy](security.md).
