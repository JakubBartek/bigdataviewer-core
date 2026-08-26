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
package bdv.tools.brightness.palette;

import java.util.Objects;

import bdv.tools.brightness.colorscheme.DiscreteColorScheme;

/**
 * Maps a raw image value to a color through a {@link DiscreteColorScheme},
 * owning everything the color scheme itself deliberately does not know about:
 * the raw-value origin ({@link #getMin()}), the raw-value width of one
 * palette step ({@link #getStepSize()}), and what happens when a raw value
 * falls outside the scheme's domain on either side ({@link #getLeftBoundaryCondition()}/
 * {@link #getRightBoundaryCondition()}).
 * <p>
 * Responsibility split (see {@code IColorScheme}'s own javadoc for the other
 * half): {@link DiscreteColorScheme} only ever turns a palette value into a
 * color; this class only ever turns a raw value into a palette value (plus
 * boundary handling) and then delegates. {@code min}, {@code stepSize} and
 * the boundary conditions live here, never in the color scheme.
 */
public class DiscretePaletteWrapper
{
	private DiscreteColorScheme colorScheme;

	private float min;

	private float stepSize;

	private BoundaryCondition leftBoundaryCondition;

	private BoundaryCondition rightBoundaryCondition;

	private float leftSpecialValue;

	private float rightSpecialValue;

	/**
	 * @param colorScheme            the palette this wrapper feeds; {@code paletteValue -> color} lives
	 *                               entirely there (see the class javadoc).
	 * @param min                    raw value that maps to palette value {@code 0}.
	 * @param stepSize               raw-value width of one palette step; must be strictly positive.
	 * @param leftBoundaryCondition  applied when {@code (rawValue - min) / stepSize < 0}.
	 * @param rightBoundaryCondition applied when {@code (rawValue - min) / stepSize >= colorScheme.getDelkaIntervalu()}.
	 * @throws IllegalArgumentException if {@code stepSize} is not strictly positive.
	 */
	public DiscretePaletteWrapper( final DiscreteColorScheme colorScheme, final float min, final float stepSize,
	                               final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		requirePositiveStepSize( stepSize );

		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
		this.min = min;
		this.stepSize = stepSize;
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
	}

	/**
	 * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
	 */
	public DiscretePaletteWrapper( final DiscreteColorScheme colorScheme, final float min, final float stepSize )
	{
		this( colorScheme, min, stepSize, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );
	}

	public DiscreteColorScheme getColorScheme()
	{
		return colorScheme;
	}

	public void setColorScheme( final DiscreteColorScheme colorScheme )
	{
		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
	}

	public float getMin()
	{
		return min;
	}

	public void setMin( final float min )
	{
		this.min = min;
	}

	public float getStepSize()
	{
		return stepSize;
	}

	/** @throws IllegalArgumentException if {@code stepSize} is not strictly positive -- same as the constructor. */
	public void setStepSize( final float stepSize )
	{
		requirePositiveStepSize( stepSize );
		this.stepSize = stepSize;
	}

	public BoundaryCondition getLeftBoundaryCondition()
	{
		return leftBoundaryCondition;
	}

	public void setLeftBoundaryCondition( final BoundaryCondition leftBoundaryCondition )
	{
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
	}

	public BoundaryCondition getRightBoundaryCondition()
	{
		return rightBoundaryCondition;
	}

	public void setRightBoundaryCondition( final BoundaryCondition rightBoundaryCondition )
	{
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
	}

	/** The palette value substituted for a raw value that hit the left boundary, when {@link #getLeftBoundaryCondition()} is {@link BoundaryCondition#SPECIAL}; otherwise unused. */
	public float getLeftSpecialValue()
	{
		return leftSpecialValue;
	}

	public void setLeftSpecialValue( final float leftSpecialValue )
	{
		this.leftSpecialValue = leftSpecialValue;
	}

	/** The palette value substituted for a raw value that hit the right boundary, when {@link #getRightBoundaryCondition()} is {@link BoundaryCondition#SPECIAL}; otherwise unused. */
	public float getRightSpecialValue()
	{
		return rightSpecialValue;
	}

	public void setRightSpecialValue( final float rightSpecialValue )
	{
		this.rightSpecialValue = rightSpecialValue;
	}

	/**
	 * The color for a raw image value: converts it to a palette value
	 * ({@code (rawValue - min) / stepSize}), applies the left/right boundary
	 * condition if it falls outside {@code [0, colorScheme.getDelkaIntervalu())},
	 * then looks up the (possibly boundary-adjusted) palette value in
	 * {@link #getColorScheme()}.
	 */
	public int getRGBForRaw( final float rawValue )
	{
		final float paletteValue = ( rawValue - min ) / stepSize;

		final float boundedPaletteValue;
		if ( paletteValue < 0 )
			boundedPaletteValue = apply( leftBoundaryCondition, paletteValue, true );
		else if ( paletteValue >= colorScheme.getDelkaIntervalu() )
			boundedPaletteValue = apply( rightBoundaryCondition, paletteValue, false );
		else
			boundedPaletteValue = paletteValue;

		return colorScheme.getRGB( boundedPaletteValue );
	}

	private float apply( final BoundaryCondition condition, final float paletteValue, final boolean isLeft )
	{
		switch ( condition )
		{
			case CYCLE:
				return floorMod( paletteValue, colorScheme.getDelkaIntervalu() );
			case SPECIAL:
				return isLeft ? leftSpecialValue : rightSpecialValue;
			case CLAMP:
			default:
				// The color scheme's own getRGB already clamps an out-of-domain
				// palette value to its nearest edge color, so passing the value
				// through unchanged is CLAMP's actual behavior.
				return paletteValue;
		}
	}

	/**
	 * Floating-point equivalent of {@link Math#floorMod(int, int)} (which has
	 * no float overload): wraps {@code value} into {@code [0, modulus)}.
	 * Unlike Java's {@code %} operator, which returns a <em>negative</em>
	 * result for a negative {@code value} (e.g. {@code -0.5 % 3 == -0.5}, still
	 * outside the domain it was supposed to wrap into) -- the same
	 * "remainder, then add the modulus back if still negative" idiom already
	 * used for cyclic wrapping in {@code MappingModel#cyclicOffset}.
	 */
	private static float floorMod( final float value, final float modulus )
	{
		float m = value % modulus;
		if ( m < 0 )
			m += modulus;
		return m;
	}

	private static void requirePositiveStepSize( final float stepSize )
	{
		if ( !( stepSize > 0 ) ) // written this way, not stepSize <= 0, so NaN is rejected too
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
	}
}
