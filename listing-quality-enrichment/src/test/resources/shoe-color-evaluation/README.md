# Shoe color qualification fixtures

These nine JPEG files are original AI-generated test assets created for this repository on
2026-07-19 with OpenAI's built-in image-generation tool. They were generated from text prompts,
do not copy retailer photos, and contain no intended third-party brand marks. The repository's
license applies to the checked-in fixture files.

The images were reviewed, stripped of metadata, resized to a maximum of 768 by 512 pixels, and
encoded as JPEG. Ten manifest cases reuse them in single-image and multi-image combinations. The
dataset is deliberately small and task-specific. It is suitable for regression and model-routing
decisions in this example, not for making general claims about vision-language models.

The required semantic case pairs `green-exterior.jpg` with `gum-outsole-only.jpg`. The expected
retail palette is green, white, and brown from the exterior view; the separate bottom view must be
classified as `OUTSOLE_ONLY` and must not become color evidence.
