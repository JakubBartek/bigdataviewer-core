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
 * per {@code stepSize} raw values. The shape a discrete (categorical) palette
 * is driven by, where the question is not "what curve reshapes this gradient"
 * but "how many raw units does one color cover" -- e.g. a label image wants
 * {@code stepSize = 1}, so every integer label id gets its own color.
 * <p>
 * Unlike every other {@link PresetFunc} here, this one is not parameterized by
 * a fixed shape constant but by a quantity in the caller's own raw units, and
 * <strong>its domain follows from that rather than being given</strong>:
 * {@link #getMax()} is always {@code min + stepSize * paletteRangeLength}, the
 * raw value at which the ramp has just run off the end of the palette. That is
 * the only consistent answer -- with a {@code DiscreteColorScheme} stop
 * {@code i} owns palette values {@code [i, i + 1)} and so raw values
 * {@code [min + i * stepSize, min + (i + 1) * stepSize)}, which puts the last
 * stop's far edge at exactly {@code min + stepSize * paletteRangeLength}.
 * <p>
 * A display range's maximum therefore does not enter into what color a raw
 * value gets; only its minimum and the step size do.
 * {@link #withRange(double, double)} accordingly keeps the step size and
 * ignores the {@code max} it is handed -- a band covering 1 raw unit still
 * covers 1 raw unit after a brightness or contrast change, which is the whole
 * point of specifying it in raw units -- and {@link #defaultStepSize} is how a
 * caller that <em>does</em> want the palette spread across a particular range
 * converts that range into the step size which produces it.
 * <p>
 * Repetition is entirely {@code BoundaryCondition.CYCLE}'s job, not this
 * class's: past {@link #getMax()} the palette either starts over (CYCLE) or
 * stops on its last color (CLAMP), and which one is the wrapper's decision.
 * This class only ever describes a single pass -- a second, independent
 * repetition mechanism inside the domain would make color stops depend on the
 * display range's maximum, moving every stop whenever the range was dragged.
 */
public class StepPresetFunc extends AbstractPresetFunc
{
	private final double stepSize;

	/**
	 * @param min                raw value the first color stop starts at.
	 * @param paletteRangeLength the palette's stop count, which together with the step size
	 *                           fixes {@link #getMax()} at
	 *                           {@code min + stepSize * paletteRangeLength}; there is
	 *                           deliberately no {@code max} parameter, see the class javadoc.
	 * @param stepSize           how many raw values one color stop covers; must be strictly
	 *                           positive. See {@link #defaultStepSize} for the value that lands
	 *                           {@link #getMax()} on a particular raw value.
	 * @throws IllegalArgumentException if {@code stepSize} is not strictly positive (or is NaN), or
	 *                                  if {@code min} is so large that adding the whole palette's
	 *                                  width to it does not change it -- the step size is then
	 *                                  finer than a {@code double} resolves at that magnitude, so
	 *                                  no stop boundary could be told from its neighbour anyway.
	 */
	public StepPresetFunc( final double min, final int paletteRangeLength, final double stepSize )
	{
		super( min, min + requirePositive( stepSize ) * paletteRangeLength, paletteRangeLength );
		this.stepSize = stepSize;
	}

	/** Checked before {@code super(...)} runs, because the derived maximum depends on it. Written as {@code !(x > 0)} rather than {@code x <= 0} so NaN is rejected too. */
	private static double requirePositive( final double stepSize )
	{
		if ( !( stepSize > 0.0 ) )
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
		return stepSize;
	}

	/**
	 * The step size that spreads the palette exactly once across
	 * {@code [min, max]}, i.e. the one whose derived {@link #getMax()} is
	 * {@code max} -- the behavior of a discrete palette with no explicit step
	 * size chosen. This is the one place a caller's desired maximum enters: it
	 * is converted into a step size here, and the step size is what the function
	 * is then actually defined by.
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
	 * As {@link PresetFunc#withRange(double, double)}, keeping the step size in
	 * raw units and <em>ignoring {@code max}</em>: this function's maximum is
	 * derived from its minimum and step size, so re-ranging it can only move
	 * where the palette starts. See the class javadoc.
	 */
	@Override
	public StepPresetFunc withRange( final double min, final double max )
	{
		return new StepPresetFunc( min, getPaletteRangeLength(), stepSize );
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
		// Nothing to wrap: the domain is exactly one pass wide by construction,
		// so getMax() lands on paletteRangeLength (which a discrete scheme
		// clamps to its last stop) and everything past it is the boundary
		// condition's business. See the class javadoc.
		return snappedToWhole( ( clampedRaw - min ) / stepSize, rawTolerance / stepSize );
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
