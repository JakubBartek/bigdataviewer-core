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

import java.util.Arrays;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
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
public class ColorTableLut implements ColorTable
{
	/**
	 * A black-to-white gradient, used as the placeholder whenever no real
	 * palette has been chosen yet. Deliberately a two-stop
	 * {@code ColorTableLut} rather than a {@link net.imglib2.display.ColorTable8},
	 * which always reports 256 entries. Safe to share, since a
	 * {@code ColorTableLut} exposes no mutators.
	 */
	public static final ColorTableLut DEFAULT = new ColorTableLut(
			new double[] { 0.0, 1.0 },
			new double[] { 0.0, 1.0 },
			new double[] { 0.0, 1.0 },
			new double[] { 0.0, 1.0 },
			new double[] { 1.0, 1.0 } );

	private final double[] positions;

	private final double[] red;

	private final double[] green;

	private final double[] blue;

	private final double[] alpha;

	private final boolean interpolated;

	/**
	 * Same as {@link #ColorTableLut(double[], double[], double[], double[],
	 * double[], boolean)}, defaulting {@code interpolated} to {@code true}.
	 *
	 * @param positions control point positions in [0, 1], sorted ascending
	 * @param red control point red components in [0, 1], one per position
	 * @param green control point green components in [0, 1], one per position
	 * @param blue control point blue components in [0, 1], one per position
	 * @param alpha control point alpha components in [0, 1], one per position
	 */
	public ColorTableLut( final double[] positions, final double[] red, final double[] green, final double[] blue, final double[] alpha )
	{
		this( positions, red, green, blue, alpha, true );
	}

	/**
	 * @param positions control point positions in [0, 1], sorted ascending
	 * @param red control point red components in [0, 1], one per position
	 * @param green control point green components in [0, 1], one per position
	 * @param blue control point blue components in [0, 1], one per position
	 * @param alpha control point alpha components in [0, 1], one per position
	 * @param interpolated
	 * 		whether this palette is meant to be smoothly interpolated (e.g. a
	 * 		continuous palette like viridis) rather than used as discrete,
	 * 		individually chosen colors (e.g. a qualitative/categorical palette
	 * 		like tab10); see {@link #isInterpolated(ColorTable)}. Consumed by the
	 * 		LUT editor to pick a discrete vs. continuous color scheme; it does
	 * 		not affect {@code ColorTableLut}'s own {@link #lookupARGB(double, double, double)}.
	 */
	public ColorTableLut( final double[] positions, final double[] red, final double[] green, final double[] blue, final double[] alpha, final boolean interpolated )
	{
		if ( positions.length < 2 || red.length != positions.length || green.length != positions.length
				|| blue.length != positions.length || alpha.length != positions.length )
			throw new IllegalArgumentException( "need at least 2 control points, with matching component array lengths" );
		this.positions = positions;
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
		this.interpolated = interpolated;
	}

	/**
	 * Normalizes {@code value} into {@code [0, 1]} across {@code [min, max]},
	 * then defers to {@link ContinuousColorScheme} for the actual stop lookup
	 * and blending, rather than repeating that interpolation here. Note this
	 * treats the control points as evenly spaced, same as
	 * {@link ContinuousColorScheme} always does -- unlike {@link #get(int, int)}/
	 * {@link #getResampled(int, int, int)}, this no longer respects a
	 * {@link ColorTableLut} built with unevenly spaced {@code positions}.
	 */
	@Override
	public int lookupARGB( final double min, final double max, final double value )
	{
		final double span = max - min;
		final double t = span > 0 ? Math.max( 0.0, Math.min( 1.0, ( value - min ) / span ) ) : 0.0;
		final ContinuousColorScheme scheme = new ContinuousColorScheme( this );
		return scheme.getRGBA( ( float ) ( t * scheme.getPaletteRangeLength() ) );
	}

	/**
	 * Whether this palette is meant to be smoothly interpolated, as declared
	 * at construction time (see {@link #ColorTableLut(double[], double[],
	 * double[], double[], double[], boolean)}).
	 */
	public boolean isInterpolated()
	{
		return interpolated;
	}

	/**
	 * Whether {@code other} is a {@link ColorTableLut} with exactly the same
	 * control points and interpolation flag as this one. Used to recover a
	 * loaded {@link ColorTable}'s resource name (see
	 * {@link LutPalettes#findName(ColorTable)}) when only the bare table is
	 * known, e.g. read back from a converter that was set up elsewhere.
	 */
	public boolean hasSameColors( final ColorTable other )
	{
		if ( !( other instanceof ColorTableLut ) )
			return false;
		final ColorTableLut o = ( ColorTableLut ) other;
		return interpolated == o.interpolated
				&& Arrays.equals( positions, o.positions )
				&& Arrays.equals( red, o.red )
				&& Arrays.equals( green, o.green )
				&& Arrays.equals( blue, o.blue )
				&& Arrays.equals( alpha, o.alpha );
	}

	/**
	 * Whether {@code lut} is meant to be smoothly interpolated. For a
	 * {@link ColorTableLut} this is {@link #isInterpolated()}; any other
	 * {@link ColorTable} (e.g. the default {@link net.imglib2.display.ColorTable8})
	 * has no such concept and is always treated as {@code true}.
	 */
	public static boolean isInterpolated( final ColorTable lut )
	{
		return !( lut instanceof ColorTableLut ) || ( ( ColorTableLut ) lut ).interpolated;
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
}
