package com.aioj.next.ai.domain;

import com.aioj.next.contract.ai.AiChatRequest;
import com.aioj.next.contract.ai.ProblemDraftRequest;
import com.aioj.next.contract.ai.ProblemDraftResponse;

public interface AiProvider {
    AiCompletion chat(AiChatRequest request);

    ProblemDraftResponse generateProblemDraft(Long id, ProblemDraftRequest request);

    default ProblemDraftResponse regenerateProblemDraft(Long id, ProblemDraftResponse parentDraft, String feedback) {
        throw new UnsupportedOperationException("Provider does not support regeneration");
    }

    String providerName();

    String model();
}
