package com.aioj.next.problem.controller;

import com.aioj.next.common.api.ApiResponse;
import com.aioj.next.contract.problem.TestcasePackageResponse;
import com.aioj.next.contract.problem.TestcaseUploadCompleteRequest;
import com.aioj.next.contract.problem.TestcaseUploadInitRequest;
import com.aioj.next.contract.problem.TestcaseUploadInitResponse;
import com.aioj.next.contract.problem.TestcaseUploadStatusResponse;
import com.aioj.next.problem.domain.testcase.TestcasePackageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/problems/{problemId}/testcase-packages")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class TestcasePackageController {
    private final TestcasePackageService testcasePackageService;

    public TestcasePackageController(TestcasePackageService testcasePackageService) {
        this.testcasePackageService = testcasePackageService;
    }

    @PostMapping("/init")
    public ApiResponse<TestcaseUploadInitResponse> init(@PathVariable Long problemId,
                                                        @RequestBody @Valid TestcaseUploadInitRequest request) {
        return ApiResponse.ok(testcasePackageService.init(problemId, request));
    }

    @PutMapping(value = "/uploads/{uploadId}/chunks/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResponse<TestcaseUploadStatusResponse> uploadChunk(@PathVariable Long problemId,
                                                                 @PathVariable String uploadId,
                                                                 @PathVariable int index,
                                                                 @RequestHeader(value = "X-Chunk-Sha256", required = false)
                                                                 String chunkSha256,
                                                                 HttpServletRequest request) throws IOException {
        return ApiResponse.ok(testcasePackageService.uploadChunk(problemId, uploadId, index,
                chunkSha256, request.getInputStream()));
    }

    @PostMapping("/uploads/{uploadId}/complete")
    public ApiResponse<TestcasePackageResponse> complete(@PathVariable Long problemId,
                                                         @PathVariable String uploadId,
                                                         @RequestBody(required = false) @Valid
                                                         TestcaseUploadCompleteRequest request) {
        return ApiResponse.ok(testcasePackageService.complete(problemId, uploadId, request));
    }

    @GetMapping("/uploads/{uploadId}/status")
    public ApiResponse<TestcaseUploadStatusResponse> status(@PathVariable Long problemId,
                                                            @PathVariable String uploadId) {
        return ApiResponse.ok(testcasePackageService.status(problemId, uploadId));
    }

    @GetMapping
    public ApiResponse<List<TestcasePackageResponse>> list(@PathVariable Long problemId) {
        return ApiResponse.ok(testcasePackageService.list(problemId));
    }

    @PostMapping("/{packageId}/activate")
    public ApiResponse<TestcasePackageResponse> activate(@PathVariable Long problemId, @PathVariable Long packageId) {
        return ApiResponse.ok(testcasePackageService.activate(problemId, packageId));
    }
}
