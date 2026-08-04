package org.ofdrw.converter.font;

import org.ofdrw.core.text.font.CT_Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Selects a document font that can render text missing from the primary font.
 *
 * @param <T> backend-specific font type
 */
public final class GlyphFallbackResolver<T> {

    private final List<CT_Font> candidates;
    private final FontLoader<T> loader;
    private final GlyphChecker<T> checker;
    private final Map<String, T> resolved = new HashMap<>();
    private final Set<String> unresolved = new HashSet<>();

    public GlyphFallbackResolver(List<CT_Font> fonts,
                                 FontLoader<T> loader,
                                 GlyphChecker<T> checker) {
        this.candidates = new ArrayList<>(fonts);
        this.candidates.sort(Comparator
                .comparing((CT_Font font) -> font.getFontFile() == null)
                .thenComparingLong(font -> font.getID().getId()));
        this.loader = loader;
        this.checker = checker;
    }

    /**
     * Return the primary font when it supports the complete text; otherwise
     * return the first declared document font that supports every code point.
     * If no fallback is available, the primary font is preserved.
     */
    public T resolve(String text, T primary) {
        if (text == null || text.isEmpty() || supports(primary, text)) {
            return primary;
        }
        if (resolved.containsKey(text)) {
            return resolved.get(text);
        }
        if (unresolved.contains(text)) {
            return primary;
        }

        for (CT_Font declaration : candidates) {
            try {
                T candidate = loader.load(declaration);
                if (candidate != null && candidate != primary && supports(candidate, text)) {
                    resolved.put(text, candidate);
                    return candidate;
                }
            } catch (Exception ignored) {
                // Try the next declared font.
            }
        }
        unresolved.add(text);
        return primary;
    }

    private boolean supports(T font, String text) {
        if (font == null) {
            return false;
        }
        try {
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                if (!checker.hasGlyph(font, codePoint)) {
                    return false;
                }
                offset += Character.charCount(codePoint);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @FunctionalInterface
    public interface FontLoader<T> {
        T load(CT_Font font) throws Exception;
    }

    @FunctionalInterface
    public interface GlyphChecker<T> {
        boolean hasGlyph(T font, int codePoint) throws Exception;
    }
}
