package com.CSE.AI.ML.PDF_engine;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {
    private final PdfService service;

    public PdfController(PdfService service) { this.service = service; }

    @PostMapping("/replace")
    public ResponseEntity<byte[]> replace(@RequestParam MultipartFile file,
                                          @RequestParam String find,
                                          @RequestParam String replace) throws IOException {
        byte[] out = service.replaceText(file, find, replace);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"replaced.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(out);
    }
}