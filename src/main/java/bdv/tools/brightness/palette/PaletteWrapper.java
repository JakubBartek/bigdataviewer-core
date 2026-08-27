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
package bdv.tools.brightness.palette;

import bdv.tools.brightness.colorscheme.ColorScheme;

/**
 * Maps a raw image value all the way to a color: {@code rawValue -> boundary
 * handling -> paletteValue -> color}. The common public face of
 * {@link DiscretePaletteWrapper} and {@link ContinuousPaletteWrapper} -- the
 * two differ only in how a raw value becomes a palette value (a discrete step
 * count vs. a {@code PresetFunc}), which a caller that just wants a color for a
 * pixel does not need to know.
 * <p>
 * This is the seam a renderer plugs into: it holds a {@code PaletteWrapper}
 * and calls {@link #getRGBForRaw(float)} per pixel, without caring which of the
 * two concrete kinds it was handed (see {@code PaletteConverter}).
 */
public interface PaletteWrapper
{
	/**
	 * The palette value for a raw image value: the wrapper's boundary
	 * conditions applied if the value is outside the domain, otherwise the
	 * plain {@code rawValue -> paletteValue} conversion. Feed this to
	 * {@link #getColorScheme()} to get a color, or use the {@code *ForRaw}
	 * shortcuts below.
	 */
	float getPaletteValueForRaw( float rawValue );

	/** The color for a raw image value, fully opaque; see {@link ColorScheme#getRGB(float)}. */
	int getRGBForRaw( float rawValue );

	/** Like {@link #getRGBForRaw(float)}, but carrying the color stop's own alpha; see {@link ColorScheme#getRGBA(float)}. */
	int getRGBAForRaw( float rawValue );

	/** The color scheme a resolved palette value is finally looked up in. */
	ColorScheme getColorScheme();

	/**
	 * Stretch this wrapper's raw-value domain to {@code [min, max]}, i.e. make
	 * {@code min} map to palette value {@code 0} and {@code max} to the end of
	 * the palette. This is how a display-range (brightness/contrast) change
	 * reaches the mapping, uniformly for both kinds: the discrete wrapper
	 * adjusts its step size, the continuous one re-ranges its {@code PresetFunc}
	 * -- neither disturbs the palette or (for continuous) the chosen shape.
	 *
	 * @throws IllegalArgumentException if {@code max} is not strictly greater than {@code min}.
	 */
	void setRawDomain( double min, double max );
}
