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

import net.imglib2.type.numeric.ARGBType;

/**
 * Packed-ARGB color arithmetic shared by the color schemes and their editors.
 * Channels are 8-bit; pack and unpack them with {@link ARGBType}.
 */
public final class ColorSchemeHelpers {
    private ColorSchemeHelpers() {
    }

    /**
     * {@code t} must be in {@code [0, 1]}: the result then stays between
     * {@code from} and {@code to}, whereas an extrapolated channel would wrap
     * when packed by {@link ARGBType#rgba(int, int, int, int)}.
     */
    public static int interpolateValue(final int from, final int to, final double t) {
        return (int) Math.round(from + t * (to - from));
    }

    /**
     * Each channel interpolated independently by {@link #interpolateValue}.
     */
    public static int interpolateRGBA(final int fromARGB, final int toARGB, final double t) {
        final int r = interpolateValue(ARGBType.red(fromARGB), ARGBType.red(toARGB), t);
        final int g = interpolateValue(ARGBType.green(fromARGB), ARGBType.green(toARGB), t);
        final int b = interpolateValue(ARGBType.blue(fromARGB), ARGBType.blue(toARGB), t);
        final int a = interpolateValue(ARGBType.alpha(fromARGB), ARGBType.alpha(toARGB), t);
        return ARGBType.rgba(r, g, b, a);
    }

    /**
     * Like {@link #interpolateRGBA}, but ignoring both alphas: the result is opaque.
     */
    public static int interpolateRGB(final int fromARGB, final int toARGB, final double t) {
        final int r = interpolateValue(ARGBType.red(fromARGB), ARGBType.red(toARGB), t);
        final int g = interpolateValue(ARGBType.green(fromARGB), ARGBType.green(toARGB), t);
        final int b = interpolateValue(ARGBType.blue(fromARGB), ARGBType.blue(toARGB), t);
        return ARGBType.rgba(r, g, b, 255);
    }

    public static int opaque(final int argb) {
        return argb | 0xff000000;
    }

    /**
     * A {@code [0, 1]} color component as an 8-bit channel, clamped.
     */
    public static int unitToChannel(final double unit) {
        return Math.max(0, Math.min(255, (int) Math.round(unit * 255.0)));
    }
}
