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
package bdv.tools.brightness.converter;

import bdv.tools.brightness.colorscheme.IColorScheme;

/**
 * Maps a raw image value all the way to a color: {@code rawValue -> boundary
 * handling -> schemeValue -> color}. A renderer calls
 * {@link #getRGBAForRaw(double)} per pixel (see {@link ColorSchemeConverter}).
 * Raw values stay {@code double} the whole way; see
 * {@link bdv.tools.brightness.presetfunc.IPresetFunc#getSchemeValueForRaw(double)}.
 */
public interface IColorSchemeWrapper {
    /**
     * The scheme value for a raw image value, with boundary conditions applied.
     */
    double getSchemeValueForRaw(double rawValue);

    /**
     * The color for a raw image value, fully opaque; see {@link IColorScheme#getRGB(double)}.
     */
    int getRGBForRaw(double rawValue);

    /**
     * Like {@link #getRGBForRaw(double)}, but carrying the color fix's own alpha; see {@link IColorScheme#getRGBA(double)}.
     */
    int getRGBAForRaw(double rawValue);

    /**
     * The color scheme a resolved scheme value is finally looked up in.
     */
    IColorScheme getColorScheme();

    /**
     * Stretch the raw-value domain to {@code [min, max]}, keeping the color
     * scheme and shape. A {@code StepPresetFunc} only honours {@code min}; its width comes
     * from its step size.
     *
     * @throws IllegalArgumentException if {@code max} is not strictly greater than {@code min}.
     */
    void setRawDomain(double min, double max);
}
