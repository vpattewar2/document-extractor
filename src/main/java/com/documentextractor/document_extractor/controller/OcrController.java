package com.documentextractor.document_extractor.controller;

import com.documentextractor.document_extractor.service.DocumentExtractionService;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/v1/ocr")
public class OcrController {

    private final DocumentExtractionService documentExtractionService;

    public OcrController(final DocumentExtractionService documentExtractionService) {
        this.documentExtractionService = documentExtractionService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> extractText(@RequestParam("file") final MultipartFile file) {
        try {
            val result = this.documentExtractionService.extractDocument(file);
            return ResponseEntity.ok(result);
        } catch (final IllegalArgumentException e) {
            log.error("Invalid document upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (final Exception e) {
            log.error("Document extraction failed.", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "errors", List.of("Failed to process the document.")
                    ));
        }
    }
}
