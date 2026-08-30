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
	private final float min;

	private final float max;

	private final int paletteRangeLength;

	AbstractPresetFunc( final float min, final float max, final int paletteRangeLength )
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
	public final float getMin()
	{
		return min;
	}

	@Override
	public final float getMax()
	{
		return max;
	}

	@Override
	public final int getPaletteRangeLength()
	{
		return paletteRangeLength;
	}

	@Override
	public final float getPaletteValueForRaw( final float rawValue )
	{
		// Computed in double, not float: narrowing t to float here loses enough
		// precision (e.g. 0.7f is actually 0.69999998807907104...) that
		// StepPresetFunc's periods multiplication can push an exact color-stop
		// boundary a hair below its integer value, so DiscreteColorScheme floors
		// it onto the previous stop instead -- the same stop showing twice in a
		// row where the palette should have advanced.
		final double t = ( ( double ) rawValue - min ) / ( max - min );
		final double clampedT = Math.max( 0.0, Math.min( 1.0, t ) );
		return ( float ) ( shape( clampedT ) * paletteRangeLength );
	}

	/**
	 * The normalized curve shape, {@code t} in {@code [0, 1]}, returning a
	 * value in {@code [0, 1]}. {@code t} is already clamped into {@code [0, 1]}
	 * by {@link #getPaletteValueForRaw(float)} before this is called.
	 * <p>
	 * The fixed shapes additionally guarantee {@code shape(0) == 0} and
	 * {@code shape(1) == 1} (several of them via {@link #normalized(double, DoubleUnaryOperator)});
	 * {@link CustomInterpPresetFunc} deliberately does not, since its shape is
	 * whatever the user's knots say -- see its javadoc.
	 */
	abstract double shape( double t );

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
