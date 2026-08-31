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
 * A user-defined, piecewise-linear shape through an arbitrary number of
 * control points ("knots"), for the cases the fixed shapes ({@link LinearPresetFunc},
 * {@link SigmoidPresetFunc}, etc.) cannot express.
 * <p>
 * A knot is a {@code (t, value)} pair, both already normalized to this
 * function's own domain fraction -- {@code t} is how far across
 * {@code [getMin(), getMax()]} the knot sits (0 at {@link #getMin()}, 1 at
 * {@link #getMax()}), and {@code value} is how far across the palette-value
 * range the knot sits (0 at palette value 0, 1 at {@link #getPaletteRangeLength()}).
 * Deliberately <em>not</em> raw values, palette values, or window/UI pixel
 * coordinates: a caller that has any of those (e.g. a future curve-editing
 * widget working in on-screen pixels) converts to this normalized {@code [0,1]}
 * space before calling {@link #setKnots(double[], double[])}, the same way
 * {@code MappingCurvePanel} already converts pixel coordinates before ever
 * calling {@code Curve#addPoint} -- this class only ever works in its own
 * normalized domain, never anything presentation-specific.
 * <p>
 * Between two knots, the value is linearly interpolated; outside the first or
 * last knot's {@code t}, the value stays flat at that knot's value (unlike
 * {@code Curve#evaluate}, which extrapolates the first segment's slope below
 * its first point -- a needless asymmetry not worth reproducing here).
 * <p>
 * Unlike the fixed shapes in this package, the knot values are used
 * <em>as given</em>, not rescaled to pin {@code t = 0}/{@code t = 1} onto
 * exactly {@code 0}/{@code 1}. Those shapes are monotonically increasing by
 * construction, so
 * {@link AbstractPresetFunc#normalized(double, java.util.function.DoubleUnaryOperator)}
 * only ever pins their endpoints; applied to an arbitrary user-defined curve
 * it would instead silently rewrite the user's intent -- a deliberately
 * decreasing curve would come back increasing, and a deliberately flat one
 * would divide by zero. So a {@code CustomInterpPresetFunc} may map
 * {@link #getMin()}/{@link #getMax()} to something other than {@code 0}/
 * {@link #getPaletteRangeLength()} (whatever its outermost knots say), and may be
 * decreasing or flat; every other {@link PresetFunc} still guarantees the
 * exact endpoints. Knot values are constrained to {@code [0, 1]} instead, so
 * a palette value this produces still lands in
 * {@code [0, getPaletteRangeLength()]}.
 * <p>
 * This flat/clamped extrapolation is a property of the shape between {@code
 * t = 0} and {@code t = 1} -- it has nothing to do with, and does not
 * duplicate, what {@code PresetPaletteWrapper}'s {@code BoundaryCondition}
 * (CLAMP/CYCLE/SPECIAL) does for a raw value entirely outside
 * {@code [getMin(), getMax()]}; that decision is made before this class is
 * ever consulted, exactly as for every other {@link PresetFunc}.
 */
public class CustomInterpPresetFunc extends AbstractPresetFunc
{
	private double[] knotTs;

	private double[] knotValues;

	/** Starts with two knots, {@code (0, 0)} and {@code (1, 1)} -- the same shape as {@link LinearPresetFunc} until {@link #setKnots(double[], double[])} is called. */
	public CustomInterpPresetFunc( final double min, final double max, final int paletteRangeLength )
	{
		super( min, max, paletteRangeLength );
		setKnots( new double[] { 0.0, 1.0 }, new double[] { 0.0, 1.0 } );
	}

	/**
	 * A piecewise-linear approximation of {@code shape}, over the same domain
	 * and {@link #getPaletteRangeLength()}: {@code numKnots} knots evenly spaced
	 * across {@code [shape.getMin(), shape.getMax()]}, each valued at
	 * {@code shape.getPaletteValueForRaw(rawValue)}. Meant for seeding an
	 * editable, draggable curve with one of the fixed shapes (see the class
	 * javadoc) -- the result starts out tracing {@code shape}, but is a plain
	 * {@code CustomInterpPresetFunc} afterwards, so its knots can be moved
	 * independently of it.
	 *
	 * @throws IllegalArgumentException if {@code numKnots} is less than 2.
	 */
	public static CustomInterpPresetFunc sampled( final PresetFunc shape, final int numKnots )
	{
		Objects.requireNonNull( shape, "shape" );
		if ( numKnots < 2 )
			throw new IllegalArgumentException( "numKnots must be at least 2, got " + numKnots );

		final double min = shape.getMin();
		final double max = shape.getMax();
		final int paletteRangeLength = shape.getPaletteRangeLength();

		final double[] ts = new double[ numKnots ];
		final double[] values = new double[ numKnots ];
		for ( int i = 0; i < numKnots; i++ )
		{
			final double t = i / ( double ) ( numKnots - 1 );
			final double rawValue = min + t * ( max - min );
			ts[ i ] = t;
			values[ i ] = Math.max( 0.0, Math.min( 1.0, shape.getPaletteValueForRaw( rawValue ) / paletteRangeLength ) );
		}

		final CustomInterpPresetFunc result = new CustomInterpPresetFunc( min, max, paletteRangeLength );
		result.setKnots( ts, values );
		return result;
	}

	/**
	 * Replace the control points defining this shape.
	 *
	 * @param ts     each knot's position, as a domain fraction in {@code [0, 1]}; must be
	 *               strictly ascending.
	 * @param values each knot's value, as a palette-value fraction in {@code [0, 1]}; same
	 *               length as {@code ts}. Need not be ascending -- a decreasing run is a
	 *               legitimate (inverted) curve, see the class javadoc.
	 * @throws IllegalArgumentException if there are fewer than 2 knots, the two arrays have
	 *                                  different lengths, {@code ts} is not strictly ascending,
	 *                                  or any entry falls outside {@code [0, 1]}.
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
			// Written as !(0 <= x <= 1) rather than (x < 0 || x > 1) so NaN is rejected too.
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

	/** As {@link PresetFunc#withRange(double, double)}, carrying this function's current knots over unchanged (they are domain fractions, so independent of the raw range). */
	@Override
	public CustomInterpPresetFunc withRange( final double min, final double max )
	{
		final CustomInterpPresetFunc copy = new CustomInterpPresetFunc( min, max, getPaletteRangeLength() );
		copy.setKnots( knotTs, knotValues );
		return copy;
	}

	/** The knot values as given -- deliberately not {@code normalized(...)}; see the class javadoc. */
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
