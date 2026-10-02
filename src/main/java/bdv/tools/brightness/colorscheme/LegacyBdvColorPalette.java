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

import net.imglib2.type.numeric.ARGBType;

/**
 * The palette a classic single-color BigDataViewer converter
 * ({@code net.imglib2.display.RealARGBColorConverter}) renders with: a
 * two-stop ramp from black up to one color, both stops carrying that color's
 * alpha.
 * <p>
 * That converter scales each channel of its color by
 * {@code (raw - min) / (max - min)}, and renders {@code rgba(0, 0, 0, A)} --
 * black at the color's own alpha, not opaque black -- below {@code min}. Read
 * through a {@link ContinuousColorScheme} and a linear {@code PresetFunc}
 * over the same display range, with both boundaries clamped, this palette
 * reproduces that inside the range and below it. It is what lets a source set
 * up the old way be taken over by the palette architecture without changing
 * how it looks.
 * <p>
 * Two places it cannot follow the old converter:
 * <ul>
 * <li><b>Above {@code max}.</b> The old converter does not stop at its color
 * there: it goes on scaling every channel and clips each one at 255
 * separately, so a color with a channel below 255 keeps brightening, and
 * drifts in hue, until all its non-zero channels have saturated. A palette
 * ends at its last stop, so this one clamps to the color itself. The two agree
 * for any color whose channels are each either 0 or 255 (white, the
 * primaries, cyan, magenta, yellow). Reproducing the rest would need stops at
 * each channel's own saturation point, which are not evenly spaced and lie
 * outside the display range.</li>
 * <li><b>Exact rounding ties.</b> The old converter computes each channel as
 * {@code (int) (channel / (max - min) * (raw - min) + 0.5)}; the palette path
 * normalizes the raw value first and rounds the blend with
 * {@code Math.round}. Where a channel lands exactly on a {@code .5} the two
 * operation orders can round to neighbouring values, one unit apart in that
 * channel.</li>
 * </ul>
 * The palette is {@link #isInterpolated() interpolated}: the ramp is a
 * continuous blend, not two categories.
 *
 * @author Jakub Bartek
 */
public final class LegacyBdvColorPalette extends Palette
{
	private final int color;

	/**
	 * @param argb the converter's color, packed ARGB (see
	 *             {@link ARGBType#rgba(int, int, int, int)}). Its alpha is kept
	 *             on both stops, as the old converter keeps it on every pixel.
	 */
	public LegacyBdvColorPalette( final int argb )
	{
		super( new int[] { ARGBType.rgba( 0, 0, 0, ARGBType.alpha( argb ) ), argb }, true );
		this.color = argb;
	}

	/** The color this palette ramps up to from black, packed ARGB. */
	public int getColor()
	{
		return color;
	}

	@Override
	public String toString()
	{
		return String.format( "LegacyBdvColorPalette[%08x]", color );
	}
}
