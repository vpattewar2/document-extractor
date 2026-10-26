package com.documentextractor.document_extractor.service;


import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
public class DocumentExtractionService {

    private final OcrService ocrService;
    private final DetailsExtractionService detailsExtractionService;

    public DocumentExtractionService(
            final OcrService ocrService,
            final DetailsExtractionService detailsExtractionService) {
        this.ocrService = ocrService;
        this.detailsExtractionService = detailsExtractionService;
    }

    public Map<String, Object> extractDocument(final MultipartFile file) throws Exception {

        // Validate the uploaded file.
        if (file == null || file.isEmpty()) {
            log.error("File is Empty");
            throw new IllegalArgumentException("Please upload a file.");
        }

        log.info("Starting document extraction. File size: {} bytes", file.getSize());

        // Step 1: Extract text from the uploaded PDF or image.
        final String rawText = this.ocrService.extractText(file);

        log.info("Initial OCR pass text" + rawText);

        // Step 2: Convert OCR text into structured document fields.
        var result = this.detailsExtractionService.extractDetails(rawText);

        // Step 3: Run additional OCR only for PAN when DOB is missing.
        if ("PAN".equals(result.get("document_type"))) {
            val dataObject = result.get("data");

            if (dataObject instanceof Map<?, ?> data) {
                val dob = data.get("date_of_birth");

                if (dob == null || dob.toString().isBlank()) {
                    log.info("PAN DOB missing. Starting targeted OCR.");
                    val additionalText = this.ocrService.extractPanDobText(file);
                    log.info("Additional dob OCR pass text" + additionalText);

                    // Retry only when the additional OCR returns text.
                    if (additionalText != null && !additionalText.isBlank()) {
                        val combinedText = rawText + "\n" + additionalText;

                        result = this.detailsExtractionService.extractDetails(combinedText);
                    }
                }
            }
        }

        log.info("Document extraction completed.");
        return result;
    }
}

