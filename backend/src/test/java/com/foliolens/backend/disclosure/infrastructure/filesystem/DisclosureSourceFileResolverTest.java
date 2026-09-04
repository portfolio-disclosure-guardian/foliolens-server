package com.foliolens.backend.disclosure.infrastructure.filesystem;

import com.foliolens.backend.disclosure.domain.Disclosure;
import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisclosureSourceFileResolverTest {
    @Test void validatesNfdPathSizeAndHash(@TempDir Path root) throws Exception {
        String physicalName = Normalizer.normalize("기업", Normalizer.Form.NFD);
        Path directory = Files.createDirectories(root.resolve("raw/exchange").resolve(physicalName).resolve("123"));
        Path file = directory.resolve("sample.xml");
        Files.writeString(file, "<html>test</html>");
        var document = mock(DisclosureDocument.class);
        var disclosure = mock(Disclosure.class);
        when(document.getDisclosure()).thenReturn(disclosure);
        when(disclosure.getManifestPath()).thenReturn("raw/exchange/기업/123");
        when(document.getFileName()).thenReturn("sample.xml");
        when(document.getNormalizedRelativePath()).thenReturn("raw/exchange/기업/123/sample.xml");
        when(document.getFileSizeBytes()).thenReturn(Files.size(file));
        when(document.getSha256()).thenReturn(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        var resolver = new DisclosureSourceFileResolver(new DisclosurePathResolver(root.toString()));
        assertThat(resolver.resolve(document)).isEqualTo(file.toAbsolutePath().normalize());
        Files.writeString(file, "<html>FAIL</html>"); // 같은 크기라도 해시가 다르면 거절한다.
        assertThatThrownBy(() -> resolver.resolve(document)).hasMessageContaining("SHA-256");
        when(document.getFileName()).thenReturn("../sample.xml");
        assertThatThrownBy(() -> resolver.resolve(document)).hasMessageContaining("허용되지");
    }
}
