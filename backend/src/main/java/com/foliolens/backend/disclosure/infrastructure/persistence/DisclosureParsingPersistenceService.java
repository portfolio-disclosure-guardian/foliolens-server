package com.foliolens.backend.disclosure.infrastructure.persistence;

import com.foliolens.backend.disclosure.domain.DisclosureDocument;
import com.foliolens.backend.disclosure.infrastructure.parsing.ParsedDisclosureDocument;
import com.foliolens.backend.disclosure.repository.DisclosureContentBlockRepository;
import com.foliolens.backend.disclosure.repository.DisclosureChunkRepository;
import com.foliolens.backend.disclosure.repository.DisclosureDocumentRepository;
import com.foliolens.backend.disclosure.repository.DisclosureSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class DisclosureParsingPersistenceService {

    private final DisclosureDocumentRepository documentRepository;
    private final DisclosureSectionRepository sectionRepository;
    private final DisclosureContentBlockRepository blockRepository;
    private final ParsedDisclosureEntityMapper entityMapper;
    private final DisclosureChunkRepository chunkRepository;

    public DisclosureParsingPersistenceService(
            DisclosureDocumentRepository documentRepository,
            DisclosureSectionRepository sectionRepository,
            DisclosureContentBlockRepository blockRepository,
            ParsedDisclosureEntityMapper entityMapper,
            DisclosureChunkRepository chunkRepository
    ) {
        this.documentRepository = documentRepository;
        this.sectionRepository = sectionRepository;
        this.blockRepository = blockRepository;
        this.entityMapper = entityMapper;
        this.chunkRepository = chunkRepository;
    }

    /**
     * 문서 하나의 기존 파싱 결과를 새 결과로 교체
     *
     * 삭제, 저장, COMPLETED 상태 변경은 하나의 트랜잭션으로 처리된다.
     */
    @Transactional
    public DisclosureParsingPersistenceResult replaceParsedResult(
            UUID disclosureDocumentId,
            ParsedDisclosureDocument parsedDocument,
            String parserName,
            String parserVersion
    ) {
        Objects.requireNonNull(
                disclosureDocumentId,
                "disclosureDocumentId는 필수입니다."
        );

        Objects.requireNonNull(
                parsedDocument,
                "parsedDocument는 필수입니다."
        );

        /*
         * 첫 파싱 뿐만 아니라 재파싱에서도 사용하기 위한 로직
         * -> 전체 교체 방식
         * 블록이 섹션을 참조하기 때문에 블록을 먼저 삭제한다.
         */
        // 재파싱 시 구 블록을 참조하는 청크/출처부터 제거한다. 실패하면 함께 롤백된다.
        chunkRepository.deleteAllByDisclosureDocumentId(disclosureDocumentId);

        int deletedBlockCount =
                blockRepository.deleteAllByDisclosureDocumentId(
                        disclosureDocumentId
                );

        int deletedSectionCount =
                sectionRepository.deleteAllByDisclosureDocumentId(
                        disclosureDocumentId
                );

        /*
         * 벌크 삭제 메서드의 clearAutomatically=true로 인해
         * 영속성 컨텍스트가 초기화된다.
         * 따라서 삭제가 끝난 뒤 DisclosureDocument를 다시 조회해야 한다.
         */
        DisclosureDocument document = documentRepository.findById(disclosureDocumentId)
                .orElseThrow(() -> new IllegalStateException(
                    "파싱 결과를 저장할 원문 문서를 찾을 수 없습니다."
                            + " disclosureDocumentId="
                            + disclosureDocumentId)
                );

        /*
         * 파싱 모델을 아직 저장되지 않은 JPA 엔티티로 변환한다.
         */
        DisclosureParseMappingResult mappingResult =
                entityMapper.map(
                        document,
                        parsedDocument
                );

        /*
         * ContentBlock이 Section을 참조하므로 Section을 먼저 저장한다.
         */
        sectionRepository.saveAll(mappingResult.sections());

        /*
         * Section INSERT를 DB에 먼저 반영해
         * ContentBlock의 FK가 안전하게 참조하도록 한다.
         */
        sectionRepository.flush();

        blockRepository.saveAll(mappingResult.blocks());

        /*
         * JSONB 변환 오류, FK 오류, 제약조건 오류 등을
         * COMPLETED 표시 전에 확인한다.
         */
        blockRepository.flush();

        /*
         * 파서가 원문에서 문서명을 읽었다면 갱신한다.
         * null이면 기존 문서명을 유지한다.
         */
        if (parsedDocument.documentName() != null) {
            document.updateDocumentClassification(
                    document.getDocumentRole(),
                    parsedDocument.documentName()
            );
        }

        /*
         * 위 저장이 모두 성공한 후에만 COMPLETED로 변경한다.
         */
        document.replaceRelatedDisclosureLinks(entityMapper.relatedLinksPayload(parsedDocument));
        var pdfReport = parsedDocument.pdfTextReport();
        if (document.getContentFormat() == com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat.PDF
                && pdfReport == null) {
            throw new IllegalArgumentException("PDF는 최소 추출 한계와 페이지 정보를 함께 저장해야 합니다.");
        }
        document.replaceParseMetadata(entityMapper.parseMetadataPayload(parsedDocument));
        if (pdfReport != null) {
            if (document.getContentFormat() != com.foliolens.backend.disclosure.domain.DisclosureDocumentContentFormat.PDF
                    || !com.foliolens.backend.disclosure.infrastructure.parsing.pdf.PdfTextDisclosureParser.NAME.equals(parserName)) {
                throw new IllegalArgumentException("PDF 최소 추출 결과와 문서/파서 형식이 일치하지 않습니다.");
            }
            document.markPartial(parserName, parserVersion, pdfReport.limitation(), Instant.now());
        } else {
            document.markCompleted(parserName, parserVersion, Instant.now());
        }

        /*
         * document는 조회 이후 영속 상태이므로 save()는 필요 없지만,
         * 상태 변경까지 즉시 검증하기 위해 flush한다.
         */
        documentRepository.flush();

        return new DisclosureParsingPersistenceResult(
                disclosureDocumentId,
                deletedSectionCount,
                deletedBlockCount,
                mappingResult.sections().size(),
                mappingResult.blocks().size()
        );
    }
}
