
package com.documentextractor.document_extractor.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DetailsExtractionService {

    private static final Pattern PAN_PATTERN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("(?<!\\d)(?:\\d{4}[ -]?){2}\\d{4}(?!\\d)");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(?:\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{4}-\\d{2}-\\d{2})\\b");

    private static final Pattern FEMALE_PATTERN = Pattern.compile("\\bFEMALE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MALE_PATTERN = Pattern.compile("\\bMALE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSGENDER_PATTERN = Pattern.compile("\\bTRANSGENDER\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern NAME_PATTERN = Pattern.compile("\\b((?:[A-Z][a-z]+|[A-Z]{2,})"
                    + "(?:['’-][A-Z][a-z]+)?"
                    + "(?:\\s+(?:[A-Z][a-z]+|[A-Z]{2,})"
                    + "(?:['’-][A-Z][a-z]+)?){1,3})\\b");

    private static final Pattern TITLE_CASE_NAME_PATTERN = Pattern.compile("\\b[A-Z][a-z]{2,}"
                    + "(?:['’-][A-Z][a-z]+)?"
                    + "(?:\\s+[A-Z][a-z]{2,}"
                    + "(?:['’-][A-Z][a-z]+)?){1,3}\\b");

    private static final Pattern LABELED_VALUE_PATTERN = Pattern.compile("(?i)(PAN|DOB|DATE OF BIRTH|GENDER|AADHAAR)\\s*:?");
    private static final Pattern VALID_PAN_NAME_PATTERN = Pattern.compile("[A-Za-z]+(?:[ '-][A-Za-z]+){1,3}");
    private static final Pattern INVALID_NAME_CHARACTERS = Pattern.compile("^[^A-Za-z]+|[^A-Za-z]+$");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern DATE_ISO_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private static final Pattern PAN_HEADING_PATTERN = Pattern.compile("(?i)INCOME TAX|GOVT\\. OF INDIA|PERMANENT ACCOUNT"
                    + "|APPLICATION|DIGITALLY SIGNED|SIGNATURE");

    private static final Pattern AADHAAR_HEADING_PATTERN = Pattern.compile("(?i)C/O|ADDRESS:|GOVERNMENT OF INDIA"
                    + "|UNIQUE IDENTIFICATION|AUTHORITY OF INDIA"
                    + "|INCOME TAX|PERMANENT ACCOUNT|FATHER"
                    + "|DATE OF BIRTH|ENROLMENT|AADHAAR IS|UIDAI"
                    + "|MOBILE:|PIN CODE:|DISTRICT:|STATE:");

    private static final Pattern REJECTED_NAME_PATTERN = Pattern.compile("(?i)GOVERNMENT OF INDIA|INCOME TAX|PERMANENT ACCOUNT"
                    + "|DATE OF BIRTH|UNIQUE IDENTIFICATION"
                    + "|INFORMATION|AADHAAR");

    private static final Pattern REJECTED_AADHAAR_NAME_PATTERN = Pattern.compile("(?i)GOVERNMENT|INFORMATION|AADHAAR|UIDAI"
                    + "|AUTHORITY|FEMALE|MALE|ADDRESS"
                    + "|DISTRICT|MAHARASHTRA");

    public Map<String, Object> extractDetails(final String rawText) {

        // Normalize the OCR text.
        val text = rawText == null ? "" : rawText;
        val upperText = text.toUpperCase(Locale.ROOT);

        // Identify the document type.
        val isPan = upperText.contains("PERMANENT ACCOUNT NUMBER") || PAN_PATTERN.matcher(upperText).find();

        val isAadhaar = isAadhaarDocument(text);

        val documentType = determineDocumentType(isPan, isAadhaar);

        // Prepare the extraction response collections.
        val data = new LinkedHashMap<String, Object>();
        val missingFields = new ArrayList<String>();
        val errors = new ArrayList<String>();

        // Extract document-specific fields.
        if ("PAN".equals(documentType)) {
            extractPanDetails(text, upperText, data, missingFields);
        } else if ("AADHAAR".equals(documentType)) {
            extractAadhaarDetails(text, data, missingFields);
        } else {
            log.error("Unable to identify the uploaded document type.");
            errors.add("Could not identify the document as PAN or Aadhaar.");
        }

        // Build the API response.
        val response = new LinkedHashMap<String, Object>();

        response.put("document_type", documentType);
        response.put("success", errors.isEmpty() && missingFields.isEmpty());
        response.put("data", data);
        response.put("missing_fields", missingFields);
        response.put("errors", errors);

        return response;
    }

    private String determineDocumentType(final boolean isPan,
                                         final boolean isAadhaar) {
        if (isPan) {
            return "PAN";
        }
        if (isAadhaar) {
            return "AADHAAR";
        }
        return "UNKNOWN";
    }

    private void extractPanDetails(final String text,
                                   final String upperText,
                                   final Map<String, Object> data,
                                   final List<String> missingFields) {
        val panNumber = findFirst(PAN_PATTERN, upperText);
        val name = cleanPanHolderName(findDocumentHolderName(text, "PAN"));
        val dateOfBirth = findDate(text);

        data.put("name", name);
        data.put("pan_number", panNumber);
        data.put("date_of_birth", dateOfBirth);

        addMissingIfBlank(missingFields, "name", name);
        addMissingIfBlank(missingFields, "pan_number", panNumber);
        addMissingIfBlank(missingFields, "date_of_birth", dateOfBirth);
    }

    private void extractAadhaarDetails(final String text,
                                       final Map<String, Object> data,
                                       final List<String> missingFields) {
        var aadhaarNumber = findFirst(AADHAAR_PATTERN, text);
        var name = findAadhaarNameNearDob(text);

        // Use the document-specific name extractor as a fallback.
        if (name == null || name.isBlank()) {
            name = findDocumentHolderName(text, "AADHAAR");
        }
        // Mask the Aadhaar number before returning it.
        if (aadhaarNumber != null) {
            val digits = aadhaarNumber.replaceAll("\\D", "");
            aadhaarNumber = "XXXX-XXXX-" + digits.substring(8);
        }

        val dateOfBirth = findDate(text);
        val gender = findGender(text);

        data.put("name", name);
        data.put("aadhaar_number", aadhaarNumber);
        data.put("date_of_birth", dateOfBirth);

        if (gender != null) {
            data.put("gender", gender);
        }

        addMissingIfBlank(missingFields, "name", name);
        addMissingIfBlank(missingFields, "aadhaar_number", aadhaarNumber);
        addMissingIfBlank(missingFields, "date_of_birth", dateOfBirth);
    }

    private String findFirst(final Pattern pattern,
                             final String text) {
        val matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private String findLabeledValue(final String text,
                                    final String label) {
        val pattern = Pattern.compile("(?im)^\\s*" + Pattern.quote(label) + "\\s*[:\\-]?\\s*(.+?)\\s*$");
        final Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            val value = matcher.group(1).trim();
            // Avoid returning another field label as a name.
            if (!LABELED_VALUE_PATTERN.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }

    private String findDocumentHolderName(final String text,
                                          final String documentType) {
        val lines = text.split("\\R");
        if ("PAN".equals(documentType)) {
            final String panName = findPanHolderName(lines);
            if (panName != null) {
                return panName;
            }
        }

        // Find a likely name in Aadhaar OCR text.
        for (var rawLine : lines) {
            val line = rawLine.trim();
            val upper = line.toUpperCase(Locale.ROOT);

            if (AADHAAR_HEADING_PATTERN.matcher(upper).find()) {
                continue;
            }

            val candidate = extractNameCandidate(line);
            if (candidate != null) {
                return candidate;
            }
        }
        return findLabeledValue(text, "Name");
    }

    private String findPanHolderName(final String[] lines) {
        var panFound = false;
        for (var rawLine : lines) {
            var line = rawLine.trim();
            var upper = line.toUpperCase(Locale.ROOT);

            if (PAN_PATTERN.matcher(upper).find()) {
                panFound = true;
                continue;
            }

            if (!panFound) {
                continue;
            }

            // Stop before the father's-name section.
            if (upper.contains("FATHER")) {
                break;
            }
            // Ignore common PAN card headings.
            if (PAN_HEADING_PATTERN.matcher(upper).find()) {
                continue;
            }

            val candidate = extractNameCandidate(line);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String extractNameCandidate(final String line) {
        val matcher = NAME_PATTERN.matcher(line);
        if (matcher.find()) {
            var candidate = matcher.group(1).trim();
            var upper = candidate.toUpperCase(Locale.ROOT);

            if (REJECTED_NAME_PATTERN.matcher(upper).find()) {
                return null;
            }
            return MULTIPLE_SPACES.matcher(candidate)
                    .replaceAll(" ")
                    .trim();
        }
        return null;
    }

    private String cleanPanHolderName(final String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        // Remove OCR text after separators.
        var beforePipe = name.split("[|]", 2)[0];
        var beforeDash = beforePipe.split("\\s+[-—]", 2)[0];

        // Remove unwanted symbols and normalize whitespace.
        val withoutSymbols = INVALID_NAME_CHARACTERS.matcher(beforeDash).replaceAll("");
        val normalized = MULTIPLE_SPACES.matcher(withoutSymbols)
                        .replaceAll(" ")
                        .trim();

        // Remove common OCR noise appended to the name.
        val cleaned = normalized.replaceFirst("(?i)\\s+PEO$", "").trim();
        // Require two to four alphabetic name words.
        if (!VALID_PAN_NAME_PATTERN.matcher(cleaned).matches()) {
            return null;
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private String findDate(final String text) {
        val matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            val candidate = matcher.group();

            try {
                if (DATE_ISO_PATTERN.matcher(candidate).matches()) {
                    return LocalDate.parse(candidate).toString();
                }
                val formatter = DateTimeFormatter.ofPattern(candidate.contains("/") ? "dd/MM/uuuu" : "dd-MM-uuuu");
                return LocalDate.parse(candidate, formatter).toString();

            } catch (final DateTimeParseException e) {
                log.debug("Ignoring an invalid date recognized by OCR.");
            }
        }
        return null;
    }

    private String findGender(final String text) {
        if (FEMALE_PATTERN.matcher(text).find()) {
            return "Female";
        }
        if (MALE_PATTERN.matcher(text).find()) {
            return "Male";
        }
        if (TRANSGENDER_PATTERN.matcher(text).find()) {
            return "Transgender";
        }
        return null;
    }

    private void addMissingIfBlank(final List<String> missingFields,
                                   final String field,
                                   final String value) {
        if (value == null || value.isBlank()) {
            missingFields.add(field);
        }
    }

    private boolean isAadhaarDocument(final String text) {
        val upper = text.toUpperCase(Locale.ROOT);
        val hasAadhaarKeyword = upper.contains("AADHAAR") || upper.contains("UIDAI") || upper.contains("UNIQUE IDENTIFICATION");
        val hasAadhaarNumberLabel = upper.contains("YOUR AADHAAR NO") || upper.contains("AADHAAR NO");
        val hasAadhaarNumber = AADHAAR_PATTERN.matcher(text).find();
        val hasDob = DATE_PATTERN.matcher(text).find();

        // Prefer explicit Aadhaar indicators.
        return hasAadhaarKeyword
                || hasAadhaarNumberLabel
                || (hasAadhaarNumber
                && hasDob
                && (FEMALE_PATTERN.matcher(upper).find()
                || MALE_PATTERN.matcher(upper).find()
                || TRANSGENDER_PATTERN.matcher(upper).find()));
    }

    private String findAadhaarNameNearDob(final String text) {
        val lines = text.split("\\R");
        // Find the DOB line, then inspect nearby lines for a name.
        for (var i = 0; i < lines.length; i++) {
            val upper = lines[i].toUpperCase(Locale.ROOT);
            if (upper.contains("DOB") || upper.contains("DATE OF BIRTH")) {
                // Check the three preceding lines, closest first.
                for (var j = i - 1; j >= Math.max(0, i - 3); j--) {
                    val candidate = extractTitleCaseName(lines[j]);
                    if (candidate != null) {
                        return candidate;
                    }
                }
                // Check the DOB line itself.
                val candidate = extractTitleCaseName(lines[i]);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String extractTitleCaseName(final String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        val matcher = TITLE_CASE_NAME_PATTERN.matcher(line);
        String bestCandidate = null;
        int bestWordCount = 0;

        while (matcher.find()) {
            val candidate = matcher.group().trim();
            val upper = candidate.toUpperCase(Locale.ROOT);
            // Reject document headings and labels.
            if (REJECTED_AADHAAR_NAME_PATTERN.matcher(upper).find()) {
                continue;
            }
            val wordCount = candidate.split("\\s+").length;
            // Prefer the longest name-like phrase on the line.
            if (wordCount > bestWordCount) {
                bestCandidate = candidate;
                bestWordCount = wordCount;
            }
        }
        return bestCandidate;
    }
}
