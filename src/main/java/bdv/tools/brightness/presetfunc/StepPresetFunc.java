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
	public StepPresetFunc( final float min, final float max, final int paletteRangeLength, final double stepSize )
	{
		super( min, max, paletteRangeLength );
		// Written as !(x > 0) rather than (x <= 0) so NaN is rejected too.
		if ( !( stepSize > 0.0 ) )
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
		this.stepSize = stepSize;
		this.periods = ( max - min ) / ( stepSize * paletteRangeLength );
	}

	/**
	 * The step size that spreads the palette exactly once across
	 * {@code [min, max]} -- i.e. the one for which this function is a plain
	 * linear ramp over the whole palette, the behavior of a discrete palette
	 * with no explicit step size chosen.
	 */
	public static double defaultStepSize( final float min, final float max, final int paletteRangeLength )
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

	/** As {@link PresetFunc#withRange(float, float)}, keeping the step size in raw units; see the class javadoc. */
	@Override
	public StepPresetFunc withRange( final float min, final float max )
	{
		return new StepPresetFunc( min, max, getPaletteRangeLength(), stepSize );
	}

	@Override
	double shape( final double t )
	{
		final double u = t * periods;
		// Below one full pass there is nothing to wrap, and wrapping would cost
		// the endpoint: u == 1 would come back as 0 (the *next* pass's first
		// stop) instead of reaching the last one. See the class javadoc.
		return periods <= 1.0 ? u : u - Math.floor( u );
	}
}
