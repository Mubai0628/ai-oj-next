package com.aioj.next.judge.domain;

import com.aioj.next.contract.problem.TestcasePackageStatus;
import com.aioj.next.judge.config.JudgeTestcaseProperties;
import com.aioj.next.judge.config.JudgeWorkerProperties;
import com.aioj.next.judge.persistence.entity.TestcasePackageCaseEntity;
import com.aioj.next.judge.persistence.entity.TestcasePackageEntity;
import com.aioj.next.judge.persistence.mapper.TestcasePackageCaseMapper;
import com.aioj.next.judge.persistence.mapper.TestcasePackageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class TestcasePackageCache {
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String LOCAL_PROVIDER = "LOCAL";
    private static final String READY_MARKER = ".ready";

    private final TestcasePackageMapper packageMapper;
    private final TestcasePackageCaseMapper caseMapper;
    private final Path storageRoot;
    private final Path cacheRoot;

    public TestcasePackageCache(TestcasePackageMapper packageMapper,
                                TestcasePackageCaseMapper caseMapper,
                                JudgeTestcaseProperties testcaseProperties,
                                JudgeWorkerProperties workerProperties) {
        this.packageMapper = packageMapper;
        this.caseMapper = caseMapper;
        this.storageRoot = Path.of(testcaseProperties.getStorageRoot()).toAbsolutePath().normalize();
        this.cacheRoot = Path.of(workerProperties.getCacheRoot()).toAbsolutePath().normalize();
    }

    public synchronized Optional<PreparedTestcasePackage> prepareActivePackage(Long problemId) {
        TestcasePackageEntity testcasePackage = activePackage(problemId);
        if (testcasePackage == null) {
            return Optional.empty();
        }
        if (!LOCAL_PROVIDER.equalsIgnoreCase(testcasePackage.getStorageProvider())) {
            throw new TestcasePackageUnavailableException("unsupported testcase storage provider: "
                    + testcasePackage.getStorageProvider());
        }
        Path zipPath = resolveStorageKey(testcasePackage.getStorageKey());
        verifyZipFile(testcasePackage, zipPath);
        Path cachePath = cachePath(testcasePackage);
        if (!Files.isRegularFile(cachePath.resolve(READY_MARKER))) {
            extractToCache(zipPath, cachePath, testcasePackage);
        }
        return Optional.of(new PreparedTestcasePackage(testcasePackage.getId(), testcasePackage.getProblemId(),
                testcasePackage.getSha256(), cachePath, preparedCases(testcasePackage, cachePath)));
    }

    private TestcasePackageEntity activePackage(Long problemId) {
        return packageMapper.selectOne(new LambdaQueryWrapper<TestcasePackageEntity>()
                .eq(TestcasePackageEntity::getProblemId, problemId)
                .eq(TestcasePackageEntity::getActive, true)
                .eq(TestcasePackageEntity::getStatus, TestcasePackageStatus.READY)
                .orderByDesc(TestcasePackageEntity::getActivatedAt)
                .orderByDesc(TestcasePackageEntity::getId)
                .last("LIMIT 1"));
    }

    private void verifyZipFile(TestcasePackageEntity testcasePackage, Path zipPath) {
        try {
            if (!Files.isRegularFile(zipPath)) {
                throw new TestcasePackageUnavailableException("testcase package file is missing");
            }
            long size = Files.size(zipPath);
            if (testcasePackage.getFileSizeBytes() != null && size != testcasePackage.getFileSizeBytes()) {
                throw new TestcasePackageUnavailableException("testcase package file size does not match metadata");
            }
            String actualSha = sha256(zipPath);
            if (!actualSha.equalsIgnoreCase(testcasePackage.getSha256())) {
                throw new TestcasePackageUnavailableException("testcase package SHA-256 does not match metadata");
            }
        } catch (IOException ex) {
            throw new TestcasePackageUnavailableException("failed to read testcase package file", ex);
        }
    }

    private void extractToCache(Path zipPath, Path cachePath, TestcasePackageEntity testcasePackage) {
        Path tempPath = cacheRoot.resolve(cachePath.getFileName() + ".tmp-" + UUID.randomUUID()).normalize();
        ensureUnder(cacheRoot, tempPath);
        try {
            Files.createDirectories(cacheRoot);
            deleteRecursively(tempPath);
            deleteRecursively(cachePath);
            Files.createDirectories(tempPath);
            extractZip(zipPath, tempPath);
            Files.writeString(tempPath.resolve(READY_MARKER),
                    "packageId=" + testcasePackage.getId() + System.lineSeparator()
                            + "sha256=" + testcasePackage.getSha256() + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            moveDirectoryIntoPlace(tempPath, cachePath);
        } catch (IOException ex) {
            deleteRecursively(tempPath);
            throw new TestcasePackageUnavailableException("failed to cache testcase package", ex);
        } catch (RuntimeException ex) {
            deleteRecursively(tempPath);
            throw ex;
        }
    }

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(zipPath, StandardOpenOption.READ))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                String safePath = normalizeZipPath(entry.getName());
                Path target = targetDir.resolve(safePath).normalize();
                ensureUnder(targetDir, target);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }
    }

    private List<PreparedTestcaseCase> preparedCases(TestcasePackageEntity testcasePackage, Path cachePath) {
        return caseMapper.selectList(new LambdaQueryWrapper<TestcasePackageCaseEntity>()
                        .eq(TestcasePackageCaseEntity::getPackageId, testcasePackage.getId())
                        .orderByAsc(TestcasePackageCaseEntity::getSortOrder)
                        .orderByAsc(TestcasePackageCaseEntity::getId))
                .stream()
                .map(entity -> toPreparedCase(cachePath, entity))
                .toList();
    }

    private PreparedTestcaseCase toPreparedCase(Path cachePath, TestcasePackageCaseEntity entity) {
        Path input = cachePath.resolve(entity.getInputPath()).normalize();
        Path output = cachePath.resolve(entity.getOutputPath()).normalize();
        ensureUnder(cachePath, input);
        ensureUnder(cachePath, output);
        if (!Files.isRegularFile(input) || !Files.isRegularFile(output)) {
            throw new TestcasePackageUnavailableException("cached testcase case files are missing");
        }
        return new PreparedTestcaseCase(entity.getId(), entity.getName(), input, output,
                Boolean.TRUE.equals(entity.getSample()), entity.getScore(), entity.getSortOrder());
    }

    private Path resolveStorageKey(String storageKey) {
        Path path = storageRoot.resolve(storageKey).toAbsolutePath().normalize();
        ensureUnder(storageRoot, path);
        return path;
    }

    private Path cachePath(TestcasePackageEntity testcasePackage) {
        String sha8 = testcasePackage.getSha256().substring(0, 8).toLowerCase(Locale.ROOT);
        Path path = cacheRoot.resolve(testcasePackage.getId() + "-" + sha8).toAbsolutePath().normalize();
        ensureUnder(cacheRoot, path);
        return path;
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new TestcasePackageUnavailableException("SHA-256 is not available", ex);
        }
    }

    private String normalizeZipPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank() || rawPath.contains("\\")
                || rawPath.startsWith("/") || rawPath.matches("^[A-Za-z]:.*")) {
            throw new TestcasePackageUnavailableException("unsafe testcase zip path: " + rawPath);
        }
        String path = rawPath;
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment) || segment.contains(":")) {
                throw new TestcasePackageUnavailableException("unsafe testcase zip path: " + rawPath);
            }
        }
        return String.join("/", segments);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        ensureUnder(cacheRoot, path.toAbsolutePath().normalize());
        try (var paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ignored) {
                    // Best-effort cleanup only.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private void moveDirectoryIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void ensureUnder(Path root, Path path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw new TestcasePackageUnavailableException("unsafe testcase path");
        }
    }
}
