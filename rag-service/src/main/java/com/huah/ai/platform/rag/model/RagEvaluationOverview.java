package com.huah.ai.platform.rag.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RagEvaluationOverview {
    long totalQueries;
    long feedbackCount;
    long positiveFeedbackCount;
    long negativeFeedbackCount;
    double positiveFeedbackRate;
    long evidenceFeedbackCount;
    long positiveEvidenceCount;
    long negativeEvidenceCount;
    double positiveEvidenceRate;
    long lowRatedQueryCount;
}
