package com.CSE.AI.ML.PDF_engine;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.text.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.*;
import java.util.*;

@Service
public class PdfService {

public byte[] replaceText(MultipartFile file, String find, String replace) throws IOException {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            for (int p = 0; p < doc.getNumberOfPages(); p++) {
                PDPage page = doc.getPage(p);
                List<float[]> matches = findMatches(doc, p + 1, find);
                if (matches.isEmpty()) continue;
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    for (float[] m : matches) {
                        cs.setNonStrokingColor(Color.WHITE);
                        cs.addRect(m[0], m[1] - 2, m[2], m[3] + 2);
                        cs.fill();
                        cs.setNonStrokingColor(Color.BLACK);
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), m[4]);
                        cs.newLineAtOffset(m[0], m[1]);
                        cs.showText(replace);
                        cs.endText();
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private List<float[]> findMatches(PDDocument doc, int pageNo, String find) throws IOException {
        List<float[]> result = new ArrayList<>();
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> pos) throws IOException {
                int idx = text.indexOf(find);
                while (idx >= 0 && idx + find.length() <= pos.size()) {
                    TextPosition first = pos.get(idx);
                    TextPosition last = pos.get(idx + find.length() - 1);
                    float pageH = getCurrentPage().getMediaBox().getHeight();
                    float x = first.getXDirAdj();
                    float y = pageH - first.getYDirAdj();
                    float w = last.getXDirAdj() + last.getWidthDirAdj() - x;
                    result.add(new float[]{x, y, w, first.getHeightDir(), first.getFontSizeInPt()});
                    idx = text.indexOf(find, idx + find.length());
                }
                super.writeString(text, pos);
            }
        };
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        stripper.getText(doc);
        return result;
    }
}