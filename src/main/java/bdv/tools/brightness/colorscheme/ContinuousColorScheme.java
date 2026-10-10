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
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness.colorscheme;

import java.util.Arrays;

import net.imglib2.display.ColorTable;
import net.imglib2.type.numeric.ARGBType;

/**
 * {@code N} color fixes, linearly interpolated (e.g. viridis). The domain is
 * the closed interval {@code [0, N - 1]}: {@code N} fixes have {@code N - 1}
 * gaps.
 * <p>
 * A value: subclasses only add named constructors, and compare equal to a
 * plain scheme with the same fixes.
 */
public class ContinuousColorScheme extends AbstractColorScheme {
    /**
     * Black-to-white gradient, used until a scheme is chosen.
     */
    public static final ContinuousColorScheme DEFAULT = new ContinuousColorScheme(
            new int[]{ARGBType.rgba(0, 0, 0, 255), ARGBType.rgba(255, 255, 255, 255)});

    public ContinuousColorScheme(final int[] argbFixes) {
        super(argbFixes);
    }

    /**
     * One fix per {@code colorTable} entry. Not a {@link ColorTable} itself:
     * its {@code lookupARGB(min, max, value)} maps raw values, which could only
     * be answered here by assuming a linear transfer function.
     */
    public static ContinuousColorScheme of(final ColorTable colorTable) {
        final int n = colorTable.getLength();
        // RGB-only tables (e.g. grayscale ColorTable8) are opaque
        final boolean hasAlpha = colorTable.getComponentCount() > ColorTable.ALPHA;
        final int[] argb = new int[n];
        for (int i = 0; i < n; i++) {
            argb[i] = ARGBType.rgba(
                    colorTable.get(ColorTable.RED, i),
                    colorTable.get(ColorTable.GREEN, i),
                    colorTable.get(ColorTable.BLUE, i),
                    hasAlpha ? colorTable.get(ColorTable.ALPHA, i) : 255);
        }
        return new ContinuousColorScheme(argb);
    }

    @Override
    public int getRange() {
        return fixes.length - 1;
    }

    @Override
    int colorAt(final double schemeValue) {
        final int lastIndex = fixes.length - 1;
        final double clamped = Math.max(0.0, Math.min(lastIndex, schemeValue));
        final int index = Math.min(lastIndex - 1, (int) Math.floor(clamped));
        final double frac = clamped - index;
        return interpolateColor(fixes[index], fixes[index + 1], frac);
    }

    /**
     * {@code ColorSchemeFactory#findName} relies on this.
     */
    @Override
    public final boolean equals(final Object obj) {
        return obj instanceof ContinuousColorScheme && hasSameFixes((ContinuousColorScheme) obj);
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(fixes);
    }
}
