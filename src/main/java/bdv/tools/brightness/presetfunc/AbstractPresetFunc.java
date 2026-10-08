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

import java.util.function.DoubleUnaryOperator;

/**
 * Range storage and rescaling for every {@link PresetFunc}: a subclass only
 * implements {@link #shape(double)} over {@code [0, 1] -> [0, 1]}.
 */
abstract class AbstractPresetFunc implements PresetFunc
{
	/**
	 * ULPs either side of a whole number that still count as it. Measured
	 * errors never exceeded 2 ULPs (in the right units).
	 */
	static final int SNAP_ULPS = 4;

	private final double min;

	private final double max;

	private final int paletteRangeLength;

	AbstractPresetFunc( final double min, final double max, final int paletteRangeLength )
	{
		if ( !( max > min ) )
			throw new IllegalArgumentException( "max must be strictly greater than min, got min=" + min + ", max=" + max );
		if ( paletteRangeLength <= 0 )
			throw new IllegalArgumentException( "paletteRangeLength must be strictly positive, got " + paletteRangeLength );

		this.min = min;
		this.max = max;
		this.paletteRangeLength = paletteRangeLength;
	}

	@Override
	public final double getMin()
	{
		return min;
	}

	@Override
	public final double getMax()
	{
		return max;
	}

	@Override
	public final int getPaletteRangeLength()
	{
		return paletteRangeLength;
	}

	@Override
	public final double getPaletteValueForRaw( final double rawValue )
	{
		final double clampedRaw = Math.max( min, Math.min( max, rawValue ) );
		return paletteValueForClampedRaw( clampedRaw );
	}

	/**
	 * Normalizes to {@code [0, 1]}, applies {@link #shape(double)} and scales
	 * back up. Overridden by {@link StepPresetFunc}, for which that round trip
	 * lands stop boundaries a hair off their integers.
	 */
	double paletteValueForClampedRaw( final double clampedRaw )
	{
		return shape( ( clampedRaw - min ) / ( max - min ) ) * paletteRangeLength;
	}

	/**
	 * {@code t} and the result are in {@code [0, 1]}. The fixed shapes also
	 * guarantee {@code shape(0) == 0} and {@code shape(1) == 1};
	 * {@link CustomInterpPresetFunc} does not.
	 */
	abstract double shape( double t );

	/**
	 * {@code x} snapped to the nearest whole number when within
	 * {@code tolerance} of it. Used where a value is floored onto a stop.
	 * {@code tolerance} is explicit because a quotient whose numerator came from
	 * a cancelling subtraction inherits the numerator's absolute error, which can
	 * be far more than {@code x}'s own ULPs.
	 */
	static double snappedToWhole( final double x, final double tolerance )
	{
		final double whole = Math.rint( x );
		return Math.abs( x - whole ) <= tolerance ? whole : x;
	}

	/** Tolerance of {@link #SNAP_ULPS} ULPs of {@code x}; only right if {@code x} did not come from a cancelling subtraction. */
	static double snappedToWhole( final double x )
	{
		return snappedToWhole( x, SNAP_ULPS * Math.ulp( x ) );
	}

	/** Rescales {@code f} so that {@code f(0) -> 0} and {@code f(1) -> 1}. */
	static double normalized( final double t, final DoubleUnaryOperator f )
	{
		final double v = f.applyAsDouble( t );
		final double v0 = f.applyAsDouble( 0.0 );
		final double v1 = f.applyAsDouble( 1.0 );
		return ( v - v0 ) / ( v1 - v0 );
	}
}
