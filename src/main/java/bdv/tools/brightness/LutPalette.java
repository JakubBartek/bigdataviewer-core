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
package bdv.tools.brightness;

import net.imglib2.display.ColorTable;

/**
 * A {@link ColorTable} defined by an arbitrary number of RGBA control points,
 * each at a normalized position in [0, 1]. Colors between control points are
 * obtained by linear interpolation, so the table is not tied to any fixed
 * resolution (unlike e.g. {@link net.imglib2.display.ColorTable8}, which
 * always has exactly 256 fixed, non-interpolated entries).
 * <p>
 * This allows palettes to be stored compactly (e.g. a handful of stops for a
 * qualitative/categorical palette) as well as at arbitrarily high resolution,
 * while always producing a smooth lookup.
 */
public class LutPalette implements ColorTable
{
	private final double[] positions;

	private final double[] red;

	private final double[] green;

	private final double[] blue;

	private final double[] alpha;

	/**
	 * @param positions control point positions in [0, 1], sorted ascending
	 * @param red control point red components in [0, 1], one per position
	 * @param green control point green components in [0, 1], one per position
	 * @param blue control point blue components in [0, 1], one per position
	 * @param alpha control point alpha components in [0, 1], one per position
	 */
	public LutPalette( final double[] positions, final double[] red, final double[] green, final double[] blue, final double[] alpha )
	{
		if ( positions.length < 2 || red.length != positions.length || green.length != positions.length
				|| blue.length != positions.length || alpha.length != positions.length )
			throw new IllegalArgumentException( "need at least 2 control points, with matching component array lengths" );
		this.positions = positions;
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
	}

	@Override
	public int lookupARGB( final double min, final double max, final double value )
	{
		final double span = max - min;
		final double t = span > 0 ? Math.max( 0.0, Math.min( 1.0, ( value - min ) / span ) ) : 0.0;
		return pack( interpolate( red, t ), interpolate( green, t ), interpolate( blue, t ), interpolate( alpha, t ) );
	}

	/**
	 * Look up a color from {@code lut}, honoring {@code matching}: for
	 * {@link ValueMatching#INTERPOLATE} this is the same as
	 * {@link ColorTable#lookupARGB(double, double, double)}, blending
	 * smoothly between control points; for {@link ValueMatching#ROUND} or
	 * {@link ValueMatching#TRUNCATE} it instead snaps to a single control
	 * point's exact color, with no blending -- consistent with how those
	 * modes make the mapping curve itself step rather than interpolate.
	 * <p>
	 * Only has an effect for {@code lut} instances that are actually a
	 * {@link LutPalette}; any other {@link ColorTable} (e.g. the default
	 * {@link net.imglib2.display.ColorTable8}) always behaves as if
	 * {@link ValueMatching#INTERPOLATE} were selected.
	 */
	public static int lookupARGB( final ColorTable lut, final double min, final double max, final double value, final ValueMatching matching )
	{
		if ( matching == ValueMatching.INTERPOLATE || !( lut instanceof LutPalette ) )
			return lut.lookupARGB( min, max, value );
		return ( ( LutPalette ) lut ).lookupARGBStepped( min, max, value, matching );
	}

	private int lookupARGBStepped( final double min, final double max, final double value, final ValueMatching matching )
	{
		final double span = max - min;
		final double t = span > 0 ? Math.max( 0.0, Math.min( 1.0, ( value - min ) / span ) ) : 0.0;
		final int idx = stepIndex( t, matching );
		return pack( red[ idx ], green[ idx ], blue[ idx ], alpha[ idx ] );
	}

	/**
	 * The normalized positions (in [0, 1]) of {@code lut}'s colors, in order.
	 * For a {@link LutPalette} these are its actual control point positions,
	 * which are not necessarily evenly spaced (e.g. a categorical palette may
	 * deliberately cluster some colors closer together than others); for any
	 * other {@link ColorTable} (e.g. the fixed-256-entry
	 * {@link net.imglib2.display.ColorTable8}) they are assumed evenly
	 * spaced, since that is how such tables are always laid out.
	 */
	public static double[] colorPositions( final ColorTable lut )
	{
		if ( lut instanceof LutPalette )
			return ( ( LutPalette ) lut ).positions.clone();

		final int n = Math.max( 1, lut.getLength() );
		final double[] positions = new double[ n ];
		for ( int i = 0; i < n; i++ )
			positions[ i ] = n > 1 ? i / ( double ) ( n - 1 ) : 0.0;
		return positions;
	}

