package com.aioj.next.ai.controller;

import com.aioj.next.ai.domain.AiCompletion;
import com.aioj.next.ai.domain.AiConversationService;
import com.aioj.next.ai.domain.AiProvider;
import com.aioj.next.ai.domain.AiQuotaService;
import com.aioj.next.ai.domain.ProblemDraftStore;
import com.aioj.next.ai.persistence.entity.AiConversationEntity;
import com.aioj.next.common.api.ApiResponse;
import com.aioj.next.common.api.PageResponse;
import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.common.security.SecuritySupport;
import com.aioj.next.contract.ai.AiChatMessageResponse;
import com.aioj.next.contract.ai.AiChatRequest;
import com.aioj.next.contract.ai.AiConversationResponse;
import com.aioj.next.contract.ai.AiUsageResponse;
import com.aioj.next.contract.ai.ProblemDraftApprovalRequest;
import com.aioj.next.contract.ai.ProblemDraftRequest;
import com.aioj.next.contract.ai.ProblemDraftResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RestController
public class AiController {
    private final AiProvider aiProvider;
    private final AiQuotaService aiQuotaService;
    private final AiConversationService aiConversationService;
    private final ProblemDraftStore problemDraftStore;

    public AiController(
            AiProvider aiProvider,
            AiQuotaService aiQuotaService,
            AiConversationService aiConversationService,
            ProblemDraftStore problemDraftStore
    ) {
        this.aiProvider = aiProvider;
        this.aiQuotaService = aiQuotaService;
        this.aiConversationService = aiConversationService;
        this.problemDraftStore = problemDraftStore;
    }

    @PostMapping("/ai/chat/send")
    public ApiResponse<AiChatMessageResponse> send(@RequestBody @Valid AiChatRequest request) {
        Long userId = SecuritySupport.currentUserId();
        aiQuotaService.assertAvailable(userId);
        AiConversationEntity conversation = aiConversationService.resolveForWrite(userId, request);
        aiConversationService.appendMessage(conversation.getId(), userId, "user", request.message(), null);
        boolean usageRecorded = false;
        try {
            AiCompletion completion = aiProvider.chat(request);
            aiQuotaService.record(
                    userId,
                    completion.provider(),
                    completion.model(),
                    completion.promptTokens(),
                    completion.completionTokens(),
                    true
            );
            usageRecorded = true;
            AiChatMessageResponse assistant = aiConversationService.appendMessage(
                    conversation.getId(),
                    userId,
                    "assistant",
                    completion.content(),
                    completion.model()
            );
            return ApiResponse.ok(assistant);
        } catch (RuntimeException ex) {
            if (!usageRecorded) {
                aiQuotaService.record(userId, aiProvider.providerName(), aiProvider.model(), 0, 0, false);
            }
            throw providerFailure(ex);
        }
    }

    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody @Valid AiChatRequest request) {
        Long userId = SecuritySupport.currentUserId();
        aiQuotaService.assertAvailable(userId);
        AiConversationEntity conversation = aiConversationService.resolveForWrite(userId, request);
        aiConversationService.appendMessage(conversation.getId(), userId, "user", request.message(), null);

        StreamingResponseBody body = output -> {
            boolean usageRecorded = false;
            try {
                writeSseEvent(output, "meta", "{\"conversationId\":\"" + conversation.getId() + "\"}");
                AiCompletion completion = aiProvider.chat(request);
                aiQuotaService.record(
                        userId,
                        completion.provider(),
                        completion.model(),
                        completion.promptTokens(),
                        completion.completionTokens(),
                        true
                );
                usageRecorded = true;
                for (String part : streamParts(completion.content())) {
                    writeSseEvent(output, "message", part);
                }
                aiConversationService.appendMessage(conversation.getId(), userId, "assistant", completion.content(), completion.model());
                writeSseEvent(output, "done", "[DONE]");
            } catch (IOException ex) {
                if (!usageRecorded) {
                    aiQuotaService.record(userId, aiProvider.providerName(), aiProvider.model(), 0, 0, false);
                }
            } catch (RuntimeException ex) {
                if (!usageRecorded) {
                    aiQuotaService.record(userId, aiProvider.providerName(), aiProvider.model(), 0, 0, false);
                }
                sendStreamError(output, ex);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    @GetMapping("/ai/conversations")
    public ApiResponse<PageResponse<AiConversationResponse>> conversations(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize
    ) {
        return ApiResponse.ok(aiConversationService.list(SecuritySupport.currentUserId(), page, pageSize));
    }

    @GetMapping("/ai/conversations/{id}/messages")
    public ApiResponse<List<AiChatMessageResponse>> messages(@PathVariable String id) {
        return ApiResponse.ok(aiConversationService.messages(SecuritySupport.currentUserId(), id));
    }

    @DeleteMapping("/ai/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable String id) {
        aiConversationService.delete(SecuritySupport.currentUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/ai/problem-drafts/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<ProblemDraftResponse> generateDraft(@RequestBody @Valid ProblemDraftRequest request) {
        return ApiResponse.ok(problemDraftStore.generate(SecuritySupport.currentUserId(), request));
    }

    @GetMapping("/admin/problem-drafts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ProblemDraftResponse>> listDrafts(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(problemDraftStore.list(page, pageSize, status));
    }

    @PostMapping("/admin/problem-drafts/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProblemDraftResponse> approveDraft(
            @PathVariable Long id,
            @RequestBody(required = false) ProblemDraftApprovalRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        return ApiResponse.ok(problemDraftStore.approve(id, SecuritySupport.currentUserId(), request, authorization));
    }

    @GetMapping("/ai/usage/me")
    public ApiResponse<AiUsageResponse> usage() {
        return ApiResponse.ok(aiQuotaService.usage(SecuritySupport.currentUserId()));
    }

    private DomainException providerFailure(RuntimeException ex) {
        if (ex instanceof DomainException domainException) {
            return domainException;
        }
        return new DomainException(ErrorCode.INTERNAL_ERROR, "AI provider call failed: " + ex.getMessage());
    }

    private void sendStreamError(OutputStream output, Exception ex) {
        try {
            writeSseEvent(output, "error", ex.getMessage() == null ? "AI provider call failed" : ex.getMessage());
            writeSseEvent(output, "done", "[DONE]");
        } catch (IOException ignored) {
            // Client already left; the response stream can be closed quietly.
        }
    }

    private void writeSseEvent(OutputStream output, String event, String data) throws IOException {
        output.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        String payload = data == null ? "" : data;
        for (String line : payload.split("\\R", -1)) {
            output.write(("data: " + line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        output.write('\n');
        output.flush();
    }

    private List<String> streamParts(String content) {
        if (content == null || content.isEmpty()) {
            return List.of("");
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char value = content.charAt(i);
            current.append(value);
            if (isSentenceBoundary(value) || current.length() >= 96) {
                addPart(parts, current);
            }
        }
        addPart(parts, current);
        return parts;
    }

    private boolean isSentenceBoundary(char value) {
        return value == '。' || value == '！' || value == '？'
                || value == '.' || value == '!' || value == '?' || value == '\n';
    }

    private void addPart(List<String> parts, StringBuilder current) {
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        current.setLength(0);
    }
}
