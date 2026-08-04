package org.ofdrw.converter.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class MissingGlyphFallbackExportTest {

    private static final int BULLET = 0x2022;

    @TempDir
    Path tempDir;

    @Test
    void svgUsesAnotherDocumentFontForAMissingGlyph() throws Exception {
        Path ofd = createOFD("svg.ofd");
        Path output = tempDir.resolve("svg");

        List<Path> pages;
        try (SVGExporter exporter = new SVGExporter(ofd, output)) {
            exporter.export();
            pages = exporter.getSvgFilePaths();
        }

        assertEquals(read(pages.get(1)), read(pages.get(0)));
    }

    @Test
    void imageUsesAnotherDocumentFontForAMissingGlyph() throws Exception {
        Path ofd = createOFD("image.ofd");
        Path output = tempDir.resolve("images");

        List<Path> pages;
        try (ImageExporter exporter = new ImageExporter(ofd, output)) {
            exporter.export();
            pages = exporter.getImgFilePaths();
        }

        assertImagesEqual(ImageIO.read(pages.get(1).toFile()),
                ImageIO.read(pages.get(0).toFile()));
    }

    @Test
    void iTextUsesAnotherDocumentFontForAMissingGlyph() throws Exception {
        assertPdfRendering("itext", PDFExporterIText::new);
    }

    @Test
    void pdfBoxUsesAnotherDocumentFontForAMissingGlyph() throws Exception {
        assertPdfRendering("pdfbox", PDFExporterPDFBox::new);
    }

    private void assertPdfRendering(String name, ExporterFactory factory) throws Exception {
        Path ofd = createOFD(name + ".ofd");
        Path pdf = tempDir.resolve(name + ".pdf");
        try (OFDExporter exporter = factory.create(ofd, pdf)) {
            exporter.export();
        }

        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            assertImagesEqual(renderer.renderImageWithDPI(1, 144),
                    renderer.renderImageWithDPI(0, 144));
        }
    }

    private Path createOFD(String name) throws IOException {
        Path output = tempDir.resolve(name);
        byte[] fallbackFont = resource("/font_13132_0_edit.ttf");
        byte[] missingBulletFont = removeSingletonBmpMapping(fallbackFont, BULLET);

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output))) {
            put(zip, "OFD.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:OFD xmlns:ofd=\"http://www.ofdspec.org/2016\" Version=\"1.0\" DocType=\"OFD\">"
                    + "<ofd:DocBody><ofd:DocInfo><ofd:DocID>glyph-fallback-test</ofd:DocID></ofd:DocInfo>"
                    + "<ofd:DocRoot>Doc_0/Document.xml</ofd:DocRoot></ofd:DocBody></ofd:OFD>");
            put(zip, "Doc_0/Document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:Document xmlns:ofd=\"http://www.ofdspec.org/2016\"><ofd:CommonData>"
                    + "<ofd:MaxUnitID>20</ofd:MaxUnitID>"
                    + "<ofd:PageArea><ofd:PhysicalBox>0 0 40 40</ofd:PhysicalBox></ofd:PageArea>"
                    + "<ofd:PublicRes>PublicRes.xml</ofd:PublicRes></ofd:CommonData><ofd:Pages>"
                    + "<ofd:Page ID=\"1\" BaseLoc=\"Pages/Page_0/Content.xml\"/>"
                    + "<ofd:Page ID=\"2\" BaseLoc=\"Pages/Page_1/Content.xml\"/>"
                    + "</ofd:Pages></ofd:Document>");
            put(zip, "Doc_0/PublicRes.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ofd:Res xmlns:ofd=\"http://www.ofdspec.org/2016\" BaseLoc=\"Res\">"
                    + "<ofd:Fonts>"
                    + "<ofd:Font ID=\"10\" FontName=\"MissingBullet\" FamilyName=\"MissingBullet\">"
                    + "<ofd:FontFile>missing.ttf</ofd:FontFile></ofd:Font>"
                    + "<ofd:Font ID=\"11\" FontName=\"FallbackBullet\" FamilyName=\"FallbackBullet\">"
                    + "<ofd:FontFile>fallback.ttf</ofd:FontFile></ofd:Font>"
                    + "</ofd:Fonts></ofd:Res>");
            put(zip, "Doc_0/Pages/Page_0/Content.xml", textPage(3, 10));
            put(zip, "Doc_0/Pages/Page_1/Content.xml", textPage(5, 11));
            put(zip, "Doc_0/Res/missing.ttf", missingBulletFont);
            put(zip, "Doc_0/Res/fallback.ttf", fallbackFont);
        }
        return output;
    }

    private static String textPage(int id, int font) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ofd:Page xmlns:ofd=\"http://www.ofdspec.org/2016\"><ofd:Content>"
                + "<ofd:Layer ID=\"" + (id + 1) + "\"><ofd:TextObject ID=\"" + id + "\" "
                + "Boundary=\"5 5 30 25\" Font=\"" + font + "\" Size=\"10\">"
                + "<ofd:TextCode X=\"10\" Y=\"15\">&#x2022;</ofd:TextCode>"
                + "</ofd:TextObject></ofd:Layer></ofd:Content></ofd:Page>";
    }

    private static byte[] removeSingletonBmpMapping(byte[] source, int codePoint) {
        byte[] font = source.clone();
        int tableCount = readUnsignedShort(font, 4);
        int cmap = -1;
        for (int i = 0; i < tableCount; i++) {
            int record = 12 + i * 16;
            if (readInt(font, record) == 0x636D6170) { // cmap
                cmap = readInt(font, record + 8);
                break;
            }
        }
        if (cmap < 0) {
            throw new IllegalArgumentException("Test font has no cmap table");
        }

        int encodingCount = readUnsignedShort(font, cmap + 2);
        for (int i = 0; i < encodingCount; i++) {
            int subtable = cmap + readInt(font, cmap + 4 + i * 8 + 4);
            if (readUnsignedShort(font, subtable) != 4) {
                continue;
            }
            int segmentCount = readUnsignedShort(font, subtable + 6) / 2;
            int ends = subtable + 14;
            int starts = ends + segmentCount * 2 + 2;
            int deltas = starts + segmentCount * 2;
            int rangeOffsets = deltas + segmentCount * 2;
            for (int segment = 0; segment < segmentCount; segment++) {
                int start = readUnsignedShort(font, starts + segment * 2);
                int end = readUnsignedShort(font, ends + segment * 2);
                int rangeOffset = readUnsignedShort(font, rangeOffsets + segment * 2);
                if (start == codePoint && end == codePoint && rangeOffset == 0) {
                    writeUnsignedShort(font, deltas + segment * 2, -codePoint);
                    return font;
                }
            }
        }
        throw new IllegalArgumentException("Test font has no singleton mapping for U+"
                + Integer.toHexString(codePoint));
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 8 | bytes[offset + 1] & 0xFF;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24
                | (bytes[offset + 1] & 0xFF) << 16
                | (bytes[offset + 2] & 0xFF) << 8
                | bytes[offset + 3] & 0xFF;
    }

    private static void writeUnsignedShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
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
        try (InputStream input = MissingGlyphFallbackExportTest.class.getResourceAsStream(name)) {
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

    @FunctionalInterface
    private interface ExporterFactory {
        OFDExporter create(Path ofd, Path pdf) throws IOException;
    }
}
