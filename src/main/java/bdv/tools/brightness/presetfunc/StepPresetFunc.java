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

/**
 * One color stop per {@code stepSize} raw values (e.g. {@code stepSize = 1}
 * for label ids). The domain is derived, not given:
 * {@link #getMax()} is {@code min + stepSize * paletteRangeLength}, so a
 * display range's maximum never affects colors and
 * {@link #withRange(double, double)} ignores {@code max}.
 * <p>
 * Describes a single pass; repetition is {@code BoundaryCondition.CYCLE}'s job.
 */
public class StepPresetFunc extends AbstractPresetFunc
{
	private final double stepSize;

	/**
	 * @param min      raw value the first color stop starts at.
	 * @param stepSize raw values per color stop; see {@link #defaultStepSize}.
	 * @throws IllegalArgumentException if {@code stepSize} is not strictly positive, or
	 *                                  {@code min} is too large for the palette's width to
	 *                                  change it.
	 */
	public StepPresetFunc( final double min, final int paletteRangeLength, final double stepSize )
	{
		super( min, min + requirePositive( stepSize ) * paletteRangeLength, paletteRangeLength );
		this.stepSize = stepSize;
	}

	/** Rejects NaN too. */
	private static double requirePositive( final double stepSize )
	{
		if ( !( stepSize > 0.0 ) )
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
		return stepSize;
	}

	/** The step size that spreads the palette exactly once across {@code [min, max]}. */
	public static double defaultStepSize( final double min, final double max, final int paletteRangeLength )
	{
		return ( max - min ) / ( double ) paletteRangeLength;
	}

	/** How many raw values one color stop covers. */
	public double getStepSize()
	{
		return stepSize;
	}

	/** Keeps the step size and ignores {@code max}. */
	@Override
	public StepPresetFunc withRange( final double min, final double max )
	{
		return new StepPresetFunc( min, getPaletteRangeLength(), stepSize );
	}

	/**
	 * Computed in stops, {@code (clampedRaw - min) / stepSize}, where boundaries
	 * are integers. Going through a normalized {@code [0, 1]} fraction lands
	 * them a hair below, one stop back.
	 */
	@Override
	double paletteValueForClampedRaw( final double clampedRaw )
	{
		final double min = getMin();
		// Tolerance in raw ULPs, since subtracting min cancels the low bits:
		// near raw 4610 with step 0.3 a boundary came out ~2700 ULPs of the
		// quotient short.
		final double rawTolerance = SNAP_ULPS * Math.ulp( Math.max( Math.abs( clampedRaw ), Math.abs( min ) ) );
		return snappedToWhole( ( clampedRaw - min ) / stepSize, rawTolerance / stepSize );
	}

	/** Delegates to {@link #paletteValueForClampedRaw(double)} so there is one implementation. */
	@Override
	double shape( final double t )
	{
		return paletteValueForClampedRaw( getMin() + t * ( getMax() - getMin() ) ) / getPaletteRangeLength();
	}
}
