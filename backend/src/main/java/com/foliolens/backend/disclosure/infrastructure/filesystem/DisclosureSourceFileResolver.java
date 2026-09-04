package com.foliolens.backend.disclosure.infrastructure.filesystem;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 등록된 문서의 실제 경로·크기·해시를 검증한다. 원본 파일은 수정하지 않는다. */
@Component
public class DisclosureSourceFileResolver {
    private final DisclosurePathResolver paths;

    public DisclosureSourceFileResolver(DisclosurePathResolver paths) {
        this.paths = paths;
    }

    public Path resolve(DisclosureDocument document) {
        Path directory = paths.resolveDirectory(document.getDisclosure().getManifestPath());
        Path file = directory.resolve(document.getFileName()).toAbsolutePath().normalize();
        if (!directory.equals(file.getParent()) || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("허용되지 않거나 존재하지 않는 원문 파일: " + file);
        }
        String normalized = paths.normalizeRelativePath(paths.toDatasetRelativePath(file));
        if (!normalized.equals(document.getNormalizedRelativePath())) {
            throw new IllegalArgumentException("등록된 원문 경로와 실제 경로가 다릅니다.");
        }
        try {
            if (Files.size(file) != document.getFileSizeBytes()) {
                throw new IllegalArgumentException("등록된 원문 파일 크기가 다릅니다.");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(document.getSha256())) {
                throw new IllegalArgumentException("등록된 원문 SHA-256과 실제 파일이 다릅니다.");
            }
            return file;
        } catch (IOException exception) {
            throw new UncheckedIOException("원문 검증 실패", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
