package com.documentextractor.document_extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DocumentExtractorApplicationTests {

    private static final String OCR_RESOURCE_DIRECTORY = "ocr/";

    private static final String EXPECTED_AADHAAR_JSON = """
            {
              "document_type": "AADHAAR",
              "success": true,
              "data": {
                "name": "Vaishnavi Dattatray Pattewar",
                "aadhaar_number": "XXXX-XXXX-1732",
                "date_of_birth": "2003-01-22",
                "gender": "Female"
              },
              "missing_fields": [],
              "errors": []
            }
            """;

    private static final String EXPECTED_PAN_JSON = """
            {
              "document_type": "PAN",
              "success": true,
              "data": {
                "name": "VAISHNAVI DATTATRAY PATTEWAR",
                "pan_number": "HMAPP5769L",
                "date_of_birth": "2003-01-22"
              },
              "missing_fields": [],
              "errors": []
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
            "aadhaar.jpg, image/jpeg, AADHAAR",
            "aadhaar.jpeg, image/jpeg, AADHAAR",
            "aadhaar.png, image/png, AADHAAR",
            "aadhaar.pdf, application/pdf, AADHAAR",
            "pan.jpg, image/jpeg, PAN",
            "pan.jpeg, image/jpeg, PAN",
            "pan.png, image/png, PAN",
            "pan.pdf, application/pdf, PAN"
    })
    void shouldExtractExpectedDetailsFromRealDocument(final String filename,
                                                      final String contentType,
                                                      final String expectedDocumentType) throws Exception {

        // Load the actual image/PDF bytes from src/test/resources/.
        var resource = new ClassPathResource(filename);
        assertTrue(resource.exists(), "Missing test fixture: " + resource.getPath());

        try (var inputStream = resource.getInputStream()) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    filename,
                    contentType,
                    inputStream
            );

            // Call the real controller and real OCR/extraction services.
            var result = mockMvc.perform(multipart("/api/v1/ocr/extract").file(file))
                    .andExpect(status().isOk())
                    .andReturn();

            var actualJson = this.objectMapper.readTree(result.getResponse().getContentAsString());

            // Compare JSON structurally, so whitespace and object key order do not matter.
            var expectedJson = objectMapper.readTree("AADHAAR".equals(expectedDocumentType)
                    ? EXPECTED_AADHAAR_JSON
                    : EXPECTED_PAN_JSON);

            assertEquals(expectedJson, actualJson, "Unexpected OCR response for fixture: " + filename);
        }
    }

    @Test
    void shouldRejectUnsupportedFileFormat() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "test content".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/ocr/extract").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        var file = new MockMultipartFile(
                "file",
                "pan.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/ocr/extract").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRequestWithoutFile() throws Exception {
        mockMvc.perform(multipart("/api/v1/ocr/extract"))
                .andExpect(status().isBadRequest());
    }
}