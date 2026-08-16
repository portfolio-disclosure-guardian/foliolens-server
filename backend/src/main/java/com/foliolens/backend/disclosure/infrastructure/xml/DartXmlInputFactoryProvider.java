package com.foliolens.backend.disclosure.infrastructure.xml;

import com.foliolens.backend.global.exception.BusinessException;
import com.foliolens.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;

@Component
public class DartXmlInputFactoryProvider {

    /**
     * 호출할 때마다 새로운 XMLInputFactory를 생성한다.
     *
     * XMLInputFactory 인스턴스를 여러 파일과 스레드가
     * 공유하지 않도록 한다.
     */
    public XMLInputFactory create() {
        XMLInputFactory factory =
                XMLInputFactory.newFactory();

        setRequiredProperty(
                factory,
                XMLInputFactory.SUPPORT_DTD,
                false
        );

        setRequiredProperty(
                factory,
                "javax.xml.stream.isSupportingExternalEntities",
                false
        );

        setRequiredProperty(
                factory,
                XMLInputFactory.IS_NAMESPACE_AWARE,
                true
        );

        setRequiredProperty(
                factory,
                XMLInputFactory.IS_COALESCING,
                true
        );

        factory.setXMLResolver(
                (
                        publicId,
                        systemId,
                        baseUri,
                        namespace
                ) -> {
                    throw new XMLStreamException(
                            "외부 XML 리소스 접근은 허용되지 않습니다. "
                                    + "systemId=" + systemId
                    );
                }
        );

        return factory;
    }

    private void setRequiredProperty(
            XMLInputFactory factory,
            String propertyName,
            Object value
    ) {
        try {
            factory.setProperty(
                    propertyName,
                    value
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.DATASET_503_1,
                    "현재 XML 파서가 필수 보안 설정을 "
                            + "지원하지 않습니다. property="
                            + propertyName,
                    exception
            );
        }
    }
}
