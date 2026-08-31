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
 * Shared range storage and rescaling mechanics for every {@link PresetFunc}:
 * a concrete subclass only implements {@link #shape(double)}, a normalized
 * curve shape over {@code [0, 1] -> [0, 1]} (for the fixed shapes, with
 * {@code shape(0) == 0} and {@code shape(1) == 1}, exactly the family of shapes
 * {@code bdv.tools.brightness.MappingPreset} already defines, deliberately
 * mirrored here with the same constants for the same curve shapes -- see
 * each subclass); this class handles normalizing a raw value into that
 * {@code [0, 1]} domain and rescaling the result into
 * {@code [0, getPaletteRangeLength()]}.
 * <p>
 * Not built on {@code LutEditorMapping}/{@code Curve}: those exist to drive an
 * interactively-draggable, {@code [0, 255]}-output, 9-point piecewise-linear
 * <em>approximation</em> of a shape, for a UI that lets a user further hand-edit
 * it -- a different job from computing a shape's exact value at an arbitrary
 * point over an arbitrary {@code [0, getPaletteRangeLength()]} output range, which
 * is all a {@link PresetFunc} needs to do. The shape formulas themselves are
 * copied verbatim (same constants) from {@code MappingPreset} rather than
 * shared by reference, to avoid a dependency from this new, self-contained
 * architecture back onto the old curve-editing one.
 */
abstract class AbstractPresetFunc implements PresetFunc
{
	/**
	 * How many ULPs either side of a whole number still counts as that whole
	 * number, for {@link #snappedToWhole(double)} and as the unit callers scale
	 * when they pass their own tolerance. Measured, not guessed: a quantity
	 * whose exact value is whole was never seen further than 2 ULPs from it once
	 * the tolerance is expressed in the right units, so 4 leaves a factor of two
	 * of headroom, while the distinction it must never blur -- neighbouring
	 * color stops -- is a whole 1.0 away.
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
		// Clamped in raw units rather than after normalizing, and never narrowed
		// to float on the way through: a float carries about 7 digits, which is
		// not enough to keep a color-stop boundary on its integer (0.7f is
		// really 0.69999998807907104...), and DiscreteColorScheme floors what it
		// is given, so a boundary that lands a hair low picks the stop before it.
		final double clampedRaw = Math.max( min, Math.min( max, rawValue ) );
		return paletteValueForClampedRaw( clampedRaw );
	}

	/**
	 * The palette value for a raw value already clamped into
	 * {@code [getMin(), getMax()]}. By default the raw value is normalized to
	 * {@code t} in {@code [0, 1]}, handed to {@link #shape(double)}, and scaled
	 * up by {@link #getPaletteRangeLength()} -- all a shape defined as a curve
	 * over {@code [0, 1]} can do.
	 * <p>
	 * The seam exists for {@link StepPresetFunc}, the one shape here defined in
	 * raw units rather than by a fixed constant. That route is lossy for it
	 * twice over: normalizing by {@code (max - min)} only to multiply a
	 * separately-rounded {@code (max - min)} back in, and dividing by the
	 * palette range length only to multiply it back out. Neither round trip
	 * cancels, and a stop boundary that is algebraically a whole number arrives
	 * a hair off it -- which is the whole ballgame for a value about to be
	 * floored to a stop. It computes the palette value from {@code clampedRaw}
	 * directly instead, in the units the boundaries are actually whole in.
	 */
	double paletteValueForClampedRaw( final double clampedRaw )
	{
		return shape( ( clampedRaw - min ) / ( max - min ) ) * paletteRangeLength;
	}

	/**
	 * The normalized curve shape, {@code t} in {@code [0, 1]}, returning a
	 * value in {@code [0, 1]}. {@code t} is already clamped into {@code [0, 1]}
	 * by {@link #getPaletteValueForRaw(double)} before this is called.
	 * <p>
	 * The fixed shapes additionally guarantee {@code shape(0) == 0} and
	 * {@code shape(1) == 1} (several of them via {@link #normalized(double, DoubleUnaryOperator)});
	 * {@link CustomInterpPresetFunc} deliberately does not, since its shape is
	 * whatever the user's knots say -- see its javadoc.
	 */
	abstract double shape( double t );

	/**
	 * {@code x} snapped to the nearest whole number when it is within
	 * {@code tolerance} of one; {@code x} unchanged otherwise.
	 * <p>
	 * For a quantity that is algebraically a whole number -- how many stops into
	 * the palette a raw value sits, how many passes fit across the domain -- and
	 * is then floored or compared against an integer, arriving a hair short is
	 * the difference between the right color stop and its neighbour. No amount
	 * of care in the arithmetic removes that: the inputs (a raw pixel value, a
	 * dragged display range, a typed step size) are inexact before this code
	 * ever runs, so a quantity derived from them can only land <em>near</em> the
	 * whole number it means. This makes the tolerance that discretizing needs
	 * explicit, at the sites that actually make a discrete decision, instead of
	 * leaving it to whatever a narrowing to {@code float} happened to round away.
	 * <p>
	 * {@code tolerance} is a parameter rather than a constant because the right
	 * budget depends on how {@code x} was derived, and can be far larger than
	 * {@code x}'s own ULPs: a quotient whose numerator came from a cancelling
	 * subtraction inherits the numerator's absolute error, not its own relative
	 * one. See {@link StepPresetFunc#paletteValueForClampedRaw(double)}.
	 */
	static double snappedToWhole( final double x, final double tolerance )
	{
		final double whole = Math.rint( x );
		return Math.abs( x - whole ) <= tolerance ? whole : x;
	}

	/** {@link #snappedToWhole(double, double)} with the default budget of {@link #SNAP_ULPS} ULPs of {@code x} itself -- right only when {@code x} was not derived through a cancelling subtraction. */
	static double snappedToWhole( final double x )
	{
		return snappedToWhole( x, SNAP_ULPS * Math.ulp( x ) );
	}

	/**
	 * Rescales {@code f} so that {@code f(0) -> 0} and {@code f(1) -> 1} --
	 * same helper (and purpose) as {@code MappingPreset}'s own
	 * {@code normalized}, for shapes (sigmoid, tan, atan) that are naturally
	 * defined on a wider range than {@code [0, 1]} and need rescaling to fit
	 * it exactly.
	 */
	static double normalized( final double t, final DoubleUnaryOperator f )
	{
		final double v = f.applyAsDouble( t );
		final double v0 = f.applyAsDouble( 0.0 );
		final double v1 = f.applyAsDouble( 1.0 );
		return ( v - v0 ) / ( v1 - v0 );
	}
}
