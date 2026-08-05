package com.foliolens.backend.disclosure.infrastructure.filesystem;

import com.foliolens.backend.disclosure.domain.Disclosure;

import java.nio.file.Path;
import java.util.List;

public interface DisclosureDocumentScanner {

    /**
     * 공시 폴더 안의 실제 원문 파일을 조사한다.
     */
    List<ScannedDisclosureFile> scan(
            Disclosure disclosure,
            Path disclosureDirectory
    );
}
