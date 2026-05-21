package com.aioj.next.problem.domain.testcase;

import com.aioj.next.common.error.DomainException;
import com.aioj.next.common.error.ErrorCode;
import com.aioj.next.common.security.SecuritySupport;
import com.aioj.next.contract.problem.TestcasePackageCaseResponse;
import com.aioj.next.contract.problem.TestcasePackageResponse;
import com.aioj.next.contract.problem.TestcasePackageStatus;
import com.aioj.next.contract.problem.TestcaseUploadCompleteRequest;
import com.aioj.next.contract.problem.TestcaseUploadInitRequest;
import com.aioj.next.contract.problem.TestcaseUploadInitResponse;
import com.aioj.next.contract.problem.TestcaseUploadStatusResponse;
import com.aioj.next.problem.config.TestcaseProperties;
import com.aioj.next.problem.domain.ProblemCatalog;
import com.aioj.next.problem.persistence.entity.TestcasePackageCaseEntity;
import com.aioj.next.problem.persistence.entity.TestcasePackageEntity;
import com.aioj.next.problem.persistence.entity.TestcaseUploadChunkEntity;
import com.aioj.next.problem.persistence.entity.TestcaseUploadSessionEntity;
import com.aioj.next.problem.persistence.mapper.TestcasePackageCaseMapper;
import com.aioj.next.problem.persistence.mapper.TestcasePackageMapper;
import com.aioj.next.problem.persistence.mapper.TestcaseUploadChunkMapper;
import com.aioj.next.problem.persistence.mapper.TestcaseUploadSessionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Service
public class TestcasePackageService {
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final int MAX_TOTAL_CHUNKS = 10_000;
    private static final int ERROR_LIMIT = 1_000;
    private static final String PENDING_VERSION = "pending";

    private final ProblemCatalog problemCatalog;
    private final TestcaseProperties properties;
    private final TestcaseStorageService storageService;
    private final TestcasePackageValidator validator;
    private final TestcasePackageMapper packageMapper;
    private final TestcasePackageCaseMapper caseMapper;
    private final TestcaseUploadSessionMapper sessionMapper;
    private final TestcaseUploadChunkMapper chunkMapper;

    public TestcasePackageService(ProblemCatalog problemCatalog,
                                  TestcaseProperties properties,
                                  TestcaseStorageService storageService,
                                  TestcasePackageValidator validator,
                                  TestcasePackageMapper packageMapper,
                                  TestcasePackageCaseMapper caseMapper,
                                  TestcaseUploadSessionMapper sessionMapper,
                                  TestcaseUploadChunkMapper chunkMapper) {
        this.problemCatalog = problemCatalog;
        this.properties = properties;
        this.storageService = storageService;
        this.validator = validator;
        this.packageMapper = packageMapper;
        this.caseMapper = caseMapper;
        this.sessionMapper = sessionMapper;
        this.chunkMapper = chunkMapper;
    }

    @Transactional
    public TestcaseUploadInitResponse init(Long problemId, TestcaseUploadInitRequest request) {
        requireProblem(problemId);
        InitSpec spec = validateInit(request);
        TestcasePackageEntity existing = findPackageBySha(problemId, spec.sha256());
        if (existing != null && existing.getStatus() == TestcasePackageStatus.READY) {
            TestcaseUploadSessionEntity session = createSession(problemId, spec, existing.getId(),
                    TestcasePackageStatus.READY, spec.totalChunks(), null);
            return toInitResponse(session, "Testcase package already exists");
        }
        if (existing != null && existing.getStatus() == TestcasePackageStatus.PROCESSING) {
            throw new DomainException(ErrorCode.CONFLICT, "Testcase package is still processing");
        }
        if (existing != null && existing.getStatus() == TestcasePackageStatus.UPLOADING) {
            TestcaseUploadSessionEntity session = latestSession(problemId, spec.sha256(), existing.getId());
            if (session != null) {
                return toInitResponse(session, "Testcase upload already initialized");
            }
        }

        TestcasePackageEntity testcasePackage = existing == null
                ? createPackage(problemId, spec)
                : resetFailedPackage(existing, spec);
        TestcaseUploadSessionEntity session = createSession(problemId, spec, testcasePackage.getId(),
                TestcasePackageStatus.UPLOADING, 0, null);
        return toInitResponse(session, "Testcase upload initialized");
    }