	/**
	 * The normalized position (in [0, 1]) that {@code t} snaps to under
	 * {@code matching} -- i.e. the position of whichever control point
	 * {@link #lookupARGB(ColorTable, double, double, double, ValueMatching)}
	 * would pick, without needing to actually resolve a color. Useful to
	 * visualize where the mapping curve "steps" for Round/Truncate, at the
	 * palette's own resolution rather than the curve's control points.
	 * <p>
	 * Returns {@code t} unchanged for {@link ValueMatching#INTERPOLATE}, or
	 * when {@code lut} is not actually a {@link LutPalette}.
	 */
	public static double steppedPosition( final ColorTable lut, final double t, final ValueMatching matching )
	{
		if ( matching == ValueMatching.INTERPOLATE || !( lut instanceof LutPalette ) )
			return t;
		final LutPalette palette = ( LutPalette ) lut;
		return palette.positions[ palette.stepIndex( t, matching ) ];
	}

	/**
	 * The control point used for step (non-interpolated) lookup.
	 * {@link ValueMatching#TRUNCATE} holds the color of the last control
	 * point at or before {@code t}; {@link ValueMatching#ROUND} uses
	 * whichever control point is nearest to {@code t}.
	 */
	private int stepIndex( final double t, final ValueMatching matching )
	{
		if ( matching == ValueMatching.TRUNCATE )
		{
			// t typically comes from a value that was already rounded to an
			// integer LUT index in [0, 255] upstream (see
			// MappingModel#mapToLutIndex), which can round *down* by up to
			// 0.5/255. Without slack here, that alone -- with no actual
			// intent behind it -- can make TRUNCATE floor to the *previous*
			// control point instead of landing exactly on the intended one,
			// silently dropping colors from the cycle. ROUND does not need
			// this: its nearest-point search already tolerates much more
			// than this amount of noise.
			final double eps = 0.5 / 255.0 + 1e-9;
			int idx = 0;
			for ( int i = 0; i < positions.length; i++ )
				if ( positions[ i ] <= t + eps )
					idx = i;
			return idx;
		}

		// ROUND: nearest control point
		int bestIdx = 0;
		double bestDist = Double.MAX_VALUE;
		for ( int i = 0; i < positions.length; i++ )
		{
			final double dist = Math.abs( positions[ i ] - t );
			if ( dist < bestDist )
			{
				bestDist = dist;
				bestIdx = i;
			}
		}
		return bestIdx;
	}

	@Override
	public int getComponentCount()
	{
		return 4;
	}

	@Override
	public int getLength()
	{
		return positions.length;
	}

	@Override
	public int get( final int component, final int index )
	{
		final double[] channel = channel( component );
		return to8( channel[ Math.max( 0, Math.min( channel.length - 1, index ) ) ] );
	}

	@Override
	public int getResampled( final int component, final int length, final int index )
	{
		final double t = length > 1 ? index / ( double ) ( length - 1 ) : 0.0;
		return to8( interpolate( channel( component ), t ) );
	}

	private double[] channel( final int component )
	{
		switch ( component )
		{
			case RED:
				return red;
			case GREEN:
				return green;
			case BLUE:
				return blue;
			case ALPHA:
				return alpha;
			default:
				throw new IllegalArgumentException( "invalid component: " + component );
		}
	}

	/**
	 * Linearly interpolate {@code channel} (parallel to {@link #positions}) at
	 * normalized position {@code t} in [0, 1].
	 */
	private double interpolate( final double[] channel, final double t )
	{
		final int n = positions.length;
		if ( t <= positions[ 0 ] )
			return channel[ 0 ];
		if ( t >= positions[ n - 1 ] )
			return channel[ n - 1 ];

		int hi = 1;
		while ( hi < n - 1 && positions[ hi ] < t )
			hi++;
		final int lo = hi - 1;

		final double p0 = positions[ lo ];
		final double p1 = positions[ hi ];
		final double f = p1 > p0 ? ( t - p0 ) / ( p1 - p0 ) : 0.0;
		return channel[ lo ] + f * ( channel[ hi ] - channel[ lo ] );
	}

	private static int to8( final double v )
	{
		return Math.max( 0, Math.min( 255, ( int ) Math.round( v * 255.0 ) ) );
	}

	private static int pack( final double r, final double g, final double b, final double a )
	{
		return ( to8( a ) << 24 ) | ( to8( r ) << 16 ) | ( to8( g ) << 8 ) | to8( b );
	}
}
