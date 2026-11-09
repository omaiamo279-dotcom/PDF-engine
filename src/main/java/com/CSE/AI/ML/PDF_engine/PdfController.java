package com.CSE.AI.ML.PDF_engine;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final String UNREADABLE = "This PDF could not be read. It may be damaged or password-protected.";

    private final PdfService service;

    public PdfController(PdfService service) { this.service = service; }

    /** Step 1: list every place the text appears (page, line, surrounding words). */
    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestParam MultipartFile file,
                                  @RequestParam String find,
                                  @RequestParam(defaultValue = "false") boolean ignoreCase) {
        String problem = check(file, find);
        if (problem != null) return error(HttpStatus.BAD_REQUEST, problem);
        try {
            return ResponseEntity.ok(service.analyze(file, find, ignoreCase));
        } catch (IOException e) {
            return error(HttpStatus.BAD_REQUEST, UNREADABLE);
        }
    }

    /** Step 2: replace the chosen matches (ids like "1,3,4"), or all of them when ids is missing. */
    @PostMapping("/replace")
    public ResponseEntity<?> replace(@RequestParam MultipartFile file,
                                     @RequestParam String find,
                                     @RequestParam String replace,
                                     @RequestParam(defaultValue = "false") boolean ignoreCase,
                                     @RequestParam(required = false) String ids) {
        String problem = check(file, find);
        if (problem != null) return error(HttpStatus.BAD_REQUEST, problem);

        Set<Integer> picked = null;
        if (ids != null && !ids.isBlank()) {
            try {
                picked = Arrays.stream(ids.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Integer::valueOf).collect(Collectors.toSet());
            } catch (NumberFormatException e) {
                return error(HttpStatus.BAD_REQUEST, "The selection is not valid. Scan the document again.");
            }
        }

        try {
            PdfService.Result r = service.replaceText(file, find, replace, ignoreCase, picked);
            if (r.count() == 0) {
                return error(HttpStatus.NOT_FOUND, "None of the selected matches were found. Scan the document again.");
            }
            String original = file.getOriginalFilename();
            String base = original.substring(0, original.length() - 4).replaceAll("[^A-Za-z0-9._-]", "_");
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + base + "-replaced.pdf\"")
                .header("X-Match-Count", String.valueOf(r.count()))
                .contentType(MediaType.APPLICATION_PDF)
                .body(r.bytes());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            return error(HttpStatus.BAD_REQUEST, UNREADABLE);
        }
    }

    private String check(MultipartFile file, String find) {
        String name = file.getOriginalFilename();
        if (file.isEmpty()) return "Choose a PDF file first.";
        if (name == null || !name.toLowerCase().endsWith(".pdf")) return "Only PDF files are supported.";
        if (file.getSize() > MAX_BYTES) return "This file is larger than 10 MB. Choose a smaller PDF.";
        if (find.isBlank()) return "Enter the text to find.";
        return null;
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(message);
    }
}