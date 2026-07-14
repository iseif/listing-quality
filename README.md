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
./mvnw spring-boot:run -Dspring-boot.run.profiles=openai
```

**Gemini:**

```bash
export GEMINI_API_KEY="your-api-key"
./mvnw spring-boot:run -Dspring-boot.run.profiles=gemini
```

### Local providers

**Ollama:**

Install and start [Ollama](https://ollama.com/download), then download the model before
starting the application:

```bash
ollama pull gemma4:e4b
./mvnw spring-boot:run -Dspring-boot.run.profiles=ollama
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
./mvnw spring-boot:run -Dspring-boot.run.profiles=omlx
```

The profile defaults to `http://localhost:8000/v1` and
`mlx-community/gemma-4-e4b-it-4bit`. If `/v1/models` reports a different identifier, set
`OMLX_CHAT_MODEL` to that value. Use `OMLX_BASE_URL` for a different endpoint and
`OMLX_API_KEY` when oMLX authentication is enabled.

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
./mvnw test
```

Unit tests cover the prompt rendering, response validation, service flow, and error contract
using a fake generator — no paid or non-deterministic model calls are made during the build.

## License

[MIT](LICENSE)
