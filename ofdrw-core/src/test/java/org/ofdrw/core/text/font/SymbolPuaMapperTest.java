package org.ofdrw.core.text.font;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolPuaMapperTest {

    private final CT_Font symbol = new CT_Font("Symbol").setFamilyName("Symbol");

    @Test
    void convertsKnownSymbolPuaCharactersToUnicode() {
        assertEquals("=+\u03B2\u2202[]\u2209\u2212",
                SymbolPuaMapper.toUnicode(symbol,
                        "\uF03D\uF02B\uF062\uF0B6\uF05B\uF05D\uF0CF\uF02D"));
    }

    @Test
    void doesNotApplyAHexOffsetToUnknownCharacters() {
        assertEquals("\uF061", SymbolPuaMapper.toUnicode(symbol, "\uF061"));
    }

    @Test
    void ignoresPrivateUseCharactersFromOtherFonts() {
        CT_Font other = new CT_Font("Example").setFamilyName("Example");
        assertEquals("\uF03D", SymbolPuaMapper.toUnicode(other, "\uF03D"));
    }

    @Test
    void renderingKeepsPuaWhenTheSelectedFontContainsItsGlyph() {
        assertEquals("\uF03D+",
                SymbolPuaMapper.forRendering(symbol, "\uF03D\uF02B",
                        codePoint -> codePoint == 0xF03D));
    }
}
