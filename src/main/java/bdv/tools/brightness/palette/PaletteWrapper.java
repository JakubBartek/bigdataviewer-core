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
 * handling -> paletteValue -> color}. The public face of
 * {@link PresetPaletteWrapper} (raw value -> palette value via a
 * {@code PresetFunc}, then a color scheme), kept as an interface so a renderer
 * can depend on the mapping without the concrete composition.
 * <p>
 * This is the seam a renderer plugs into: it holds a {@code PaletteWrapper}
 * and calls {@link #getRGBAForRaw(double)} per pixel (see {@code PaletteConverter}).
 * Raw values travel as {@code double} the whole way: see
 * {@link bdv.tools.brightness.presetfunc.PresetFunc#getPaletteValueForRaw(double)}
 * for why nothing on this path may narrow to {@code float}.
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
	double getPaletteValueForRaw( double rawValue );

	/** The color for a raw image value, fully opaque; see {@link ColorScheme#getRGB(double)}. */
	int getRGBForRaw( double rawValue );

	/** Like {@link #getRGBForRaw(double)}, but carrying the color stop's own alpha; see {@link ColorScheme#getRGBA(double)}. */
	int getRGBAForRaw( double rawValue );

	/** The color scheme a resolved palette value is finally looked up in. */
	ColorScheme getColorScheme();

	/**
	 * Stretch this wrapper's raw-value domain to {@code [min, max]}, i.e. make
	 * {@code min} map to palette value {@code 0} and {@code max} to the end of
	 * the palette. This is how a display-range (brightness/contrast) change
	 * reaches the mapping, without disturbing the palette or the chosen shape.
	 * <p>
	 * A {@code StepPresetFunc} (the discrete path) honours {@code min} but
	 * derives its own maximum from its step size, since that step size is a
	 * quantity in raw units that a range change is not supposed to rescale -- so
	 * for a discrete mapping this moves where the palette starts and leaves how
	 * wide each color band is alone. See that class's javadoc.
	 *
	 * @throws IllegalArgumentException if {@code max} is not strictly greater than {@code min}.
	 */
	void setRawDomain( double min, double max );
}
