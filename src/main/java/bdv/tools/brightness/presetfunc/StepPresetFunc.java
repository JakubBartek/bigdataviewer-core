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
 * A linear ramp with an explicit, raw-unit <em>step size</em>: one color stop
 * per {@code stepSize} raw values, repeating through the palette as often as
 * the domain allows. The shape a discrete (categorical) palette is driven by,
 * where the question is not "what curve reshapes this gradient" but "how many
 * raw units does one color cover" -- e.g. a label image wants
 * {@code stepSize = 1}, so every integer label id gets its own color.
 * <p>
 * Unlike every other {@link PresetFunc} here, this one is not parameterized by
 * a fixed shape constant but by a quantity in the caller's own raw units, so
 * {@link #withRange(float, float)} deliberately keeps the step size rather
 * than rescaling it: a band that covers 1 raw unit still covers 1 raw unit
 * after the display range changes, which is the whole point of specifying it
 * in raw units. The number of times the palette repeats across the domain
 * changes instead.
 * <p>
 * The palette repeats <em>within</em> the domain once the ramp runs past the
 * last stop -- which is a different thing from {@code BoundaryCondition.CYCLE},
 * and composes with it: this class decides what happens inside
 * {@code [getMin(), getMax()]}, the boundary condition what happens outside.
 * When the domain is not wide enough for even one full pass ({@link #getPeriods()}
 * {@code <= 1}) there is nothing to repeat and the ramp is left plain, so the
 * default step size ({@link #defaultStepSize}, exactly one pass across the
 * domain) still maps {@link #getMax()} onto the last stop rather than wrapping
 * back onto the first.
 */
public class StepPresetFunc extends AbstractPresetFunc
{
	private final double stepSize;

	private final double periods;

	/**
	 * @param stepSize how many raw values one color stop covers; must be strictly positive.
	 *                 See {@link #defaultStepSize} for the value that spreads the palette
	 *                 exactly once across {@code [min, max]}.
	 * @throws IllegalArgumentException if {@code stepSize} is not strictly positive (or is NaN).
	 */
	public StepPresetFunc( final double min, final double max, final int paletteRangeLength, final double stepSize )
	{
		super( min, max, paletteRangeLength );
		// Written as !(x > 0) rather than (x <= 0) so NaN is rejected too.
		if ( !( stepSize > 0.0 ) )
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
		this.stepSize = stepSize;
		// Snapped, because this is a branch condition below and being a single
		// ULP out flips it: with the default step size the exact value is 1, but
		// (max - min) / ((max - min) / N * N) lands just above 1 for about one
		// in twelve palette-size and display-range pairs (a 13-stop palette over
		// 0-65535 among them). That takes the wrapping branch and sends getMax()
		// to the FIRST stop instead of the last, contradicting the contract in
		// the class javadoc.
		this.periods = snappedToWhole( ( max - min ) / ( stepSize * paletteRangeLength ) );
	}

	/**
	 * The step size that spreads the palette exactly once across
	 * {@code [min, max]} -- i.e. the one for which this function is a plain
	 * linear ramp over the whole palette, the behavior of a discrete palette
	 * with no explicit step size chosen.
	 */
	public static double defaultStepSize( final double min, final double max, final int paletteRangeLength )
	{
		return ( max - min ) / ( double ) paletteRangeLength;
	}

	/** How many raw values one color stop covers. */
	public double getStepSize()
	{
		return stepSize;
	}

	/**
	 * How many complete passes through the palette fit across the domain, i.e.
	 * {@code (max - min) / (stepSize * paletteRangeLength)}. A value of 1 is a
	 * single pass (see {@link #defaultStepSize}); above 1 the palette repeats,
	 * below 1 only its first part is reached.
	 */
	public double getPeriods()
	{
		return periods;
	}

	/** As {@link PresetFunc#withRange(double, double)}, keeping the step size in raw units; see the class javadoc. */
	@Override
	public StepPresetFunc withRange( final double min, final double max )
	{
		return new StepPresetFunc( min, max, getPaletteRangeLength(), stepSize );
	}

	/**
	 * Worked entirely in <em>stops</em> -- {@code (clampedRaw - min) / stepSize},
	 * i.e. how far into the ramp this raw value sits measured in color stops --
	 * because that is the unit in which the answers that have to be exact are
	 * whole numbers. Every stop boundary is an integer here, and the wrap is a
	 * plain modulo by the stop count, so nothing has to survive a round trip
	 * through a normalized fraction to get back to it.
	 * <p>
	 * Two tempting round trips are wrong here, both by about one part in 10^16,
	 * which is exactly enough to land on the wrong side of a floor. Reaching the
	 * position as {@code t * periods} multiplies two separately-rounded copies
	 * of {@code (max - min)} back together and they do not cancel, putting an
	 * exact stop boundary a hair <em>below</em> its integer, one stop back; under
	 * {@code BoundaryCondition.CYCLE} that reads as the last color twice in a row
	 * where it should have wrapped to the first. Returning a {@code [0, 1]}
	 * fraction for {@link AbstractPresetFunc} to scale by the stop count divides
	 * by that count only to multiply it straight back, which turns stop 1 of a
	 * 3-stop palette into {@code 0.9999999999999998}. Neither is visible while
	 * the result is narrowed to {@code float} on the way out -- that narrowing
	 * rounds the difference away and hides both -- so neither may be reintroduced
	 * on the strength of the numbers happening to look right.
	 */
	@Override
	double paletteValueForClampedRaw( final double clampedRaw )
	{
		final int stops = getPaletteRangeLength();
		final double min = getMin();
		// A raw value within a few ULPs of a stop boundary IS that boundary:
		// subtracting min cancels the low bits away, so at the precision the raw
		// value itself carries the two are not distinguishable. The budget is
		// therefore in RAW units -- ULPs of the larger operand of that
		// subtraction -- converted to stops by dividing by the step size. It has
		// to be: around raw 4610 with a step size of 0.3, an exact stop boundary
		// came out 1.2e-12 short, which is under half a ULP of the raw value but
		// about 2700 ULPs of the quotient, and floors to the previous stop.
		final double rawTolerance = SNAP_ULPS * Math.ulp( Math.max( Math.abs( clampedRaw ), Math.abs( min ) ) );
		final double stopsIn = snappedToWhole( ( clampedRaw - min ) / stepSize, rawTolerance / stepSize );
		// Below one full pass there is nothing to wrap, and wrapping would cost
		// the endpoint: getMax() would come back as stop 0 (the *next* pass's
		// first) instead of reaching the last one. See the class javadoc.
		return periods <= 1.0 ? stopsIn : stopsIn - stops * Math.floor( stopsIn / stops );
	}

	/**
	 * This shape is really a function of the raw value, so it is defined by
	 * {@link #paletteValueForClampedRaw(double)}; a normalized {@code t} is
	 * un-normalized back into raw units to get there, keeping one
	 * implementation rather than two that could drift apart.
	 */
	@Override
	double shape( final double t )
	{
		return paletteValueForClampedRaw( getMin() + t * ( getMax() - getMin() ) ) / getPaletteRangeLength();
	}
}
