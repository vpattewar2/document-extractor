
package com.documentextractor.document_extractor.service;

import com.sun.jna.NativeLibrary;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Locale;

@Slf4j
@Service
public class OcrService {

    private static final String TESSERACT_LIBRARY_PATH = "/opt/homebrew/opt/tesseract/lib";
    private static final String TESSDATA_PATH = "/opt/homebrew/share/tessdata";

    private static final int PDF_OCR_DPI = 200;
    private static final int DOB_OCR_DPI = 300;
    private static final int IMAGE_SCALE_FACTOR = 3;

    public String extractText(final MultipartFile file) throws Exception {
        // Configure the native Tesseract library path.
        NativeLibrary.addSearchPath("tesseract", TESSERACT_LIBRARY_PATH);

        // Create and configure the Tesseract OCR engine.
        val tesseract = new Tesseract();
        tesseract.setDatapath(TESSDATA_PATH);
        tesseract.setLanguage("eng");

        // Read the uploaded file.
        val bytes = file.getBytes();
        val filename = file.getOriginalFilename();

        // Validate the filename.
        if (filename == null || filename.isBlank()) {
            log.error("Uploaded file has no filename.");
            throw new IllegalArgumentException("File name is missing.");
        }

        // Normalize the filename to support uppercase extensions.
        val lowerFilename = filename.toLowerCase(Locale.ROOT);

        // Accept PDF, JPG, JPEG, and PNG files only.
        if (!(lowerFilename.endsWith(".pdf")
                || lowerFilename.endsWith(".jpg")
                || lowerFilename.endsWith(".jpeg")
                || lowerFilename.endsWith(".png"))) {

            log.error("Unsupported document format.");
            throw new IllegalArgumentException("Unsupported file format. Upload a PDF, JPG, JPEG, or PNG file.");
        }

        // Process single-page PDF files.
        if (lowerFilename.endsWith(".pdf")) {
            try (val document = Loader.loadPDF(bytes)) {
                // Reject PDFs containing zero or multiple pages.
                validateSinglePagePdf(document);

                // Render the only page (page index 0) as an image.
                val renderer = new PDFRenderer(document);
                val image = renderer.renderImageWithDPI(0, PDF_OCR_DPI, ImageType.RGB);

                // Extract text from the rendered page.
                return tesseract.doOCR(image);
            }
        }

        // Read JPG, JPEG, or PNG directly from the uploaded bytes.
        val image = ImageIO.read(new ByteArrayInputStream(bytes));

        if (image == null) {
            log.error("The uploaded image could not be read");
            throw new IllegalArgumentException("The uploaded image could not be read.");
        }

        return tesseract.doOCR(image);
    }

    // Run targeted OCR on the suspected PAN DOB region.
    public String extractPanDobText(final MultipartFile file) throws Exception {
        // Configure the native Tesseract library path.
        NativeLibrary.addSearchPath("tesseract", TESSERACT_LIBRARY_PATH);

        // Configure Tesseract for the additional OCR pass.
        val tesseract = new Tesseract();
        tesseract.setDatapath(TESSDATA_PATH);
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(6);

        // Read the uploaded file.
        val bytes = file.getBytes();
        val filename = file.getOriginalFilename();

        // Validate the filename.
        if (filename == null || filename.isBlank()) {
            log.error("File name is missing.");
            throw new IllegalArgumentException("File name is missing.");
        }

        val lowerFilename = filename.toLowerCase(Locale.ROOT);
        // Handle single-page PDFs.
        if (lowerFilename.endsWith(".pdf")) {
            try (val document = Loader.loadPDF(bytes)) {
                // Reject PDFs containing zero or multiple pages.
                validateSinglePagePdf((document));

                // Render the only page at a higher resolution for DOB OCR.
                val renderer = new PDFRenderer(document);
                val image = renderer.renderImageWithDPI(0, DOB_OCR_DPI, ImageType.RGB);
                return ocrPanDobRegion(image, tesseract);
            }
        }

        // Handle JPG, JPEG, and PNG files.
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

        if (image == null) {
            throw new IllegalArgumentException(
                    "The uploaded image could not be read."
            );
        }
        return ocrPanDobRegion(image, tesseract);
    }

    // Crop and enlarge the likely DOB area before running OCR.
    private String ocrPanDobRegion(final BufferedImage image,
                                   final ITesseract tesseract) throws TesseractException {
        // Keep the lower 60% of the image.
        val startY = (int) (image.getHeight() * 0.40);

        val crop = image.getSubimage(0, startY, image.getWidth(), image.getHeight() - startY);

        // Enlarge the crop to improve recognition of small text.
        val width = crop.getWidth() * IMAGE_SCALE_FACTOR;
        val height = crop.getHeight() * IMAGE_SCALE_FACTOR;

        val enlarged = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        val graphics = enlarged.createGraphics();

        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.drawImage(crop, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        // First OCR pass: treat the crop as a text block.
        tesseract.setPageSegMode(6);
        val text1 = tesseract.doOCR(enlarged);

        // Second OCR pass: detect text regions more flexibly.
        tesseract.setPageSegMode(11);
        val text2 = tesseract.doOCR(enlarged);

        return text1 + "\n" + text2;
    }

    private void validateSinglePagePdf(final PDDocument document) {
        if (document.getNumberOfPages() != 1) {
            log.error("PDF must contain exactly one page.");
            throw new IllegalArgumentException("Only single-page PDF files are supported.");
        }
    }
}