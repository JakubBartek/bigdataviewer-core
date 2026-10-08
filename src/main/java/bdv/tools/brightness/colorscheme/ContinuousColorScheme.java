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
 * {@code N} color stops, linearly interpolated (e.g. viridis). The domain is
 * the closed interval {@code [0, N - 1]}: {@code N} stops have {@code N - 1}
 * gaps.
 */
public class ContinuousColorScheme extends AbstractColorScheme
{
	public ContinuousColorScheme( final int[] argbStops )
	{
		super( argbStops );
	}

	/** See {@link AbstractColorScheme#AbstractColorScheme(Palette)}. */
	public ContinuousColorScheme( final Palette palette )
	{
		super( palette );
	}

	@Override
	public int getPaletteRangeLength()
	{
		return stops.length - 1;
	}

	@Override
	int colorAt( final double paletteValue )
	{
		final int lastIndex = stops.length - 1;
		final double clamped = Math.max( 0.0, Math.min( lastIndex, paletteValue ) );
		final int index = Math.min( lastIndex - 1, ( int ) Math.floor( clamped ) );
		final double frac = clamped - index;
		return interpolateColor( stops[ index ], stops[ index + 1 ], frac );
	}
}
