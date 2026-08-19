package org.ofdrw.core.text.font;

import java.util.Locale;
import java.util.function.IntPredicate;

/**
 * Converts legacy Microsoft Symbol private-use code points to Unicode.
 *
 * <p>Microsoft Symbol fonts commonly expose Symbol Encoding byte values at
 * {@code U+F000 + byte}. The mappings below are the unambiguous characters
 * used by the compatibility documents and follow the Unicode Consortium's
 * Adobe Symbol Encoding mapping.</p>
 */
public final class SymbolPuaMapper {

    private SymbolPuaMapper() {
    }

    /**
     * Convert legacy Symbol PUA characters to their Unicode semantics.
     *
     * @param font font declaration associated with the text
     * @param text text to convert
     * @return converted text, or the original value for a non-Symbol font
     */
    public static String toUnicode(CT_Font font, String text) {
        return map(font, text, null);
    }

    /**
     * Convert only the legacy characters that the selected rendering font
     * cannot display in their original private-use form.
     *
     * @param font font declaration associated with the text
     * @param text text to convert
     * @param supportsOriginal reports whether the rendering font contains the
     *                         original PUA code point
     * @return text suitable for the selected rendering font
     */
    public static String forRendering(CT_Font font, String text, IntPredicate supportsOriginal) {
        return map(font, text, supportsOriginal);
    }

    /**
     * Determine whether a font declaration denotes the legacy Symbol family.
     *
     * @param font font declaration
     * @return true for known Symbol font names
     */
    public static boolean isSymbolFont(CT_Font font) {
        if (font == null) {
            return false;
        }
        return isSymbolName(font.getFontName()) || isSymbolName(font.getFamilyName());
    }

    private static String map(CT_Font font, String text, IntPredicate supportsOriginal) {
        if (text == null || text.isEmpty() || !isSymbolFont(font)) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int unicode = toUnicodeCodePoint(codePoint);
            if (unicode != codePoint
                    && supportsOriginal != null
                    && supportsOriginal.test(codePoint)) {
                result.appendCodePoint(codePoint);
            } else {
                result.appendCodePoint(unicode);
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static boolean isSymbolName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.replace(" ", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return "symbol".equals(normalized)
                || "symbolmt".equals(normalized)
                || "symbolstd".equals(normalized)
                || "symbolpsmt".equals(normalized);
    }

    private static int toUnicodeCodePoint(int codePoint) {
        switch (codePoint) {
            case 0xF02B:
                return 0x002B; // PLUS SIGN
            case 0xF02D:
                return 0x2212; // MINUS SIGN
            case 0xF03D:
                return 0x003D; // EQUALS SIGN
            case 0xF05B:
                return 0x005B; // LEFT SQUARE BRACKET
            case 0xF05D:
                return 0x005D; // RIGHT SQUARE BRACKET
            case 0xF062:
                return 0x03B2; // GREEK SMALL LETTER BETA
            case 0xF0B6:
                return 0x2202; // PARTIAL DIFFERENTIAL
            case 0xF0CF:
                return 0x2209; // NOT AN ELEMENT OF
            default:
                return codePoint;
        }
    }
}
