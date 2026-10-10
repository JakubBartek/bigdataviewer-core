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

import net.imglib2.type.numeric.ARGBType;

/**
 * Shared color-fix storage for {@link DiscreteColorScheme} and
 * {@link ContinuousColorScheme}, which differ only in {@link #colorAt(double)}
 * and {@link IColorScheme#getRange()}.
 */
abstract class AbstractColorScheme implements IColorScheme {
    /**
     * Color fixes, packed ARGB (see {@link ARGBType#rgba(int, int, int, int)}); always at least 2.
     */
    final int[] fixes;

    /**
     * @param argbFixes copied, so a scheme is immutable and can be shared.
     */
    AbstractColorScheme(final int[] argbFixes) {
        if (argbFixes.length < 2) {
            throw new IllegalArgumentException("a color scheme needs at least 2 color fixes, got " + argbFixes.length);
        }
        this.fixes = argbFixes.clone();
    }

    @Override
    public final int getRGBA(final double schemeValue) {
        return colorAt(schemeValue);
    }

    @Override
    public final int getRGB(final double schemeValue) {
        return colorAt(schemeValue) | 0xff000000;
    }

    @Override
    public final int getFixCount() {
        return fixes.length;
    }

    @Override
    public final int getFix(final int index) {
        return fixes[index];
    }

    @Override
    public final int[] getFixes() {
        return fixes.clone();
    }

    final boolean hasSameFixes(final AbstractColorScheme other) {
        return Arrays.equals(fixes, other.fixes);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + fixes.length + " fixes]";
    }

    /**
     * The packed-ARGB color at {@code schemeValue}, clamped to the nearest edge fix.
     */
    abstract int colorAt(double schemeValue);

    /**
     * Linearly interpolates each channel independently between two packed-ARGB fixes, {@code t} in {@code [0, 1]}.
     */
    static int interpolateColor(final int fromARGB, final int toARGB, final double t) {
        final int r = interpolateValue(ARGBType.red(fromARGB), ARGBType.red(toARGB), t);
        final int g = interpolateValue(ARGBType.green(fromARGB), ARGBType.green(toARGB), t);
        final int b = interpolateValue(ARGBType.blue(fromARGB), ARGBType.blue(toARGB), t);
        final int a = interpolateValue(ARGBType.alpha(fromARGB), ARGBType.alpha(toARGB), t);
        return ARGBType.rgba(r, g, b, a);
    }

    private static int interpolateValue(final int from, final int to, final double t) {
        return (int) Math.round(from + t * (to - from));
    }
}
