# SeeAssist model evaluation notes

This page records the Visual Question Answering (VQA) checkpoint comparisons and explains the classification metrics discussed during the project. **Development VQA scores are real recorded experiment outputs; the confusion matrix below is an educational example, not a measured SeeAssist result.**

## Dataset and evaluation approach

- Dataset: **VizWiz** visual-question-answering images with human reference answers.
- Development comparison: **200 corrected examples**, reused across checkpoints.
- Reserved holdout: **400 different validation examples** reserved for a later, locked evaluation.
- Scoring: an **approximate VQA-style consensus score**, based on agreement with multiple reference answers after normalization. This is not ordinary classification accuracy and not an official VizWiz leaderboard score.
- The comparison evaluates VQA responses; separate OCR/object-detection performance numbers are **not** implied by these results.

## Recorded development results

### Phase 3 checkpoint comparison

Each checkpoint was evaluated on the same 200 development examples.

| Checkpoint | Overall VQA-style score | Answerable | Unanswerable |
| --- | ---: | ---: | ---: |
| Phase 2 reference | 63.45% | 61.03% | 68.59% |
| Phase 3 — after 300 | 63.60% | 61.03% | 69.06% |
| Phase 3 — after 600 | 64.10% | 61.03% | 70.62% |
| Phase 3 — after 900 | 64.40% | 61.47% | 70.62% |
| **Phase 3 — after 1,200** | **64.60%** | **61.47%** | **71.25%** |

Source: recorded Phase 3 development evaluation console summary. These are preliminary development measurements, not performance on the reserved holdout.

### Phase 4 checkpoint comparison

These checkpoints were also evaluated on the **same 200 development examples**.

| Checkpoint | Overall VQA-style score | Answerable | Unanswerable |
| --- | ---: | ---: | ---: |
| Phase 3 — after 1,200 | 64.60% | 61.47% | 71.25% |
| Phase 4 — after 500 | 64.25% | 61.25% | 70.62% |
| Phase 4 — after 1,000 | 64.20% | 61.18% | 70.62% |
| Phase 4 — after 1,500 | 64.75% | 61.47% | 71.72% |

The Phase 4 after-1,500 result was numerically highest on this development subset (by 0.15 percentage points over Phase 3 after-1,200). Recorded paired comparisons showed **2 examples better, 2 worse, and 196 unchanged** relative to Phase 3. This difference is too small to claim a meaningful improvement from this subset alone.

**Checkpoint selection:** Phase 3 `after_1200` was frozen before the proposed 400-image holdout evaluation. Do not change the chosen model based on held-out results.

**Holdout status:** The reserved 400-example evaluation procedure was prepared, but the available notebook/output record does not verify a completed run or provide a final score. Do not claim a 400-image final accuracy until the full run and output are available.

## Confusion matrix: answerable vs. unanswerable

VQA generally generates open-ended text, so a simple binary confusion matrix is **not** a substitute for VQA answer scoring. A confusion matrix can still be useful for the narrower problem of identifying whether the question is *unanswerable* from the image.

Define **positive = Unanswerable**.

| Actual class \\ Predicted class | Predicted unanswerable | Predicted answerable |
| --- | ---: | ---: |
| **Actually unanswerable** | True positive (TP) | False negative (FN) |
| **Actually answerable** | False positive (FP) | True negative (TN) |

- **TP:** correctly says an unanswerable image/question cannot be answered.
- **FN:** incorrectly tries to answer an unanswerable question.
- **FP:** incorrectly rejects an answerable question.
- **TN:** correctly treats an answerable question as answerable.

### Illustrative worked example — NOT experimental SeeAssist results

| Actual class \\ Predicted class | Predicted unanswerable | Predicted answerable |
| --- | ---: | ---: |
| Actually unanswerable | TP = 30 | FN = 20 |
| Actually answerable | FP = 10 | TN = 40 |

In this **hypothetical** 100-example case:

- **Accuracy** = (TP + TN) / (TP + TN + FP + FN) = (30 + 40) / 100 = **70%**
- **Precision** = TP / (TP + FP) = 30 / 40 = **75%**
- **Recall** = TP / (TP + FN) = 30 / 50 = **60%**
- **F1-score** = 2 × (Precision × Recall) / (Precision + Recall) = **66.7%**

These numbers demonstrate how the measures work. **No actual SeeAssist answerability confusion matrix, precision, recall or F1-score has been computed in the recorded experiments.** Do not put these example numbers on a results poster, résumé or project abstract as genuine performance.

## Interpreting the measurements

- **VQA-style score:** how well a generated answer agrees with human answers; partial credit can occur.
- **Answerable / unanswerable VQA-style scores:** separate VQA consensus-score averages by annotated answerability, *not* binary classification precision or recall.
- **Precision:** among predicted unanswerable cases, how many really were unanswerable?
- **Recall:** among actually unanswerable cases, how many did the model correctly detect?
- **F1:** the harmonic mean of precision and recall.
- **Limits:** a development-subset result may not transfer to different lighting, blur, text, devices or real assistive use.

## Reproducibility and next measurement

1. Export the sanitized evaluation notebook and minimal configuration to the repository, without private tokens, photographs or model weights.
2. Preserve a fixed list of image IDs and separate development versus holdout splits.
3. Run the reserved 400-example holdout **once** on the frozen Phase 3 checkpoint; save the raw output and summary with the exact scoring script/version.
4. For an **actual** binary answerability confusion matrix, collect true labels and predicted answerability on that evaluation set, define the classification threshold/rule, and compute TP/FP/TN/FN plus metrics.
5. Report known limitations and keep all measurements labeled by split (development vs held-out validation).

This document records experiments and current gaps rather than treating planned evaluations as completed.
