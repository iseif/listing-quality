# Listing Quality Model Comparison

Experiment: `listing-quality-v1`

Dataset: `listing-quality-v1`

Rubric: `listing-quality-rubric-v1`

## Product threshold

A candidate passes only with 100% valid responses, 100% human-review recall, zero forbidden claims, at least 80% expected-concept coverage, and no severe inconsistency.

## Candidate results

| Candidate | Temperature | Valid responses | Human review recall | Forbidden claims | Expected concepts | Semantic judge coverage | Median ms | P95 ms | Eligibility | Cost USD |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| gemini-3.5-flash | 0.2 | 100.0% | 100.0% | 0 | 95.1% | 100.0% | 1565 | 2027 | PASS | 0.091530 |
| ollama-gemma4-e4b | 0.2 | 100.0% | 71.4% | 0 | 83.6% | 100.0% | 3465 | 5373 | FAIL | not applicable |
| omlx-gemma4-12b-8bit | 0.2 | 100.0% | 100.0% | 0 | 93.1% | 100.0% | 16550 | 21185 | PASS | not applicable |
| omlx-gemma4-26b-a4b-8bit | 0.2 | 100.0% | 100.0% | 0 | 92.6% | 100.0% | 4664 | 5453 | PASS | not applicable |
| omlx-gemma4-e4b-4bit | 0.2 | 100.0% | 100.0% | 0 | 86.4% | 100.0% | 2352 | 3636 | PASS | not applicable |
| omlx-qwen3.6-27b-4bit | 0.2 | 100.0% | 100.0% | 0 | 98.4% | 100.0% | 21227 | 25478 | PASS | not applicable |
| omlx-qwen3.6-35b-a3b-6bit | 0.2 | 100.0% | 100.0% | 0 | 94.4% | 100.0% | 4396 | 5348 | FAIL | not applicable |
| openai-gpt-5.6-sol | 1 | 100.0% | 100.0% | 0 | 94.9% | 100.0% | 6230 | 9029 | FAIL | 0.372120 |

Cost is an estimate when token metrics and a dated cloud price are available. Local cost is not applicable here and excludes hardware, electricity, engineering, and hosting.

## Limitations

- The dataset is intentionally small and task specific.
- Semantic scores depend on one judge model and require human audit.
- Local latency depends on the recorded hardware and runtime.
- Cloud prices are estimates from a dated price table.
- Model and hosted-service behavior can drift after collection.
