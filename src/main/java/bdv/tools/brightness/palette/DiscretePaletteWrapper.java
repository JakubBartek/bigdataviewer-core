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
 * Responsibility split (see {@code ColorScheme}'s own javadoc for the other
 * half): {@link DiscreteColorScheme} only ever turns a palette value into a
 * color; this class only ever turns a raw value into a palette value (plus
 * boundary handling) and then delegates. {@code min}, {@code stepSize} and
 * the boundary conditions live here, never in the color scheme.
 */
public class DiscretePaletteWrapper extends AbstractPaletteWrapper
{
	private DiscreteColorScheme colorScheme;

	private float min;

	private float stepSize;

	/**
	 * @param colorScheme            the palette this wrapper feeds; {@code paletteValue -> color} lives
	 *                               entirely there (see the class javadoc).
	 * @param min                    raw value that maps to palette value {@code 0}.
	 * @param stepSize               raw-value width of one palette step; must be strictly positive.
	 * @param leftBoundaryCondition  applied when {@code (rawValue - min) / stepSize < 0}.
	 * @param rightBoundaryCondition applied when {@code (rawValue - min) / stepSize >= colorScheme.getPaletteRangeLength()}.
	 * @throws IllegalArgumentException if {@code stepSize} is not strictly positive.
	 */
	public DiscretePaletteWrapper( final DiscreteColorScheme colorScheme, final float min, final float stepSize,
	                               final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		super( leftBoundaryCondition, rightBoundaryCondition );
		requirePositiveStepSize( stepSize );

		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
		this.min = min;
		this.stepSize = stepSize;
	}

	/**
	 * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
	 */
	public DiscretePaletteWrapper( final DiscreteColorScheme colorScheme, final float min, final float stepSize )
	{
		this( colorScheme, min, stepSize, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );
	}

	@Override
	public DiscreteColorScheme getColorScheme()
	{
		return colorScheme;
	}

	/** Palette value {@code 0} sits at {@link #getMin()}. */
	@Override
	float rawDomainMin()
	{
		return min;
	}

	/** One step past the last stop's slot: {@code min + N * stepSize}, i.e. the raw value whose palette value is exactly {@code N}. */
	@Override
	float rawDomainMax()
	{
		return min + colorScheme.getPaletteRangeLength() * stepSize;
	}

	/** The discrete domain is half-open ({@code [0, N)}), so {@link #rawDomainMax()} itself is already outside it. */
	@Override
	boolean isAboveDomain( final float rawValue )
	{
		return rawValue >= rawDomainMax();
	}

	@Override
	float toPaletteValue( final float rawValue )
	{
		return ( rawValue - min ) / stepSize;
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

	/**
	 * Sets {@link #getMin()} to {@code min} and picks the {@link #getStepSize()}
	 * that fits all {@code N} palette stops across {@code [min, max]}:
	 * {@code (max - min) / N}. So {@code min} lands on the first stop and
	 * {@code max} on the far edge of the last one.
	 */
	@Override
	public void setRawDomain( final double min, final double max )
	{
		if ( !( max > min ) )
			throw new IllegalArgumentException( "max must be strictly greater than min, got min=" + min + ", max=" + max );
		this.min = ( float ) min;
		this.stepSize = ( float ) ( ( max - min ) / colorScheme.getPaletteRangeLength() );
	}

	private static void requirePositiveStepSize( final float stepSize )
	{
		if ( !( stepSize > 0 ) ) // written this way, not stepSize <= 0, so NaN is rejected too
			throw new IllegalArgumentException( "stepSize must be strictly positive, got " + stepSize );
	}
}