    public TestcaseUploadStatusResponse uploadChunk(Long problemId, String uploadId, int index,
                                                    String chunkSha256Header, InputStream input) {
        TestcaseUploadSessionEntity session = requireUploadSession(problemId, uploadId);
        assertUploading(session);
        if (index < 0 || index >= session.getTotalChunks()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Chunk index is out of range");
        }
        String expectedChunkSha = normalizeOptionalSha(chunkSha256Header, "X-Chunk-Sha256");
        TestcaseStorageService.StoredChunk staged = storageService.writeStagingChunk(uploadId, index, input);
        try {
            validateChunkSize(session, index, staged.sizeBytes());
            if (expectedChunkSha != null && !expectedChunkSha.equals(staged.sha256())) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Chunk SHA-256 does not match X-Chunk-Sha256");
            }

            TestcaseUploadChunkEntity existing = findChunk(uploadId, index);
            if (existing != null) {
                if (!existing.getChunkSizeBytes().equals(staged.sizeBytes()) || !existing.getSha256().equals(staged.sha256())) {
                    throw new DomainException(ErrorCode.CONFLICT, "Uploaded chunk differs from existing chunk");
                }
                storageService.commitTempChunk(staged.stagingPath(), uploadId, index);
                refreshUploadedChunkCount(session);
                return status(problemId, uploadId);
            }

            String storagePath = storageService.commitTempChunk(staged.stagingPath(), uploadId, index);
            TestcaseUploadChunkEntity chunk = new TestcaseUploadChunkEntity();
            chunk.setUploadId(uploadId);
            chunk.setChunkIndex(index);
            chunk.setChunkSizeBytes(staged.sizeBytes());
            chunk.setSha256(staged.sha256());
            chunk.setStoragePath(storagePath);
            chunk.setCreatedAt(Instant.now());
            chunkMapper.insert(chunk);
            refreshUploadedChunkCount(session);
            return status(problemId, uploadId);
        } catch (RuntimeException ex) {
            storageService.deleteIfExists(staged.stagingPath());
            throw ex;
        }
    }

    public TestcasePackageResponse complete(Long problemId, String uploadId, TestcaseUploadCompleteRequest request) {
        TestcaseUploadSessionEntity session = requireUploadSession(problemId, uploadId);
        if (session.getStatus() == TestcasePackageStatus.READY) {
            return toResponse(requirePackage(session.getPackageId()));
        }
        assertUploading(session);
        List<TestcaseUploadChunkEntity> chunks = chunks(uploadId);
        verifyCompleteChunks(session, chunks);
        markProcessing(session);
        try {
            TestcaseStorageService.MergeResult merged = storageService.mergePackage(uploadId, problemId,
                    session.getSha256(), session.getTotalChunks());
            if (merged.sizeBytes() != session.getFileSizeBytes()) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Merged testcase package size does not match init request");
            }
            if (!merged.sha256().equals(session.getSha256())) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Merged testcase package SHA-256 does not match init request");
            }
            TestcasePackageValidator.ValidatedPackage validated = validator.validate(merged.packagePath());
            saveReadyPackage(session, merged, validated);
            storageService.deleteTempUpload(uploadId);
            return toResponse(requirePackage(session.getPackageId()));
        } catch (RuntimeException ex) {
            markFailed(session, userMessage(ex));
            if (ex instanceof DomainException domainException) {
                throw domainException;
            }
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Failed to process testcase package");
        }
    }

    public TestcaseUploadStatusResponse status(Long problemId, String uploadId) {
        TestcaseUploadSessionEntity session = requireUploadSession(problemId, uploadId);
        List<Integer> uploadedChunks = session.getStatus() == TestcasePackageStatus.READY
                ? fullChunkList(session.getTotalChunks())
                : uploadedChunkIndexes(uploadId);
        double progress = session.getTotalChunks() == null || session.getTotalChunks() == 0
                ? 0.0D
                : (double) uploadedChunks.size() / session.getTotalChunks();
        return new TestcaseUploadStatusResponse(session.getId(), session.getStatus(), uploadedChunks,
                session.getTotalChunks(), progress, session.getPackageId(), session.getErrorMessage());
    }

    public List<TestcasePackageResponse> list(Long problemId) {
        requireProblem(problemId);
        return packageMapper.selectList(new LambdaQueryWrapper<TestcasePackageEntity>()
                        .eq(TestcasePackageEntity::getProblemId, problemId)
                        .orderByDesc(TestcasePackageEntity::getActive)
                        .orderByDesc(TestcasePackageEntity::getCreatedAt)
                        .orderByDesc(TestcasePackageEntity::getId))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TestcasePackageResponse activate(Long problemId, Long packageId) {
        requireProblem(problemId);
        TestcasePackageEntity testcasePackage = requirePackage(problemId, packageId);
        if (testcasePackage.getStatus() != TestcasePackageStatus.READY) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Only READY testcase packages can be activated");
        }
        Instant now = Instant.now();
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getProblemId, problemId)
                .eq(TestcasePackageEntity::getActive, true)
                .set(TestcasePackageEntity::getActive, false)
                .set(TestcasePackageEntity::getUpdatedAt, now));
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, packageId)
                .eq(TestcasePackageEntity::getProblemId, problemId)
                .set(TestcasePackageEntity::getActive, true)
                .set(TestcasePackageEntity::getActivatedAt, now)
                .set(TestcasePackageEntity::getUpdatedAt, now));
        return toResponse(requirePackage(packageId));
    }

    private InitSpec validateInit(TestcaseUploadInitRequest request) {
        String fileName = normalizeFileName(request.fileName());
        String sha256 = normalizeSha(request.sha256(), "sha256");
        if (request.fileSizeBytes() <= 0 || request.fileSizeBytes() > properties.getMaxPackageBytes()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Testcase package file size exceeds limit");
        }
        if (request.chunkSizeBytes() <= 0 || request.chunkSizeBytes() > properties.getChunkSizeBytes()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Chunk size exceeds configured limit");
        }
        long expectedChunks = (request.fileSizeBytes() + request.chunkSizeBytes() - 1) / request.chunkSizeBytes();
        if (expectedChunks <= 0 || expectedChunks > MAX_TOTAL_CHUNKS || expectedChunks != request.totalChunks()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Total chunks does not match file size and chunk size");
        }
        return new InitSpec(fileName, request.fileSizeBytes(), sha256, request.chunkSizeBytes(), request.totalChunks());
    }

    private String normalizeFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (!StringUtils.hasText(normalized) || normalized.length() > 255) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Testcase package file name is required");
        }
        if (normalized.contains("/") || normalized.contains("\\") || !normalized.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Only .zip testcase packages are supported");
        }
        return normalized;
    }

    private String normalizeSha(String sha256, String fieldName) {
        String normalized = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, fieldName + " must be a 64-character SHA-256 hex string");
        }
        return normalized;
    }

    private String normalizeOptionalSha(String sha256, String fieldName) {
        if (!StringUtils.hasText(sha256)) {
            return null;
        }
        return normalizeSha(sha256, fieldName);
    }

    private TestcasePackageEntity createPackage(Long problemId, InitSpec spec) {
        Instant now = Instant.now();
        TestcasePackageEntity testcasePackage = new TestcasePackageEntity();
        testcasePackage.setProblemId(problemId);
        testcasePackage.setVersion(PENDING_VERSION);
        testcasePackage.setFileName(spec.fileName());
        testcasePackage.setFileSizeBytes(spec.fileSizeBytes());
        testcasePackage.setSha256(spec.sha256());
        testcasePackage.setStatus(TestcasePackageStatus.UPLOADING);
        testcasePackage.setActive(false);
        testcasePackage.setStorageProvider(TestcaseStorageService.LOCAL_PROVIDER);
        testcasePackage.setStorageKey(storageService.packageStorageKey(problemId, spec.sha256()));
        testcasePackage.setCaseCount(0);
        testcasePackage.setSampleCount(0);
        testcasePackage.setCreatedBy(SecuritySupport.currentUserId());
        testcasePackage.setCreatedAt(now);
        testcasePackage.setUpdatedAt(now);
        packageMapper.insert(testcasePackage);
        return testcasePackage;
    }

    private TestcasePackageEntity resetFailedPackage(TestcasePackageEntity existing, InitSpec spec) {
        Instant now = Instant.now();
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, existing.getId())
                .set(TestcasePackageEntity::getVersion, PENDING_VERSION)
                .set(TestcasePackageEntity::getFileName, spec.fileName())
                .set(TestcasePackageEntity::getFileSizeBytes, spec.fileSizeBytes())
                .set(TestcasePackageEntity::getStatus, TestcasePackageStatus.UPLOADING)
                .set(TestcasePackageEntity::getActive, false)
                .set(TestcasePackageEntity::getStorageProvider, TestcaseStorageService.LOCAL_PROVIDER)
                .set(TestcasePackageEntity::getStorageKey, storageService.packageStorageKey(existing.getProblemId(), spec.sha256()))
                .set(TestcasePackageEntity::getCaseCount, 0)
                .set(TestcasePackageEntity::getSampleCount, 0)
                .set(TestcasePackageEntity::getManifestJson, null)
                .set(TestcasePackageEntity::getActivatedAt, null)
                .set(TestcasePackageEntity::getErrorMessage, null)
                .set(TestcasePackageEntity::getUpdatedAt, now));
        caseMapper.delete(new LambdaQueryWrapper<TestcasePackageCaseEntity>()
                .eq(TestcasePackageCaseEntity::getPackageId, existing.getId()));
        return requirePackage(existing.getId());
    }

    private TestcaseUploadSessionEntity createSession(Long problemId, InitSpec spec, Long packageId,
                                                       TestcasePackageStatus status, int uploadedChunks,
                                                       String tempDir) {
        Instant now = Instant.now();
        TestcaseUploadSessionEntity session = new TestcaseUploadSessionEntity();
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        session.setId(sessionId);
        session.setProblemId(problemId);
        session.setFileName(spec.fileName());
        session.setFileSizeBytes(spec.fileSizeBytes());
        session.setSha256(spec.sha256());
        session.setChunkSizeBytes(spec.chunkSizeBytes());
        session.setTotalChunks(spec.totalChunks());
        session.setUploadedChunks(uploadedChunks);
        session.setStatus(status);
        session.setTempDir(status == TestcasePackageStatus.UPLOADING ? "tmp/" + sessionId : tempDir);
        session.setPackageId(packageId);
        session.setCreatedBy(SecuritySupport.currentUserId());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plus(24, ChronoUnit.HOURS));
        sessionMapper.insert(session);
        return session;
    }

    private TestcaseUploadInitResponse toInitResponse(TestcaseUploadSessionEntity session, String message) {
        List<Integer> uploadedChunks = session.getStatus() == TestcasePackageStatus.READY
                ? fullChunkList(session.getTotalChunks())
                : uploadedChunkIndexes(session.getId());
        return new TestcaseUploadInitResponse(session.getId(), session.getStatus(), session.getPackageId(),
                uploadedChunks, session.getChunkSizeBytes(), session.getTotalChunks(), session.getExpiresAt(), message);
    }

    private void validateChunkSize(TestcaseUploadSessionEntity session, int index, long actualSize) {
        long expectedSize = expectedChunkSize(session, index);
        if (actualSize != expectedSize) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Chunk size does not match expected chunk range");
        }
    }

    private long expectedChunkSize(TestcaseUploadSessionEntity session, int index) {
        if (index < session.getTotalChunks() - 1) {
            return session.getChunkSizeBytes();
        }
        return session.getFileSizeBytes() - (long) session.getChunkSizeBytes() * (session.getTotalChunks() - 1);
    }

    private void verifyCompleteChunks(TestcaseUploadSessionEntity session, List<TestcaseUploadChunkEntity> chunks) {
        if (chunks.size() != session.getTotalChunks()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Not all testcase chunks have been uploaded");
        }
        Set<Integer> indexes = new HashSet<>();
        long uploadedBytes = 0L;
        for (TestcaseUploadChunkEntity chunk : chunks) {
            indexes.add(chunk.getChunkIndex());
            uploadedBytes += chunk.getChunkSizeBytes();
            if (!storageService.exists(chunk.getStoragePath())) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Uploaded testcase chunk is missing on disk: " + chunk.getChunkIndex());
            }
        }
        for (int i = 0; i < session.getTotalChunks(); i++) {
            if (!indexes.contains(i)) {
                throw new DomainException(ErrorCode.BAD_REQUEST, "Missing testcase upload chunk: " + i);
            }
        }
        if (uploadedBytes != session.getFileSizeBytes()) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Uploaded testcase chunk sizes do not match file size");
        }
    }

    private void markProcessing(TestcaseUploadSessionEntity session) {
        Instant now = Instant.now();
        sessionMapper.update(new TestcaseUploadSessionEntity(), new LambdaUpdateWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getId, session.getId())
                .set(TestcaseUploadSessionEntity::getStatus, TestcasePackageStatus.PROCESSING)
                .set(TestcaseUploadSessionEntity::getUpdatedAt, now)
                .set(TestcaseUploadSessionEntity::getErrorMessage, null));
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, session.getPackageId())
                .set(TestcasePackageEntity::getStatus, TestcasePackageStatus.PROCESSING)
                .set(TestcasePackageEntity::getUpdatedAt, now)
                .set(TestcasePackageEntity::getErrorMessage, null));
        session.setStatus(TestcasePackageStatus.PROCESSING);
    }

    private void saveReadyPackage(TestcaseUploadSessionEntity session, TestcaseStorageService.MergeResult merged,
                                  TestcasePackageValidator.ValidatedPackage validated) {
        Instant now = Instant.now();
        caseMapper.delete(new LambdaQueryWrapper<TestcasePackageCaseEntity>()
                .eq(TestcasePackageCaseEntity::getPackageId, session.getPackageId()));
        int sampleCount = 0;
        for (TestcasePackageValidator.ValidatedCase validatedCase : validated.cases()) {
            TestcasePackageCaseEntity entity = new TestcasePackageCaseEntity();
            entity.setPackageId(session.getPackageId());
            entity.setName(validatedCase.name());
            entity.setInputPath(validatedCase.inputPath());
            entity.setOutputPath(validatedCase.outputPath());
            entity.setSample(validatedCase.sample());
            entity.setScore(validatedCase.score());
            entity.setInputSizeBytes(validatedCase.inputSizeBytes());
            entity.setOutputSizeBytes(validatedCase.outputSizeBytes());
            entity.setSortOrder(validatedCase.sortOrder());
            entity.setCreatedAt(now);
            caseMapper.insert(entity);
            if (validatedCase.sample()) {
                sampleCount++;
            }
        }
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, session.getPackageId())
                .set(TestcasePackageEntity::getVersion, validated.version())
                .set(TestcasePackageEntity::getFileSizeBytes, merged.sizeBytes())
                .set(TestcasePackageEntity::getSha256, merged.sha256())
                .set(TestcasePackageEntity::getStatus, TestcasePackageStatus.READY)
                .set(TestcasePackageEntity::getStorageProvider, TestcaseStorageService.LOCAL_PROVIDER)
                .set(TestcasePackageEntity::getStorageKey, merged.storageKey())
                .set(TestcasePackageEntity::getCaseCount, validated.cases().size())
                .set(TestcasePackageEntity::getSampleCount, sampleCount)
                .set(TestcasePackageEntity::getManifestJson, validated.manifestJson())
                .set(TestcasePackageEntity::getUpdatedAt, now)
                .set(TestcasePackageEntity::getErrorMessage, null));
        sessionMapper.update(new TestcaseUploadSessionEntity(), new LambdaUpdateWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getId, session.getId())
                .set(TestcaseUploadSessionEntity::getStatus, TestcasePackageStatus.READY)
                .set(TestcaseUploadSessionEntity::getUploadedChunks, session.getTotalChunks())
                .set(TestcaseUploadSessionEntity::getPackageId, session.getPackageId())
                .set(TestcaseUploadSessionEntity::getUpdatedAt, now)
                .set(TestcaseUploadSessionEntity::getErrorMessage, null));
    }

    private void markFailed(TestcaseUploadSessionEntity session, String message) {
        String error = truncate(message);
        Instant now = Instant.now();
        sessionMapper.update(new TestcaseUploadSessionEntity(), new LambdaUpdateWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getId, session.getId())
                .set(TestcaseUploadSessionEntity::getStatus, TestcasePackageStatus.FAILED)
                .set(TestcaseUploadSessionEntity::getUpdatedAt, now)
                .set(TestcaseUploadSessionEntity::getErrorMessage, error));
        packageMapper.update(new TestcasePackageEntity(), new LambdaUpdateWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, session.getPackageId())
                .set(TestcasePackageEntity::getStatus, TestcasePackageStatus.FAILED)
                .set(TestcasePackageEntity::getActive, false)
                .set(TestcasePackageEntity::getUpdatedAt, now)
                .set(TestcasePackageEntity::getErrorMessage, error));
    }

    private void refreshUploadedChunkCount(TestcaseUploadSessionEntity session) {
        int count = (int) chunkMapper.selectCount(new LambdaQueryWrapper<TestcaseUploadChunkEntity>()
                .eq(TestcaseUploadChunkEntity::getUploadId, session.getId())).longValue();
        sessionMapper.update(new TestcaseUploadSessionEntity(), new LambdaUpdateWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getId, session.getId())
                .set(TestcaseUploadSessionEntity::getUploadedChunks, count)
                .set(TestcaseUploadSessionEntity::getUpdatedAt, Instant.now()));
        session.setUploadedChunks(count);
    }

    private TestcasePackageResponse toResponse(TestcasePackageEntity testcasePackage) {
        List<TestcasePackageCaseResponse> cases = caseMapper.selectList(new LambdaQueryWrapper<TestcasePackageCaseEntity>()
                        .eq(TestcasePackageCaseEntity::getPackageId, testcasePackage.getId())
                        .orderByAsc(TestcasePackageCaseEntity::getSortOrder)
                        .orderByAsc(TestcasePackageCaseEntity::getId))
                .stream()
                .map(this::toCaseResponse)
                .toList();
        return new TestcasePackageResponse(testcasePackage.getId(), testcasePackage.getProblemId(),
                testcasePackage.getVersion(), testcasePackage.getFileName(), testcasePackage.getFileSizeBytes(),
                testcasePackage.getSha256(), testcasePackage.getStatus(), Boolean.TRUE.equals(testcasePackage.getActive()),
                testcasePackage.getCaseCount(), testcasePackage.getSampleCount(), testcasePackage.getStorageProvider(),
                testcasePackage.getCreatedAt(), testcasePackage.getActivatedAt(), testcasePackage.getErrorMessage(), cases);
    }

    private TestcasePackageCaseResponse toCaseResponse(TestcasePackageCaseEntity entity) {
        return new TestcasePackageCaseResponse(entity.getId(), entity.getName(), entity.getInputPath(),
                entity.getOutputPath(), Boolean.TRUE.equals(entity.getSample()), entity.getScore(),
                entity.getInputSizeBytes(), entity.getOutputSizeBytes(), entity.getSortOrder());
    }

    private List<Integer> uploadedChunkIndexes(String uploadId) {
        return chunks(uploadId).stream().map(TestcaseUploadChunkEntity::getChunkIndex).toList();
    }

    private List<Integer> fullChunkList(int totalChunks) {
        return IntStream.range(0, totalChunks).boxed().toList();
    }

    private List<TestcaseUploadChunkEntity> chunks(String uploadId) {
        return chunkMapper.selectList(new LambdaQueryWrapper<TestcaseUploadChunkEntity>()
                .eq(TestcaseUploadChunkEntity::getUploadId, uploadId)
                .orderByAsc(TestcaseUploadChunkEntity::getChunkIndex));
    }

    private TestcaseUploadChunkEntity findChunk(String uploadId, int index) {
        return chunkMapper.selectOne(new LambdaQueryWrapper<TestcaseUploadChunkEntity>()
                .eq(TestcaseUploadChunkEntity::getUploadId, uploadId)
                .eq(TestcaseUploadChunkEntity::getChunkIndex, index));
    }

    private TestcaseUploadSessionEntity latestSession(Long problemId, String sha256, Long packageId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getProblemId, problemId)
                .eq(TestcaseUploadSessionEntity::getSha256, sha256)
                .eq(TestcaseUploadSessionEntity::getPackageId, packageId)
                .orderByDesc(TestcaseUploadSessionEntity::getCreatedAt)
                .last("LIMIT 1"));
    }

    private TestcasePackageEntity findPackageBySha(Long problemId, String sha256) {
        return packageMapper.selectOne(new LambdaQueryWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getProblemId, problemId)
                .eq(TestcasePackageEntity::getSha256, sha256));
    }

    private TestcasePackageEntity requirePackage(Long packageId) {
        TestcasePackageEntity testcasePackage = packageMapper.selectById(packageId);
        if (testcasePackage == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Testcase package not found");
        }
        return testcasePackage;
    }

    private TestcasePackageEntity requirePackage(Long problemId, Long packageId) {
        TestcasePackageEntity testcasePackage = packageMapper.selectOne(new LambdaQueryWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getId, packageId)
                .eq(TestcasePackageEntity::getProblemId, problemId));
        if (testcasePackage == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Testcase package not found");
        }
        return testcasePackage;
    }

    private TestcaseUploadSessionEntity requireUploadSession(Long problemId, String uploadId) {
        TestcaseUploadSessionEntity session = sessionMapper.selectOne(new LambdaQueryWrapper<TestcaseUploadSessionEntity>()
                .eq(TestcaseUploadSessionEntity::getId, uploadId)
                .eq(TestcaseUploadSessionEntity::getProblemId, problemId));
        if (session == null) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Testcase upload session not found");
        }
        return session;
    }

    private void requireProblem(Long problemId) {
        if (!problemCatalog.existsActive(problemId)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Problem not found");
        }
    }

    private void assertUploading(TestcaseUploadSessionEntity session) {
        if (session.getStatus() == TestcasePackageStatus.FAILED) {
            throw new DomainException(ErrorCode.BAD_REQUEST, "Testcase upload has failed: " + session.getErrorMessage());
        }
        if (session.getStatus() != TestcasePackageStatus.UPLOADING) {
            throw new DomainException(ErrorCode.CONFLICT, "Testcase upload is not accepting chunks");
        }
        if (session.getExpiresAt() != null && Instant.now().isAfter(session.getExpiresAt())) {
            markFailed(session, "Testcase upload session expired");
            throw new DomainException(ErrorCode.BAD_REQUEST, "Testcase upload session expired");
        }
    }

    private String userMessage(Throwable ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Failed to process testcase package";
    }

    private String truncate(String value) {
        if (value == null || value.length() <= ERROR_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_LIMIT);
    }

    private record InitSpec(String fileName, long fileSizeBytes, String sha256, int chunkSizeBytes, int totalChunks) {
    }
}
