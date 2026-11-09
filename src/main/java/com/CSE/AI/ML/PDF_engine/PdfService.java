package com.CSE.AI.ML.PDF_engine;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfService {

    /** One place where the searched text appears. */
    public record Match(int id, int page, int line, String before, String found, String after) {}

    /** The edited PDF and how many places were replaced (bytes is null when count is 0). */
    public record Result(byte[] bytes, int count) {}

    private record Hit(Match info, int pageIndex, List<List<TextPosition>> groups, float avail) {}

    public List<Match> analyze(MultipartFile file, String find, boolean ignoreCase) throws IOException {
        Pattern pattern = pattern(find, ignoreCase);
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return scan(doc, pattern).stream().map(Hit::info).toList();
        }
    }

    /** ids = the match numbers to change; null means change all of them. */
    public Result replaceText(MultipartFile file, String find, String replace,
                              boolean ignoreCase, Set<Integer> ids) throws IOException {
        Pattern pattern = pattern(find, ignoreCase);
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try {
                font.encode(replace);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                    "The replacement has characters the standard font cannot draw. Use English letters, numbers and common symbols.");
            }

            Map<Integer, List<Hit>> byPage = new TreeMap<>();
            int total = 0;
            for (Hit h : scan(doc, pattern)) {
                if (ids == null || ids.contains(h.info().id())) {
                    byPage.computeIfAbsent(h.pageIndex(), k -> new ArrayList<>()).add(h);
                    total++;
                }
            }
            if (total == 0) {
                return new Result(null, 0);
            }

            for (Map.Entry<Integer, List<Hit>> entry : byPage.entrySet()) {
                PDPage page = doc.getPage(entry.getKey());
                PDRectangle box = page.getCropBox();
                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    for (Hit hit : entry.getValue()) {
                        List<List<TextPosition>> groups = hit.groups();
                        for (int g = 0; g < groups.size(); g++) {
                            List<TextPosition> group = groups.get(g);
                            TextPosition first = group.get(0);
                            TextPosition last = group.get(group.size() - 1);
                            float fs = first.getFontSizeInPt();
                            float x = box.getLowerLeftX() + first.getXDirAdj();
                            float w = (last.getXDirAdj() + last.getWidthDirAdj()) - first.getXDirAdj();
                            float baseline = box.getUpperRightY() - first.getYDirAdj();

                            cs.setNonStrokingColor(Color.WHITE);
                            cs.addRect(x - 1, baseline - fs * 0.25f, w + 2, fs * 1.15f);
                            cs.fill();

                            if (g == 0 && !replace.isEmpty()) {
                                float size = fs;
                                float scale = 100f;
                                float avail = hit.avail();
                                float full = font.getStringWidth(replace) / 1000f;
                                float textW = full * fs;
                                if (textW > avail) {
                                    float need = avail / textW;
                                    if (need >= 0.75f) {
                                        scale = need * 100f;
                                    } else {
                                        scale = 75f;
                                        size = fs * Math.max(need / 0.75f, 0.7f);
                                        if (full * size * 0.75f > avail) {
                                            scale = Math.max(50f, 100f * avail / (full * size));
                                        }
                                    }
                                }
                                cs.beginText();
                                cs.setFont(font, size);
                                cs.setHorizontalScaling(scale);
                                cs.setNonStrokingColor(Color.BLACK);
                                cs.newLineAtOffset(x, baseline);
                                cs.showText(replace);
                                cs.endText();
                            }
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new Result(out.toByteArray(), total);
        }
    }

    private Pattern pattern(String find, boolean ignoreCase) {
        find = find.trim();
        if (find.isEmpty()) {
            throw new IllegalArgumentException("Enter the text to find.");
        }
        return Pattern.compile(Pattern.quote(find),
                ignoreCase ? (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : 0);
    }

    /** Finds every match in reading order and numbers them 1, 2, 3... across the whole document. */
    private List<Hit> scan(PDDocument doc, Pattern pattern) throws IOException {
        List<Hit> hits = new ArrayList<>();
        int id = 0;

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            WordCollector collector = new WordCollector();
            collector.setStartPage(i + 1);
            collector.setEndPage(i + 1);
            collector.writeText(doc, Writer.nullWriter());

            StringBuilder sb = new StringBuilder();
            List<TextPosition> map = new ArrayList<>();
            List<Integer> lineOf = new ArrayList<>();
            int line = 0;
            float prevY = Float.NaN;

            for (List<TextPosition> word : collector.words) {
                if (word.isEmpty()) {
                    continue;
                }
                TextPosition f = word.get(0);
                if (Float.isNaN(prevY) || Math.abs(f.getYDirAdj() - prevY) > f.getFontSizeInPt() * 0.5f) {
                    line++;
                    prevY = f.getYDirAdj();
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                    map.add(null);
                    lineOf.add(line);
                }
                for (TextPosition tp : word) {
                    String u = tp.getUnicode();
                    for (int k = 0; k < u.length(); k++) {
                        sb.append(u.charAt(k));
                        map.add(tp);
                        lineOf.add(line);
                    }
                }
            }

            String text = sb.toString();
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                List<TextPosition> run = new ArrayList<>();
                for (int k = m.start(); k < m.end(); k++) {
                    TextPosition tp = map.get(k);
                    if (tp != null && (run.isEmpty() || run.get(run.size() - 1) != tp)) {
                        run.add(tp);
                    }
                }
                // one group per line of text, so a match that wraps onto a second line is covered on both
                List<List<TextPosition>> groups = new ArrayList<>();
                List<TextPosition> current = new ArrayList<>();
                for (TextPosition tp : run) {
                    if (!current.isEmpty()) {
                        TextPosition prev = current.get(current.size() - 1);
                        if (Math.abs(tp.getYDirAdj() - prev.getYDirAdj()) > prev.getFontSizeInPt() * 0.5f) {
                            groups.add(current);
                            current = new ArrayList<>();
                        }
                    }
                    current.add(tp);
                }
                if (!current.isEmpty()) {
                    groups.add(current);
                }
                if (groups.isEmpty()) {
                    continue;
                }
                PDRectangle pageBox = doc.getPage(i).getCropBox();
                TextPosition gFirst = groups.get(0).get(0);
                List<TextPosition> g0 = groups.get(0);
                TextPosition gLast = g0.get(g0.size() - 1);
                float fontSize = gFirst.getFontSizeInPt();
                float limit = pageBox.getWidth() - 20;
                if (groups.size() == 1) {
                    for (int k = m.end(); k < map.size(); k++) {
                        TextPosition nx = map.get(k);
                        if (nx == null) {
                            continue;
                        }
                        if (Math.abs(nx.getYDirAdj() - gLast.getYDirAdj()) <= fontSize * 0.5f
                                && nx.getXDirAdj() > gLast.getXDirAdj()) {
                            limit = nx.getXDirAdj() - fontSize * 0.2f;
                        }
                        break;
                    }
                }
                float oldWidth = gLast.getXDirAdj() + gLast.getWidthDirAdj() - gFirst.getXDirAdj();
                float avail = Math.max(limit - gFirst.getXDirAdj(), oldWidth);
                Match info = new Match(++id, i + 1, lineOf.get(m.start()),
                        text.substring(Math.max(0, m.start() - 30), m.start()),
                        text.substring(m.start(), m.end()),
                        text.substring(m.end(), Math.min(text.length(), m.end() + 30)));
                hits.add(new Hit(info, i, groups, avail));
            }
        }
        return hits;
    }

    /** Collects the words of a page together with their positions. */
    private static class WordCollector extends PDFTextStripper {
        final List<List<TextPosition>> words = new ArrayList<>();

        WordCollector() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            words.add(new ArrayList<>(textPositions));
        }
    }
}