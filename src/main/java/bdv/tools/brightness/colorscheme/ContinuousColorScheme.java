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

import net.imglib2.display.ColorTable;

/**
 * A color scheme with {@code N} color stops, linearly interpolated between
 * neighboring stops -- suited to continuous palettes (e.g. viridis), where
 * colors between stops are smoothly blended rather than each stop standing on
 * its own.
 * <p>
 * {@link #getDelkaIntervalu()} is {@code N - 1}: {@code N} stops have only
 * {@code N - 1} gaps between them. The valid palette-value domain is the
 * closed interval {@code [0, N - 1]} -- e.g. for {@code N = 3}: {@code 0} is
 * stop {@code 0}, {@code 2.0} is stop {@code 2} (the last one), and
 * {@code 0.99} blends 99% of the way from stop {@code 0} to stop {@code 1}. A
 * value outside {@code [0, N - 1]} (e.g. {@code -0.001} or {@code 2.01} for
 * {@code N = 3}) is not an error -- it clamps to the nearest edge stop
 * instead.
 */
public class ContinuousColorScheme extends AbstractColorScheme
{
	public ContinuousColorScheme( final int[] argbStops )
	{
		super( argbStops );
	}

	/** See {@link AbstractColorScheme#AbstractColorScheme(ColorTable)}. */
	public ContinuousColorScheme( final ColorTable colorTable )
	{
		super( colorTable );
	}

	@Override
	public int getDelkaIntervalu()
	{
		return stops.length - 1;
	}

	@Override
	int colorAt( final float paletteValue )
	{
		final int lastIndex = stops.length - 1;
		final float clamped = Math.max( 0f, Math.min( ( float ) lastIndex, paletteValue ) );
		final int index = Math.min( lastIndex - 1, ( int ) Math.floor( clamped ) );
		final float frac = clamped - index;
		return interpolateColor( stops[ index ], stops[ index + 1 ], frac );
	}
}
