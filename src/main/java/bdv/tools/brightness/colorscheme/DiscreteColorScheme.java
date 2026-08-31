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
 * A color scheme with {@code N} discrete color stops and no blending between
 * them -- suited to qualitative/categorical palettes (e.g. label ids), where
 * each stop is its own distinct color rather than a point on a gradient.
 * <p>
 * {@link #getPaletteRangeLength()} is {@code N}; the valid palette-value domain is
 * the half-open interval {@code [0, N)}. A palette value is truncated
 * (floored) to the stop whose unit-wide slot it falls into: for {@code N = 3},
 * {@code 0} and {@code 0.99} both land on stop {@code 0}, {@code 2.99} lands
 * on stop {@code 2}. A value outside {@code [0, N)} (e.g. {@code -0.001} or
 * {@code 3.0} for {@code N = 3}) is not an error -- it clamps to the nearest
 * edge stop ({@code 0} or {@code N - 1}) instead.
 */
public class DiscreteColorScheme extends AbstractColorScheme
{
	public DiscreteColorScheme( final int[] argbStops )
	{
		super( argbStops );
	}

	/** See {@link AbstractColorScheme#AbstractColorScheme(Palette)}. */
	public DiscreteColorScheme( final Palette palette )
	{
		super( palette );
	}

	@Override
	public int getPaletteRangeLength()
	{
		return stops.length;
	}

	@Override
	int colorAt( final double paletteValue )
	{
		final int lastIndex = stops.length - 1;
		final int index = Math.max( 0, Math.min( lastIndex, ( int ) Math.floor( paletteValue ) ) );
		return stops[ index ];
	}
}
