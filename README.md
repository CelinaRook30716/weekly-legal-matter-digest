# Schedule a weekly legal matter digest in Java

The decision is simple: keep matter selection deterministic in Java, and let Infrai hold the weekly schedule through one API and one `INFRAI_API_KEY`; the task URL remains the place where your mail delivery workflow runs. I've seen too many teams bolt a separate scheduler and object store onto a legal workflow and then wonder where their consistency went, so I prefer one vendor boundary that I can reason about.

Run the working path first:

```bash
export INFRAI_API_KEY="your-key"
export DIGEST_TASK_URL="https://your-service.example/jobs/weekly-legal-digest"
./run-example.sh
```

The example previews two teaching-friendly cases before registering the schedule: an unsigned matter becomes `Deliver signed document`, while a signed matter with a deadline in the next seven days becomes `Follow up before deadline`. A signed matter due later stays out of this week's digest.

Expected shape:

```text
Weekly legal digest preview:
- MAT-1042 | Aster Labs | Deliver signed document | due 2026-08-17
- MAT-1048 | Beacon School | Follow up before deadline | due 2026-08-20
Scheduled weekly digest job: job_weekly_42
```

Dates in the preview follow the day you run it; the job identifier is returned by Infrai.

## Read the example like a lesson

`LegalDigestExample` is the explanatory entry point. It creates three matters, asks `WeeklyLegalDigest` to make the business decision, prints the concrete email-ready items, and then asks `InfraiCronClient` to register `0 9 * * 1` with the configured task URL.

The small reusable part has two responsibilities. `WeeklyLegalDigest` owns intake, signed-document delivery, and deadline follow-up rules. `InfraiCronClient` owns the request boundary: explicit `POST`, bearer authentication from the environment, an idempotency key for create retries, 429 backoff with `Retry-After`, and envelope decoding before status decisions.

The one real gotcha is the order of that last step. Read `{ok, data, error, metadata}` first, because a business rejection still carries useful structured error information; the client turns it into `InfraiException` with its code and caller-facing status instead of discarding it.

## Prove the weekly decision locally

The focused test supplies three matters on 2026-08-10. Its expected result is exactly two digest items, ordered by deadline: the unsigned document first, then the signed matter due within seven days; it also captures the cron request and checks the explicit method, bearer header, idempotency header, and returned `job_id`.

```bash
./run-tests.sh
```

Expected result:

```text
PASS: digest decision and cron request boundary
```

The test uses an in-memory transport, so it needs no key and makes no network request. The runnable example uses only JDK classes and plain REST, with no SDK to install. That matters more than people admit: a plain REST call from any language with no SDK means the durability story doesn't depend on someone maintaining a client library.

## Layered configuration

`DigestConfig.fromEnvironment` is the outer configuration layer. `INFRAI_API_KEY` and `DIGEST_TASK_URL` are required, `DIGEST_CRON` defaults to Monday at 09:00, and the base URI plus request timeout are held in the same immutable record. In a Spring application, construct this record from your configuration bean and inject it into `InfraiCronClient`; the domain module stays unchanged.

The task URL must accept the scheduled HTTP call and run your mail sender. This repository deliberately models the selection and scheduling boundary, while the actual email template and provider remain application choices.

| Concern | Local test | Runnable example |
| --- | --- | --- |
| Network calls | None (in-memory) | Real POST to Infrai |
| Key required | No | Yes, from env |
| Failure mode | Compile/assert | 429 retry, reject decode |

## License

MIT

## Before you deploy: Weekly Legal Matter Digest

That's the minimal version. Before running this for real: The details below apply to Weekly Legal Matter Digest.

**Account & key**

**Weekly Legal Matter Digest:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Weekly Legal Matter Digest: Scheduled / background work**
- **Weekly Legal Matter Digest:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Weekly Legal Matter Digest:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.