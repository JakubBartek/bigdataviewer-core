/*
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2026 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness.colorscheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imglib2.type.numeric.ARGBType;

/**
 * An opaque two-fix ramp from black up to one color, as for a fluorescence
 * channel. Unlike {@link LegacyBdvColorScheme} it ignores the color's alpha;
 * for an opaque color the two compare {@link #equals equal}.
 */
public final class CustomColorsScheme extends ContinuousColorScheme {
    private static final Map<String, CustomColorsScheme> CLASSICS = classicsByName();

    private final int color;

    /**
     * @param rgb packed color the ramp rises to; alpha is ignored.
     */
    public CustomColorsScheme(final int rgb) {
        super(new int[]{ARGBType.rgba(0, 0, 0, 255), opaque(rgb)});
        this.color = opaque(rgb);
    }

    /**
     * The opaque color this scheme ramps up to from black, packed ARGB.
     */
    public int getColor() {
        return color;
    }

    /**
     * The conventional single-color scales by display name, in chooser order.
     * Names must not clash case-insensitively with a bundled scheme's
     * ("gray" is bundled), since resource lookup may ignore case.
     */
    public static Map<String, CustomColorsScheme> classics() {
        return CLASSICS;
    }

    private static Map<String, CustomColorsScheme> classicsByName() {
        final Map<String, CustomColorsScheme> classics = new LinkedHashMap<>();
        classics.put("Grayscale", new CustomColorsScheme(ARGBType.rgba(255, 255, 255, 255)));
        classics.put("Red", new CustomColorsScheme(ARGBType.rgba(255, 0, 0, 255)));
        classics.put("Green", new CustomColorsScheme(ARGBType.rgba(0, 255, 0, 255)));
        classics.put("Blue", new CustomColorsScheme(ARGBType.rgba(0, 0, 255, 255)));
        classics.put("Cyan", new CustomColorsScheme(ARGBType.rgba(0, 255, 255, 255)));
        classics.put("Magenta", new CustomColorsScheme(ARGBType.rgba(255, 0, 255, 255)));
        classics.put("Yellow", new CustomColorsScheme(ARGBType.rgba(255, 255, 0, 255)));
        classics.put("Orange", new CustomColorsScheme(ARGBType.rgba(255, 128, 0, 255)));
        return Collections.unmodifiableMap(classics);
    }

    private static int opaque(final int rgb) {
        return rgb | 0xff000000;
    }

    @Override
    public String toString() {
        return String.format("CustomColorsScheme[%06x]", color & 0xffffff);
    }
}
