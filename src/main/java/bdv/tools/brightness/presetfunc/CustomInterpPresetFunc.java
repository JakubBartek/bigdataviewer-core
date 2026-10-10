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
package bdv.tools.brightness.presetfunc;

import java.util.Objects;

/**
 * A user-defined, piecewise-linear shape through knots {@code (t, value)},
 * both normalized to {@code [0, 1]} (domain fraction and scheme-value
 * fraction). Flat outside the outermost knots.
 * <p>
 * Knot values are used as given, not
 * {@linkplain AbstractPresetFunc#normalized(double, java.util.function.DoubleUnaryOperator) normalized}:
 * that would flip a deliberately decreasing curve and divide by zero on a
 * flat one. So the endpoints need not map to {@code 0} and
 * {@link #getSchemeRange()}.
 */
public class CustomInterpPresetFunc extends AbstractPresetFunc
{
	private double[] knotTs;

	private double[] knotValues;

	/** Starts linear: knots {@code (0, 0)} and {@code (1, 1)}. */
	public CustomInterpPresetFunc( final double min, final double max, final int schemeRange )
	{
		super( min, max, schemeRange );
		setKnots( new double[] { 0.0, 1.0 }, new double[] { 0.0, 1.0 } );
	}

	/**
	 * {@code shape} sampled at {@code numKnots} evenly spaced knots, for seeding
	 * an editable curve.
	 *
	 * @throws IllegalArgumentException if {@code numKnots} is less than 2.
	 */
	public static CustomInterpPresetFunc sampled( final IPresetFunc shape, final int numKnots )
	{
		Objects.requireNonNull( shape, "shape" );
		if ( numKnots < 2 )
			throw new IllegalArgumentException( "numKnots must be at least 2, got " + numKnots );

		final double min = shape.getMin();
		final double max = shape.getMax();
		final int schemeRange = shape.getSchemeRange();

		final double[] ts = new double[ numKnots ];
		final double[] values = new double[ numKnots ];
		for ( int i = 0; i < numKnots; i++ )
		{
			final double t = i / ( double ) ( numKnots - 1 );
			final double rawValue = min + t * ( max - min );
			ts[ i ] = t;
			values[ i ] = Math.max( 0.0, Math.min( 1.0, shape.getSchemeValueForRaw( rawValue ) / schemeRange ) );
		}

		final CustomInterpPresetFunc result = new CustomInterpPresetFunc( min, max, schemeRange );
		result.setKnots( ts, values );
		return result;
	}

	/**
	 * @param ts     domain fractions in {@code [0, 1]}, strictly ascending.
	 * @param values scheme-value fractions in {@code [0, 1]}; need not be ascending.
	 * @throws IllegalArgumentException if there are fewer than 2 knots, the lengths differ,
	 *                                  {@code ts} is not strictly ascending, or any entry is
	 *                                  outside {@code [0, 1]}.
	 */
	public void setKnots( final double[] ts, final double[] values )
	{
		Objects.requireNonNull( ts, "ts" );
		Objects.requireNonNull( values, "values" );
		if ( ts.length != values.length )
			throw new IllegalArgumentException( "ts and values must have the same length, got " + ts.length + " and " + values.length );
		if ( ts.length < 2 )
			throw new IllegalArgumentException( "at least 2 knots are required, got " + ts.length );
		for ( int i = 0; i < ts.length; i++ )
		{
			// negated so NaN is rejected too
			if ( !( ts[ i ] >= 0.0 && ts[ i ] <= 1.0 ) )
				throw new IllegalArgumentException( "knot t must be in [0, 1], got " + ts[ i ] + " at index " + i );
			if ( !( values[ i ] >= 0.0 && values[ i ] <= 1.0 ) )
				throw new IllegalArgumentException( "knot value must be in [0, 1], got " + values[ i ] + " at index " + i );
		}
		for ( int i = 1; i < ts.length; i++ )
			if ( !( ts[ i ] > ts[ i - 1 ] ) )
				throw new IllegalArgumentException( "ts must be strictly ascending, got " + ts[ i - 1 ] + " at index " + ( i - 1 ) + " followed by " + ts[ i ] );

		this.knotTs = ts.clone();
		this.knotValues = values.clone();
	}

	public int getKnotCount()
	{
		return knotTs.length;
	}

	public double[] getKnotTs()
	{
		return knotTs.clone();
	}

	public double[] getKnotValues()
	{
		return knotValues.clone();
	}

	/** Knots carry over unchanged; they are domain fractions. */
	@Override
	public CustomInterpPresetFunc withRange( final double min, final double max )
	{
		final CustomInterpPresetFunc copy = new CustomInterpPresetFunc( min, max, getSchemeRange() );
		copy.setKnots( knotTs, knotValues );
		return copy;
	}

	@Override
	double shape( final double t )
	{
		return interpolate( t );
	}

	private double interpolate( final double t )
	{
		final int lastIndex = knotTs.length - 1;
		if ( t <= knotTs[ 0 ] )
			return knotValues[ 0 ];
		if ( t >= knotTs[ lastIndex ] )
			return knotValues[ lastIndex ];

		int lo = 0;
		while ( lo + 1 < knotTs.length && knotTs[ lo + 1 ] <= t )
			lo++;
		final int hi = lo + 1;

		final double frac = ( t - knotTs[ lo ] ) / ( knotTs[ hi ] - knotTs[ lo ] );
		return knotValues[ lo ] + frac * ( knotValues[ hi ] - knotValues[ lo ] );
	}
}
