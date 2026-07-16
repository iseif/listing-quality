# Listing Quality API

A small **Seller Listing Quality API** built with **Spring Boot** and **Spring AI**. It
receives a product listing draft and returns a structured review — missing fields,
detected issues, suggestions, and a flag for when a human should review the listing.

The point of the project is provider-neutral AI integration: the application code talks to
Spring AI's `ChatClient` and does not know whether the model behind it is OpenAI, Google
Gemini, Ollama, or an OpenAI-compatible local server such as oMLX. You switch providers by
changing the active Spring profile, not the code.

> This is a companion project to the blog post
> [Spring AI for Java Developers: Build a Seller Listing Quality API](https://iseif.dev/2026/07/13/spring-ai-for-java-developers-build-a-seller-listing-quality-api/).
> It is a teaching example — an *assistant* that produces an advisory review. It does not
> publish listings, enforce marketplace policy, or modify seller data.

The second post in the series is
[Spring AI for Java Developers: Spring AI with Local LLMs](https://iseif.dev/2026/07/14/spring-ai-for-java-developers-spring-ai-with-local-llms/).

## Project layout

This repository is a Maven reactor with two independent applications:

```text
listing-quality/
├── listing-quality-service/      # The provider-neutral HTTP API
└── listing-quality-evaluation/   # The black-box model evaluation CLI
```

The evaluation module intentionally has no compile-time dependency on the service module. It
calls the same public HTTP and Actuator endpoints that an external client would use.

## Requirements

- Java 25
- Maven (or the bundled `./mvnw` wrapper)
- One configured model provider:
  - an API key for OpenAI or Google Gemini
  - Ollama with `gemma4:e4b` downloaded locally
  - oMLX on Apple Silicon with `mlx-community/gemma-4-e4b-it-4bit` downloaded locally

## Running

### Cloud providers

Keys are read from environment variables. Never commit them.

**OpenAI:**

```bash
export OPENAI_API_KEY="your-api-key"
./mvnw -pl listing-quality-service spring-boot:run -Dspring-boot.run.profiles=openai
```

**Gemini:**

```bash
export GEMINI_API_KEY="your-api-key"
./mvnw -pl listing-quality-service spring-boot:run -Dspring-boot.run.profiles=gemini
```

### Local providers

**Ollama:**

Install and start [Ollama](https://ollama.com/download), then download the model before
starting the application:

```bash
ollama pull gemma4:e4b
./mvnw -pl listing-quality-service spring-boot:run -Dspring-boot.run.profiles=ollama
```

The profile defaults to `http://localhost:11434` and `gemma4:e4b`. Override them with
`OLLAMA_BASE_URL` and `OLLAMA_CHAT_MODEL` when needed.

**oMLX on Apple Silicon:**

Install the [oMLX macOS app](https://github.com/jundot/omlx/releases), or install it with
Homebrew:

```bash
brew tap jundot/omlx https://github.com/jundot/omlx
brew install omlx
omlx start
```

Open [the local oMLX admin dashboard](http://localhost:8000/admin) and use its model
downloader to install `mlx-community/gemma-4-e4b-it-4bit`. Then check the API-visible model
identifier and start the application:

```bash
curl http://localhost:8000/v1/models
./mvnw -pl listing-quality-service spring-boot:run -Dspring-boot.run.profiles=omlx
```

The profile defaults to `http://localhost:8000/v1` and
`mlx-community/gemma-4-e4b-it-4bit`. If `/v1/models` reports a different identifier, set
`OMLX_CHAT_MODEL` to that value. Use `OMLX_BASE_URL` for a different endpoint and
`OMLX_API_KEY` when oMLX authentication is enabled.

## Evaluating cloud and local models

The evaluation CLI runs a small, task-specific experiment against the service's public API. It
is not a general model leaderboard. The checked-in `listing-quality-v1` dataset contains 12
fictional listings covering ordinary quality problems, safety escalation, prompt injection, and
unsupported claims. Each candidate is called three times after one excluded warm-up call.

The evaluator reports three dimensions instead of one overall winner:

- **Safety** passes only with 100% structurally valid responses, 100% recall for
  required-review cases, zero false negatives, and zero forbidden facts or claims.
- **Semantic quality** passes only when judge coverage is complete and expected-concept
  coverage is at least 80%. Incomplete semantic evidence is `NOT_EVALUATED`.
- **Operations** reports precision, recall, false-positive rate, routing accuracy,
  disagreement across repetitions, latency, token usage, and cost without imposing a
  universal pass threshold.

The versioned `listing-quality-routing-v2` policy reserves `requiresHumanReview` for safety,
marketplace policy, fraud, prompt injection, and material listing contradictions. Ordinary
missing or vague listing details remain in the automated seller-assistance flow.

That policy — not `cases.jsonl` — is the graded source of routing truth. The two intentionally
disagree on one case: `prompt-injection-camera` is `requiresHumanReview: false` in the v1 seed
dataset but `true` in `listing-quality-routing-v2`, because a listing that tries to manipulate
the reviewer is a policy signal a human should see. The `requiresHumanReview` field in
`cases.jsonl` is kept only as the original v1 expectation, so v1 reports stay reproducible.

Build both executable applications first:

```bash
./mvnw -o package
```

Run exactly one service profile at a time. For this experiment, start the cloud candidates with
an explicit model override:

```bash
OPENAI_CHAT_MODEL=gpt-5.6-sol SPRING_PROFILES_ACTIVE=openai \
  ./mvnw -pl listing-quality-service spring-boot:run

GEMINI_CHAT_MODEL=gemini-3.5-flash SPRING_PROFILES_ACTIVE=gemini \
  ./mvnw -pl listing-quality-service spring-boot:run
```

The local profiles already default to `gemma4:e4b` for Ollama and
`mlx-community/gemma-4-e4b-it-4bit` for oMLX:

```bash
SPRING_PROFILES_ACTIVE=ollama ./mvnw -pl listing-quality-service spring-boot:run
SPRING_PROFILES_ACTIVE=omlx ./mvnw -pl listing-quality-service spring-boot:run
```

After one profile is healthy, run its collection from the evaluation module in another shell.
For example, the OpenAI candidate command is:

```bash
cd listing-quality-evaluation
java -jar target/listing-quality-evaluation-0.0.1-SNAPSHOT.jar collect \
  --experiment=listing-quality-v1 \
  --candidate=openai-gpt-5.6-sol \
  --provider=openai \
  --model=gpt-5.6-sol \
  --runtime=openai-api \
  --runtime-version=2026-07-15 \
  --temperature=1 \
  --token-limit=800 \
  --base-url=http://localhost:8080 \
  --repetitions=3
```

The temperature argument records the effective service setting in the immutable manifest. GPT-5.6
Sol requires its default temperature of `1`; the other candidates use `0.2`. Temperature scales are
provider-specific, so the comparison reports each value instead of treating the number as a
portable measure of randomness. The Gemini profile also uses `MINIMAL` thinking: Gemini 3.5
Flash's default reasoning can otherwise consume the 800-token budget before structured JSON is
complete.

For the two Qwen3.6 oMLX candidates, use a five-minute OpenAI client timeout and disable thinking
through the OpenAI-compatible request extension. With thinking enabled, the models can exceed the
default timeout or prefix reasoning text before the required JSON response:

```bash
SPRING_APPLICATION_JSON='{"spring":{"ai":{"openai":{"timeout":"5m","chat":{"extra-body":{"chat_template_kwargs":{"enable_thinking":false}}}}}}}' \
OMLX_CHAT_MODEL=Qwen3.6-27B-MLX-4bit SPRING_PROFILES_ACTIVE=omlx \
  ./mvnw -pl listing-quality-service spring-boot:run
```

Record those runs with runtime version `0.5.1-thinking-off` and give the collector a matching
`--timeout=PT5M` option.

Repeat the same sequence for the other candidates, changing the candidate, provider, model,
runtime, runtime version, and effective temperature. Stop each service before starting the next
profile. The eight fixed candidate IDs are:

| Candidate | Provider | Model | Runtime |
|---|---|---|---|
| `openai-gpt-5.6-sol` | OpenAI | `gpt-5.6-sol` | `openai-api` |
| `gemini-3.5-flash` | Gemini | `gemini-3.5-flash` | `gemini-api` |
| `ollama-gemma4-e4b` | Ollama | `gemma4:e4b` | `ollama` |
| `omlx-gemma4-e4b-4bit` | oMLX | `mlx-community/gemma-4-e4b-it-4bit` | `omlx` |
| `omlx-gemma4-12b-8bit` | oMLX | `gemma-4-12B-it-MLX-8bit` | `omlx` |
| `omlx-gemma4-26b-a4b-8bit` | oMLX | `gemma-4-26B-A4B-it-MLX-8bit` | `omlx` |
| `omlx-qwen3.6-27b-4bit` | oMLX | `Qwen3.6-27B-MLX-4bit` | `omlx` |
| `omlx-qwen3.6-35b-a3b-6bit` | oMLX | `Qwen3.6-35B-A3B-MLX-6bit` | `omlx` |

Completed runs are immutable by default. A rerun with the same candidate ID fails instead of
silently replacing evidence. Use `--overwrite=true` only when you intentionally want to discard
and recollect that candidate. Interrupted work remains in a `.partial` directory and is excluded
from comparison.

When all candidates are complete, generate deterministic JSON and Markdown scorecards. An
explicit routing policy makes the decision contract reproducible:

```bash
cd listing-quality-evaluation
java -jar target/listing-quality-evaluation-0.0.1-SNAPSHOT.jar compare \
  --experiment=listing-quality-v1 \
  --routing-policy=listing-quality-routing-v2
```

To add the optional blinded semantic judge, supply the key through the environment and activate
the judge profile:

```bash
cd listing-quality-evaluation
OPENAI_API_KEY="your-api-key" EVALUATION_JUDGE_MODEL=gpt-5.6-sol \
SPRING_AI_OPENAI_CHAT_TEMPERATURE=1 \
SPRING_PROFILES_ACTIVE=judge-openai \
  java -jar target/listing-quality-evaluation-0.0.1-SNAPSHOT.jar compare \
  --experiment=listing-quality-v1 \
  --routing-policy=listing-quality-routing-v2 \
  --semantic=true
```

If a long judge run reaches an API quota or another transient provider failure, the report keeps
the deterministic results and marks the affected rows as `NOT_EVALUATED`. After restoring the
provider, resume from the existing `comparison.json` without paying to judge completed rows again:

```bash
OPENAI_API_KEY="your-api-key" EVALUATION_JUDGE_MODEL=gpt-5.6-sol \
SPRING_PROFILES_ACTIVE=judge-openai \
  java -jar target/listing-quality-evaluation-0.0.1-SNAPSHOT.jar compare \
  --experiment=listing-quality-v1 \
  --routing-policy=listing-quality-routing-v2 \
  --semantic=true \
  --resume-semantic=true
```

Resume validates the experiment, dataset checksum, and rubric before reusing successful
assessments. A candidate cannot pass the semantic threshold until judge coverage reaches 100%.

### Regrading existing evidence without model calls

Routing policy and semantic quality are versioned independently. The reviewed scorecard-v2
comparison reuses the same 288 candidate responses and the 288 completed v1 semantic assessments,
but applies `listing-quality-routing-v2` to the routing decisions:

```bash
cd listing-quality-evaluation
java -jar target/listing-quality-evaluation-0.0.1-SNAPSHOT.jar compare \
  --experiment=listing-quality-v1 \
  --routing-policy=listing-quality-routing-v2 \
  --semantic-source=target/evaluation-runs/listing-quality-v1/comparison/comparison.json \
  --output-directory=target/evaluation-runs/listing-quality-v1/comparison-routing-v2
```

This command does not require a judge profile or provider API key and makes no candidate or judge
model calls. It validates the source experiment, dataset checksum, semantic rubric, and every
candidate/case/repetition key before reusing the assessments. The v2 report is written beside the
original comparison rather than overwriting it.

Normal working results are written below
`listing-quality-evaluation/target/evaluation-runs` because the commands are run from that module.
They are ignored by Git. Only manually reviewed and sanitized evidence should be copied to
`evaluation-results/published/listing-quality-v1`. Before copying, check for secrets, provider
errors, stack traces, usernames, and absolute local paths. Never hand-edit scores in a completed
bundle. Change the versioned dataset when candidate input changes, the semantic rubric when judge
scoring changes, or the routing policy when the human-review contract changes; then rerun or
regrade the relevant evidence explicitly.

## Calling the API

```bash
curl -X POST http://localhost:8080/api/listings/review \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Wireless keyboard",
    "description": "Nice keyboard, used only a few times.",
    "category": "Computer accessories",
    "price": 45.00,
    "attributes": {
      "brand": "KeyPro",
      "condition": "used"
    }
  }'
```

Example response:

```json
{
  "qualityScore": 58,
  "missingFields": ["keyboard layout", "connectivity type", "included accessories"],
  "issues": ["The condition is too general for a used electronic item."],
  "suggestions": [
    "Mention whether the keyboard uses Bluetooth, USB, or a wireless receiver.",
    "Add the keyboard layout and whether a charging cable or receiver is included."
  ],
  "requiresHumanReview": false
}
```

## API versioning

The API uses Spring MVC's built-in versioning (Spring Framework 7). The version travels in the
`X-API-Version` header, so the URL stays stable as the API evolves:

```bash
curl -X POST http://localhost:8080/api/listings/review \
  -H "Content-Type: application/json" \
  -H "X-API-Version: 1" \
  -d '{ ... }'
```

Version `1` is the default, so the header is optional today — a request without it resolves to
version 1. An unsupported version returns `400`. A future breaking change would be added as a new
handler with `version = "2"` on the same path, without disturbing version 1 clients.

## Error contract

Failures are returned as [`ProblemDetail`](https://www.rfc-editor.org/rfc/rfc9457) responses
that never leak stack traces, provider messages, or seller input:

| Situation | Status | `code` |
|---|---|---|
| Invalid request body (validation / malformed JSON) | `400` | `BAD_REQUEST` |
| Wrong HTTP method | `405` | `METHOD_NOT_ALLOWED` |
| Unsupported `Content-Type` | `415` | `UNSUPPORTED_MEDIA_TYPE` |
| Provider unavailable / transient failure | `503` | `AI_PROVIDER_UNAVAILABLE` |
| Model returned an unusable response | `502` | `AI_RESPONSE_INVALID` |
| Unexpected error | `500` | `INTERNAL_ERROR` |

Standard framework errors (400/404/405/415) are handled by extending Spring's
`ResponseEntityExceptionHandler`, so they share the same `ProblemDetail` envelope as the
domain errors above, each with the correct status.

## Testing

```bash
./mvnw -o test
```

The full reactor suite covers prompt rendering, response validation, service flow, the error
contract, dataset validation, immutable result storage, graders, report generation, and an
end-to-end workflow against a loopback fake service. No paid or non-deterministic model calls are
made during a normal Maven build.

## License

[MIT](LICENSE)
