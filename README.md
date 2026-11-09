# Enterprise PDF Workflow & Text-Replacement Engine

AJP mini project, Team 5

A web application that finds text in a PDF, shows every place it appears, and lets the user choose which occurrences to replace before downloading the corrected file.

**Live demo:** https://pdf-engine-1-vlqv.onrender.com
(Hosted on a free plan: if the site has been idle, the first load can take up to a minute.)

## Features

- Upload a PDF (up to 10 MB) by drag and drop or file browser
- Scan for a word or phrase, with an optional ignore-capital-letters mode
- See how many times the text appears and on which pages
- See the page number, line number and surrounding words for every match
- Choose exactly which matches to replace, then download the edited PDF
- Finds words that the PDF splits into pieces internally (for example "Gr" + "apes")
- Clear messages for wrong file type, oversized file, damaged PDF and no matches

## How it works

1. **Scan.** `POST /api/pdf/scan` reads each page with Apache PDFBox, collects every character with its position, joins them into one text string per page, and searches that string. Matches are numbered 1, 2, 3 across the document.
2. **Review.** The page lists the matches in a table with checkboxes.
3. **Replace.** `POST /api/pdf/replace` repeats the scan, keeps only the selected match numbers, covers each old match with a white rectangle and draws the new text at the same position, shrinking it to fit the available space.

The file is processed in memory and is not saved on the server.

## Technology

| Part | Technology |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot (REST controller and service) |
| PDF processing | Apache PDFBox 3.0.3 |
| Frontend | HTML, CSS and JavaScript (single page) |
| Deployment | Docker on Render, code on GitHub |

## Project structure

```
src/main/java/com/CSE/AI/ML/PDF_engine/
    PdfEngineApplication.java   application entry point
    PdfController.java          /api/pdf/scan and /api/pdf/replace
    PdfService.java             scanning, line numbering and replacement
src/main/resources/static/index.html   user interface
src/main/resources/application.properties
Dockerfile                      build and run on Render
```

## Run locally

Requires Java 17.

```
./mvnw spring-boot:run        (Windows PowerShell: .\mvnw spring-boot:run)
```

Then open http://localhost:8080

## Known limitations

- The old text is covered by a white box, not deleted, so it can still be selected or copied from the PDF.
- The new text is drawn in a standard font (Helvetica), so it will not match the original font exactly.
- Replacement text must use English letters, numbers and common symbols.
- A replacement much longer than the original is squeezed to fit the same space.
- Scanned or image-only PDFs contain no text to match.
- Line numbers count text lines from the top of each page, so complex layouts such as tables may be numbered differently from what the eye expects.

## Team

Team 5 (CSE-AIML-O Section)

Team Leader: J. Sandeep Srikar - AP25110060945
Member 1: Ch. Pawan - AP25110071019
Member 2: V. Lokesh - AP25110070366
Member 3: Srivathsav K - AP25110070616
Member 4: (not filled in yet)

Project Title: Enterprise PDF Workflow & Automated Text-Replacement Engine

## Screenshots

Add screenshots of the upload page, the match table and a before/after PDF here.