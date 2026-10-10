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

/**
 * Evenly spaced packed-ARGB color fixes, turning a scheme value (a position
 * in those fixes, e.g. 2.5) into a color. Knows nothing about raw image
 * values; {@link bdv.tools.brightness.converter.IColorSchemeWrapper} maps those.
 *
 * @see DiscreteColorScheme
 * @see ContinuousColorScheme
 */
public interface IColorScheme {
    /**
     * The packed-ARGB color at {@code schemeValue}, forced opaque. Values
     * outside the domain clamp to the nearest edge fix.
     */
    int getRGB(double schemeValue);

    /**
     * Like {@link #getRGB(double)}, but keeping the fix's own alpha.
     */
    int getRGBA(double schemeValue);

    /**
     * Length of the scheme-value domain, which starts at {@code 0}.
     */
    int getRange();

    /**
     * Always at least 2.
     */
    int getFixCount();

    /**
     * The packed-ARGB color fix at {@code index}.
     */
    int getFix(int index);

    /**
     * A copy of the fixes.
     */
    int[] getFixes();
}
