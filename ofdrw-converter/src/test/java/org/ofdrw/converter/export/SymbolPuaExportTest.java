package org.ofdrw.converter.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.reader.ContentExtractor;
import org.ofdrw.reader.OFDReader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolPuaExportTest {

    private static final String UNICODE_TEXT = "=+\u03B2[]";

    @TempDir
    Path tempDir;

    @Test
    void contentExtractorReturnsUnicodeForLegacySymbolPua() throws Exception {
        Path ofd = createSymbolOFD("extract.ofd");

        try (OFDReader reader = new OFDReader(ofd)) {
            List<String> content = new ContentExtractor(reader).getPageContent(1);
            assertEquals(UNICODE_TEXT, content.get(0));
        }
    }

    @Test
    void svgUsesUnicodeGlyphsWhenTheFontDoesNotContainSymbolPua() throws Exception {
        Path ofd = createSymbolOFD("svg.ofd");
        Path output = tempDir.resolve("svg");

        List<Path> pages;
        try (SVGExporter exporter = new SVGExporter(ofd, output)) {
            exporter.export();
            pages = exporter.getSvgFilePaths();
        }

        assertEquals(read(pages.get(1)), read(pages.get(0)));
    }

    @Test
    void imageUsesUnicodeGlyphsWhenTheFontDoesNotContainSymbolPua() throws Exception {
        Path ofd = createSymbolOFD("image.ofd");
        Path output = tempDir.resolve("images");

        List<Path> pages;
        try (ImageExporter exporter = new ImageExporter(ofd, output)) {
            exporter.export();
            pages = exporter.getImgFilePaths();
        }

        assertImagesEqual(ImageIO.read(pages.get(0).toFile()), ImageIO.read(pages.get(1).toFile()));
    }

    @Test
    void htmlUsesUnicodeForVisualAndSearchableText() throws Exception {
        Path ofd = createSymbolOFD("html.ofd");
        Path output = tempDir.resolve("symbol.html");

        try (HTMLExporter exporter = new HTMLExporter(ofd, output)) {
            exporter.export();
        }

        String html = read(output);
        for (int i = 0; i < UNICODE_TEXT.length(); i++) {
            assertTrue(html.contains(String.valueOf(UNICODE_TEXT.charAt(i))),
                    "HTML does not contain mapped character U+"
                            + String.format("%04X", (int) UNICODE_TEXT.charAt(i)));
        }
        String puaText = "\uF03D\uF02B\uF062\uF05B\uF05D";
        for (int i = 0; i < puaText.length(); i++) {
            assertFalse(html.contains(String.valueOf(puaText.charAt(i))),
                    "HTML still contains legacy Symbol character U+"
                            + String.format("%04X", (int) puaText.charAt(i)));
        }
    }

    @Test
    void iTextPdfRendersUnicodeWhenTheFontDoesNotContainSymbolPua() throws Exception {
        assertPdfRendering("itext", new ExporterFactory() {
            @Override
            public OFDExporter create(Path ofd, Path pdf) throws IOException {
                return new PDFExporterIText(ofd, pdf);
            }
        });
    }

    @Test
    void pdfBoxRendersUnicodeWhenTheFontDoesNotContainSymbolPua() throws Exception {
        assertPdfRendering("pdfbox", new ExporterFactory() {
            @Override
            public OFDExporter create(Path ofd, Path pdf) throws IOException {
                return new PDFExporterPDFBox(ofd, pdf);
            }
        });
    }

    private void assertPdfRendering(String name, ExporterFactory factory) throws Exception {
        Path ofd = createSymbolOFD(name + ".ofd");
        Path pdf = tempDir.resolve(name + ".pdf");
        try (OFDExporter exporter = factory.create(ofd, pdf)) {
            exporter.export();
        }

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage legacySymbolPage = renderer.renderImageWithDPI(0, 144);
            BufferedImage unicodeReferencePage = renderer.renderImageWithDPI(1, 144);
            assertImagesEqual(legacySymbolPage, unicodeReferencePage);
        }
    }

    private Path createSymbolOFD(String name) throws IOException {
        Path output = tempDir.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            put(zip, "OFD.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:OFD xmlns:ofd=\"http://www.ofdspec.org/2016\" Version=\"1.0\" DocType=\"OFD\">"
                    + "<ofd:DocBody><ofd:DocInfo><ofd:DocID>symbol-pua-test</ofd:DocID></ofd:DocInfo>"
                    + "<ofd:DocRoot>Doc_0/Document.xml</ofd:DocRoot></ofd:DocBody></ofd:OFD>");
            put(zip, "Doc_0/Document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:Document xmlns:ofd=\"http://www.ofdspec.org/2016\"><ofd:CommonData>"
                    + "<ofd:MaxUnitID>20</ofd:MaxUnitID>"
                    + "<ofd:PageArea><ofd:PhysicalBox>0 0 80 40</ofd:PhysicalBox></ofd:PageArea>"
                    + "<ofd:PublicRes>PublicRes.xml</ofd:PublicRes></ofd:CommonData><ofd:Pages>"
                    + "<ofd:Page ID=\"1\" BaseLoc=\"Pages/Page_0/Content.xml\"/>"
                    + "<ofd:Page ID=\"2\" BaseLoc=\"Pages/Page_1/Content.xml\"/>"
                    + "</ofd:Pages></ofd:Document>");
            put(zip, "Doc_0/PublicRes.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:Res xmlns:ofd=\"http://www.ofdspec.org/2016\" BaseLoc=\"Res\">"
                    + "<ofd:Fonts>"
                    + "<ofd:Font ID=\"10\" FontName=\"Symbol\" FamilyName=\"Symbol\">"
                    + "<ofd:FontFile>font.ttf</ofd:FontFile></ofd:Font>"
                    + "<ofd:Font ID=\"11\" FontName=\"sysfST\" FamilyName=\"sysfST\">"
                    + "<ofd:FontFile>font.ttf</ofd:FontFile></ofd:Font>"
                    + "</ofd:Fonts></ofd:Res>");
            put(zip, "Doc_0/Pages/Page_0/Content.xml", textPage(3, 10,
                    "&#xF03D;&#xF02B;&#xF062;&#xF05B;&#xF05D;"));
            put(zip, "Doc_0/Pages/Page_1/Content.xml", textPage(5, 11, UNICODE_TEXT));
            put(zip, "Doc_0/Res/font.ttf", resource("/font_13132_0_edit.ttf"));
        }
        return output;
    }

    private static String textPage(int id, int font, String content) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ofd:Page xmlns:ofd=\"http://www.ofdspec.org/2016\"><ofd:Content>"
                + "<ofd:Layer ID=\"" + (id + 1) + "\"><ofd:TextObject ID=\"" + id + "\" "
                + "Boundary=\"5 5 70 25\" Font=\"" + font + "\" Size=\"10\">"
                + "<ofd:TextCode X=\"0\" Y=\"15\" DeltaX=\"10 10 10 10\">"
                + content + "</ofd:TextCode></ofd:TextObject></ofd:Layer></ofd:Content></ofd:Page>";
    }

    private static void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y),
                        "Rendered pages differ at (" + x + ", " + y + ")");
            }
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = SymbolPuaExportTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        put(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private interface ExporterFactory {
        OFDExporter create(Path ofd, Path pdf) throws IOException;
    }
}
