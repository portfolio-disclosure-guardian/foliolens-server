package com.foliolens.backend.disclosure.infrastructure.filesystem;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * 논리적인 manifest 경로 (NFC)를 실제 파일시스템 경로(NFD)로 변환하는 것
 */
@Component
public class DisclosurePathResolver {

    @Getter
    private final Path datasetRoot;

    /**
     * 같은 디렉터리를 공시마다 반복해서 탐색하지 않도록
     * 디렉터리별 하위 폴더 목록을 캐시한다.
     *
     * key: 실제 물리 디렉터리 경로
     * value: NFC 폴더명 → 실제 물리 폴더 경로
     */
    private final ConcurrentMap<Path, Map<String, Path>>
            childDirectoryCache = new ConcurrentHashMap<>();

    public DisclosurePathResolver(
            @Value("${foliolens.dataset.root}")
            String configuredDatasetRoot
    ) {
        if (
                configuredDatasetRoot == null
                        || configuredDatasetRoot.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "foliolens.dataset.root 설정은 필수입니다."
            );
        }

        this.datasetRoot = Path.of(configuredDatasetRoot.trim())
                .toAbsolutePath()
                .normalize();
    }

    /**
     * manifest_path(manifest.jsonl과 disclosures 테이블에 기록된 폴더 주소)를
     * 실제 파일시스템의 공시 폴더(Windows 디스크에 실제로 존재하는 폴더)로 변환한다.
     *
     * manifest에는 NFC 경로가 들어 있지만 실제 한글 폴더는
     * NFD로 저장될 수 있으므로 각 경로 segment를 NFC로
     * 정규화해서 비교한다.
     */
    public Path resolveDirectory(String manifestPath) {
        validateDatasetRoot();

        String validatedPath = validateRelativePath(
                manifestPath,
                "manifestPath"
        );

        Path currentDirectory = datasetRoot;

        for (String segment : validatedPath.split("/")) {
            String normalizedSegment = normalizeName(segment);

            Map<String, Path> childDirectories =
                    childDirectoryCache.computeIfAbsent(
                            currentDirectory,
                            this::scanChildDirectories
                    );

            Path matchedDirectory = childDirectories.get(normalizedSegment);

            if (matchedDirectory == null) {
                throw new IllegalStateException(
                        "manifest 경로에 해당하는 실제 폴더를 찾을 수 없습니다."
                                + " manifestPath=" + validatedPath
                                + ", missingSegment=" + segment
                                + ", currentDirectory=" + currentDirectory
                );
            }

            currentDirectory = matchedDirectory;
        }

        validateInsideDatasetRoot(currentDirectory);

        if (
                !Files.isDirectory(
                        currentDirectory,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IllegalStateException(
                    "해석된 경로가 디렉터리가 아닙니다: "
                            + currentDirectory
            );
        }

        return currentDirectory;
    }

    /**
     * 실제 물리 경로를 데이터셋 루트 기준 상대경로로 변환한다.
     *
     * Windows와 Linux 모두 DB에는 "/" 구분자를 사용한다.
     */
    public String toDatasetRelativePath(Path physicalPath) {
        if (physicalPath == null) {
            throw new IllegalArgumentException(
                    "physicalPath는 필수입니다."
            );
        }

        validateDatasetRoot();

        Path absolutePath = physicalPath
                .toAbsolutePath()
                .normalize();

        validateInsideDatasetRoot(absolutePath);

        if (
                !Files.exists(
                        absolutePath,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IllegalStateException(
                    "실제 경로가 존재하지 않습니다: "
                            + absolutePath
            );
        }

        if (Files.isSymbolicLink(absolutePath)) {
            throw new IllegalStateException(
                    "심볼릭 링크는 원문 경로로 사용할 수 없습니다: "
                            + absolutePath
            );
        }

        return datasetRoot
                .relativize(absolutePath)
                .toString()
                .replace('\\', '/');
    }

    /**
     * DB의 normalized_relative_path에 저장할 NFC 경로를 만든다.
     */
    public String normalizeRelativePath(String relativePath) {
        String validatedPath = validateRelativePath(
                relativePath,
                "relativePath"
        );

        return Normalizer.normalize(
                validatedPath,
                Normalizer.Form.NFC
        );
    }

    /**
     * 특정 디렉터리 바로 아래의 실제 하위 폴더를 읽는다.
     *
     * 실제 폴더명은 그대로 Path에 보존하고,
     * Map의 비교 key만 NFC로 정규화한다.
     */
    private Map<String, Path> scanChildDirectories(
            Path parentDirectory
    ) {
        if (
                !Files.isDirectory(
                        parentDirectory,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IllegalStateException(
                    "하위 폴더를 탐색할 수 없는 경로입니다: "
                            + parentDirectory
            );
        }

        Map<String, Path> directoriesByNormalizedName =
                new HashMap<>();

        try (
                Stream<Path> children =
                        Files.list(parentDirectory)
        ) {
            children
                    .filter(path ->
                            Files.isDirectory(
                                    path,
                                    LinkOption.NOFOLLOW_LINKS
                            )
                    )
                    .filter(path ->
                            !Files.isSymbolicLink(path)
                    )
                    .forEach(path -> {
                        String actualName = path
                                .getFileName()
                                .toString();

                        String normalizedName =
                                normalizeName(actualName);

                        Path previous =
                                directoriesByNormalizedName.putIfAbsent(
                                        normalizedName,
                                        path
                                );

                        if (
                                previous != null
                                        && !previous.equals(path)
                        ) {
                            throw new IllegalStateException(
                                    "NFC 정규화 후 이름이 충돌하는 폴더가 있습니다."
                                            + " parent=" + parentDirectory
                                            + ", first=" + previous
                                            + ", second=" + path
                            );
                        }
                    });
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "하위 폴더 목록을 읽지 못했습니다: "
                            + parentDirectory,
                    exception
            );
        }

        return Map.copyOf(directoriesByNormalizedName);
    }

    private void validateDatasetRoot() {
        if (
                !Files.exists(
                        datasetRoot,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IllegalStateException(
                    "데이터셋 루트가 존재하지 않습니다: "
                            + datasetRoot
            );
        }

        if (Files.isSymbolicLink(datasetRoot)) {
            throw new IllegalStateException(
                    "데이터셋 루트는 심볼릭 링크일 수 없습니다: "
                            + datasetRoot
            );
        }

        if (
                !Files.isDirectory(
                        datasetRoot,
                        LinkOption.NOFOLLOW_LINKS
                )
        ) {
            throw new IllegalStateException(
                    "데이터셋 루트가 디렉터리가 아닙니다: "
                            + datasetRoot
            );
        }

        if (!Files.isReadable(datasetRoot)) {
            throw new IllegalStateException(
                    "데이터셋 루트를 읽을 수 없습니다: "
                            + datasetRoot
            );
        }
    }

    private void validateInsideDatasetRoot(Path path) {
        Path normalizedPath = path
                .toAbsolutePath()
                .normalize();

        if (!normalizedPath.startsWith(datasetRoot)) {
            throw new IllegalArgumentException(
                    "데이터셋 루트 밖의 경로에는 접근할 수 없습니다."
                            + " datasetRoot=" + datasetRoot
                            + ", path=" + normalizedPath
            );
        }
    }

    private static String validateRelativePath(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "는 필수입니다."
            );
        }

        String path = value.trim();

        if (
                path.startsWith("/")
                        || path.startsWith("\\")
                        || path.matches("^[A-Za-z]:.*")
                        || path.contains("\\")
                        || path.indexOf('\0') >= 0
        ) {
            throw new IllegalArgumentException(
                    fieldName
                            + "는 / 구분자를 사용하는 안전한 상대경로여야 합니다: "
                            + path
            );
        }

        String[] segments = path.split("/", -1);

        for (String segment : segments) {
            if (
                    segment.isBlank()
                            || ".".equals(segment)
                            || "..".equals(segment)
            ) {
                throw new IllegalArgumentException(
                        fieldName
                                + "에 허용되지 않는 경로 segment가 있습니다: "
                                + path
                );
            }
        }

        return path;
    }

    private static String normalizeName(String value) {
        return Normalizer.normalize(
                value,
                Normalizer.Form.NFC
        );
    }
}
