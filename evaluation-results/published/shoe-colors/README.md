# Shoe color qualification results

These reports were produced on 2026-07-19 by the opt-in
`ShoeColorQualificationTest` in `listing-quality-enrichment`.

- Hardware: Apple MacBook Pro, Apple M4 Pro, 48 GB unified memory
- Dataset: 10 original AI-generated cases under
  `listing-quality-enrichment/src/test/resources/shoe-color-evaluation`
- Repetitions: 3 per case, 30 measured calls per model
- Local runtime: oMLX
- Local candidates: `gemma-4-e4b-it-4bit` (4-bit) and
  `gemma-4-26B-A4B-it-MLX-8bit` (8-bit)
- Cloud reference: `gemini-3.5-flash`

The two local candidates were run at different quantization levels, each at the precision it would
realistically be deployed at on this hardware. These reports therefore compare two deployment
options and do not isolate parameter count as the cause of the E4B failures.

The release gate requires:

- zero schema failures
- zero invalid evidence references
- zero background or prop color false positives
- 100% outsole-negative accuracy
- at least 90% primary-color accuracy
- at least 90% color precision
- at least 85% color recall

The 26B local model and Gemini passed. E4B failed the gate and is retained as a
useful negative result. The reports qualify these exact models only for this
prompt, contract, dataset, runtime, and hardware.

Gemini token usage and direct provider cost were not captured because the
production generator maps the response directly to the structured entity. The
experiment therefore does not publish an estimated cloud cost.
