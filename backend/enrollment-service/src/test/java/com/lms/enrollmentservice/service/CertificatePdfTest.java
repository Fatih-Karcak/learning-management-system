package com.lms.enrollmentservice.service;

import static org.junit.jupiter.api.Assertions.*;

import com.lms.enrollmentservice.domain.entity.Certificate;
import java.time.LocalDateTime;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class CertificatePdfTest {
  @Test
  void certificateEmbedsUnicodeFontsAndPreservesRealNames() throws Exception {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setCharacterEncoding("UTF-8");
    var templates = new SpringTemplateEngine();
    templates.setTemplateResolver(resolver);
    var service = new CertificateService(null, null, null, null, templates, null);
    var certificate =
        Certificate.builder()
            .studentName("Çağrı Şimşek İpek")
            .courseTitle(
                "A complete guide to software engineering and reliable distributed applications")
            .instructorName("Özgür Güçlü")
            .verificationCode("UNICODE-REGRESSION")
            .issuedAt(LocalDateTime.of(2026, 10, 24, 10, 0))
            .build();
    byte[] pdf = ReflectionTestUtils.invokeMethod(service, "generatePdf", certificate);
    try (var document = PDDocument.load(pdf)) {
      String text = new PDFTextStripper().getText(document);
      assertTrue(text.contains("Çağrı Şimşek İpek"), text);
      assertTrue(text.contains("Özgür Güçlü"), text);
      assertTrue(text.contains("CERTIFICATE OF COMPLETION"));
      assertTrue(text.contains("UNICODE-REGRESSION"));
      assertTrue(text.contains("24 October 2026"), text);
      assertEquals(1, document.getNumberOfPages(), "Long titles must fit on one certificate");
    }
  }
}
