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
 * range the knot sits (0 at palette value 0, 1 at {@link #getDelkaIntervalu()}).
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
 * Whatever the first and last knot actually evaluate to, the {@link #shape(double)}
 * template method (see {@link AbstractPresetFunc#normalized(double, java.util.function.DoubleUnaryOperator)})
 * rescales the result so {@code t = 0} and {@code t = 1} land on exactly
 * {@code 0} and {@code 1} regardless -- the same universal
 * "{@link #getMin()} maps to palette value {@code 0}, {@link #getMax()} maps
 * to {@link #getDelkaIntervalu()}" guarantee every other {@link PresetFunc}
 * makes, so knots don't strictly need to start at {@code t = 0} or end at
 * {@code t = 1} themselves.
 * <p>
 * This flat/clamped extrapolation is a property of the shape between {@code
 * t = 0} and {@code t = 1} -- it has nothing to do with, and does not
 * duplicate, what {@code ContinuousPaletteWrapper}'s {@code BoundaryCondition}
 * (CLAMP/CYCLE/SPECIAL) does for a raw value entirely outside
 * {@code [getMin(), getMax()]}; that decision is made before this class is
 * ever consulted, exactly as for every other {@link PresetFunc}.
 */
public class CustomInterpPresetFunc extends AbstractPresetFunc
{
	private double[] knotTs;

	private double[] knotValues;

	/** Starts with two knots, {@code (0, 0)} and {@code (1, 1)} -- the same shape as {@link LinearPresetFunc} until {@link #setKnots(double[], double[])} is called. */
	public CustomInterpPresetFunc( final float min, final float max, final float delkaIntervalu )
	{
		super( min, max, delkaIntervalu );
		setKnots( new double[] { 0.0, 1.0 }, new double[] { 0.0, 1.0 } );
	}

	/**
	 * Replace the control points defining this shape.
	 *
	 * @param ts     each knot's position, as a domain fraction; must be strictly ascending.
	 * @param values each knot's value, as a palette-value fraction; same length as {@code ts}.
	 * @throws IllegalArgumentException if there are fewer than 2 knots, the two arrays have
	 *                                  different lengths, or {@code ts} is not strictly ascending.
	 */
	public void setKnots( final double[] ts, final double[] values )
	{
		Objects.requireNonNull( ts, "ts" );
		Objects.requireNonNull( values, "values" );
		if ( ts.length != values.length )
			throw new IllegalArgumentException( "ts and values must have the same length, got " + ts.length + " and " + values.length );
		if ( ts.length < 2 )
			throw new IllegalArgumentException( "at least 2 knots are required, got " + ts.length );
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

	@Override
	double shape( final double t )
	{
		return normalized( t, this::interpolate );
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
