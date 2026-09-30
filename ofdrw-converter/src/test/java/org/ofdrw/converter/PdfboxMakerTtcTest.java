package org.ofdrw.converter;

import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.ofdrw.core.text.font.CT_Font;
import org.ofdrw.reader.OFDReader;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PdfboxMakerTtcTest {

    @Test
    void selectedTtcFaceSurvivesPdfSave() throws Exception {
        // A system TTC keeps this test small; environments without it skip the integration check.
        Path ttc = Paths.get(System.getenv().getOrDefault("OFDRW_TEST_TTC", "C:/Windows/Fonts/simsun.ttc"));
        assumeTrue(Files.isRegularFile(ttc));

        Method getFont = PdfboxMaker.class.getDeclaredMethod("getTrueTypeFont", CT_Font.class, Path.class, boolean.class);
        getFont.setAccessible(true);
        CT_Font requested = new CT_Font("SimSun").setFamilyName("宋体");
        try (OFDReader reader = new OFDReader(Paths.get("src/test/resources/999.ofd"));
             PDDocument pdf = new PDDocument()) {
            PdfboxMaker maker = new PdfboxMaker(reader, pdf);
            org.ofdrw.converter.font.TrueTypeFont svgFace = FontLoader.getInstance()
                    .loadExternalFont(ttc.toString(), "宋体", "SimSun");
            assertTrue(svgFace.getUnicodeCmapLookup().getGlyphId('中') != 0);
            TrueTypeFont face = (TrueTypeFont) getFont.invoke(maker, requested, ttc, true);
            assertTrue(face.getUnicodeCmapLookup().getGlyphId('中') != 0);
            PDType0Font font = PDType0Font.load(pdf, face, true);
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("中文");
                stream.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            pdf.save(output);
            try (PDDocument saved = PDDocument.load(output.toByteArray())) {
                assertTrue(new PDFTextStripper().getText(saved).contains("中文"));
            }
        }
    }
}
