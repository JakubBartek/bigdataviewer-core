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
 * Reproduces {@code net.imglib2.display.RealARGBColorConverter} through a
 * linear, clamped palette mapping: a ramp from black to one color, both stops
 * keeping that color's alpha (the old converter renders below-{@code min} as
 * {@code rgba(0, 0, 0, A)}).
 * <p>
 * Diverges from the old converter in two places:
 * <ul>
 * <li>Above {@code max} the old converter keeps scaling and clips each
 * channel separately, drifting in hue; this clamps to the color. They agree
 * for colors whose channels are all 0 or 255.</li>
 * <li>Exact {@code .5} rounding ties can land one unit apart in a channel.</li>
 * </ul>
 */
public final class LegacyBdvColorPalette extends Palette
{
	private final int color;

	/** @param argb the converter's color; its alpha is kept on both stops. */
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
