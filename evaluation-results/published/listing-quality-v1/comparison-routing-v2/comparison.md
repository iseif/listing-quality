# Listing Quality Model Comparison

Experiment: `listing-quality-v1`

Dataset: `listing-quality-v1`

Semantic rubric: `listing-quality-rubric-v1`

Routing policy: `listing-quality-routing-v2`

## How to read this scorecard

Safety and semantic quality answer different questions. Operations intentionally has no global pass state because acceptable reviewer load, latency, and cost depend on the product.

## Safety

| Candidate | Valid responses | Required-review recall | False negatives | Forbidden claims | Status |
| --- | ---: | ---: | ---: | ---: | --- |
| gemini-3.5-flash | 100.0% | 100.0% | 0 | 0 | PASS |
| ollama-gemma4-e4b | 100.0% | 75.0% | 6 | 0 | FAIL |
| omlx-gemma4-12b-8bit | 100.0% | 100.0% | 0 | 0 | PASS |
| omlx-gemma4-26b-a4b-8bit | 100.0% | 100.0% | 0 | 0 | PASS |
| omlx-gemma4-e4b-4bit | 100.0% | 100.0% | 0 | 0 | PASS |
| omlx-qwen3.6-27b-4bit | 100.0% | 100.0% | 0 | 0 | PASS |
| omlx-qwen3.6-35b-a3b-6bit | 100.0% | 100.0% | 0 | 0 | PASS |
| openai-gpt-5.6-sol | 100.0% | 100.0% | 0 | 0 | PASS |

## Semantic quality

Expected-concept coverage and judge coverage reuse the v1 semantic evidence.

| Candidate | Expected concepts (v1) | Semantic judge coverage | Status |
| --- | ---: | ---: | --- |
| gemini-3.5-flash | 95.1% | 100.0% | PASS |
| ollama-gemma4-e4b | 83.6% | 100.0% | PASS |
| omlx-gemma4-12b-8bit | 93.1% | 100.0% | PASS |
| omlx-gemma4-26b-a4b-8bit | 92.6% | 100.0% | PASS |
| omlx-gemma4-e4b-4bit | 86.4% | 100.0% | PASS |
| omlx-qwen3.6-27b-4bit | 98.4% | 100.0% | PASS |
| omlx-qwen3.6-35b-a3b-6bit | 94.4% | 100.0% | PASS |
| openai-gpt-5.6-sol | 94.9% | 100.0% | PASS |

## Operations

| Candidate | TP | FN | FP | TN | Precision | Recall | False-positive rate | Accuracy | Disagreement cases | Disagreement rate | Median ms | P95 ms | Cost USD |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| gemini-3.5-flash | 24 | 0 | 0 | 12 | 100.0% | 100.0% | 0.0% | 100.0% | 0 | 0.0% | 1565 | 2027 | 0.091530 |
| ollama-gemma4-e4b | 18 | 6 | 0 | 12 | 100.0% | 75.0% | 0.0% | 83.3% | 0 | 0.0% | 3465 | 5373 | not applicable |
| omlx-gemma4-12b-8bit | 24 | 0 | 6 | 6 | 80.0% | 100.0% | 50.0% | 83.3% | 0 | 0.0% | 16550 | 21185 | not applicable |
| omlx-gemma4-26b-a4b-8bit | 24 | 0 | 6 | 6 | 80.0% | 100.0% | 50.0% | 83.3% | 0 | 0.0% | 4664 | 5453 | not applicable |
| omlx-gemma4-e4b-4bit | 24 | 0 | 0 | 12 | 100.0% | 100.0% | 0.0% | 100.0% | 0 | 0.0% | 2352 | 3636 | not applicable |
| omlx-qwen3.6-27b-4bit | 24 | 0 | 0 | 12 | 100.0% | 100.0% | 0.0% | 100.0% | 0 | 0.0% | 21227 | 25478 | not applicable |
| omlx-qwen3.6-35b-a3b-6bit | 24 | 0 | 1 | 11 | 96.0% | 100.0% | 8.3% | 97.2% | 1 | 8.3% | 4396 | 5348 | not applicable |
| openai-gpt-5.6-sol | 24 | 0 | 4 | 8 | 85.7% | 100.0% | 33.3% | 88.9% | 1 | 8.3% | 6230 | 9029 | 0.372120 |

Cost is an estimate when token metrics and a dated cloud price are available. Local cost is not applicable here and excludes hardware, electricity, engineering, and hosting.

## Limitations

- The dataset is intentionally small and task specific.
- Semantic quality reuses v1 assessments from one judge model and requires human audit.
- The v1 judge context contained the previous routing expectation, which may have influenced expected-concept coverage even though routing correctness was not scored.
- Local latency depends on the recorded hardware and runtime.
- Cloud prices are estimates from a dated price table.
- Model and hosted-service behavior can drift after collection.
