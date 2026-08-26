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

import bdv.tools.brightness.colorscheme.ColorScheme;

/**
 * Shared boundary-condition state and {@code rawValue -> color} mechanics for
 * {@link DiscretePaletteWrapper} and {@link ContinuousPaletteWrapper}: both own
 * a left/right {@link BoundaryCondition} plus the palette value each
 * {@link BoundaryCondition#SPECIAL} side substitutes, and both run the same
 * pipeline -- decide whether the raw value is outside the domain, apply that
 * side's boundary condition if so, then hand the resulting palette value to a
 * color scheme.
 * <p>
 * The two differ only in what "the domain" means and how a raw value crosses
 * into a palette value, which is exactly what the hooks below
 * abstract: the discrete wrapper's domain is half-open (its color scheme's is
 * {@code [0, N)}) and it converts with {@code (rawValue - min) / stepSize}; the
 * continuous wrapper's is closed (its color scheme's is
 * {@code [0, paletteRangeLength]}) and it converts through a
 * {@code PresetFunc}.
 * <p>
 * Package-private: an implementation detail shared by the two public wrappers,
 * the same way {@code AbstractColorScheme} and {@code AbstractPresetFunc} sit
 * behind theirs.
 */
abstract class AbstractPaletteWrapper
{
	private BoundaryCondition leftBoundaryCondition;

	private BoundaryCondition rightBoundaryCondition;

	private float leftSpecialValue;

	private float rightSpecialValue;

	AbstractPaletteWrapper( final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
	}

	// -- hooks ---------------------------------------------------------------

	/**
	 * The color scheme a resolved palette value is finally looked up in.
	 * Subclasses narrow the return type to the concrete scheme they wrap.
	 */
	public abstract ColorScheme getColorScheme();

	/** Raw value at the start of this wrapper's domain, i.e. the one mapping to palette value {@code 0}. */
	abstract float rawDomainMin();

	/** Raw value one past the end of this wrapper's domain (half-open) or at its end (closed) -- see {@link #isAboveDomain(float)}. */
	abstract float rawDomainMax();

	/**
	 * Whether {@code rawValue} is past the right edge of the domain. Its own
	 * method rather than a plain comparison because the two wrappers differ
	 * here: the discrete domain excludes {@link #rawDomainMax()}, the
	 * continuous one includes it.
	 */
	abstract boolean isAboveDomain( float rawValue );

	/**
	 * {@code rawValue -> paletteValue}, without any boundary handling --
	 * {@code rawValue} is not assumed to be inside the domain, since
	 * {@link BoundaryCondition#CLAMP} deliberately routes out-of-domain values
	 * straight through here (both a color scheme and a {@code PresetFunc}
	 * already clamp on their own).
	 */
	abstract float toPaletteValue( float rawValue );

	// -- boundary conditions -------------------------------------------------

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

	// -- mapping -------------------------------------------------------------

	/**
	 * The palette value for a raw image value: applies the left/right boundary
	 * condition if {@code rawValue} falls outside this wrapper's domain,
	 * otherwise converts it directly.
	 */
	public final float getPaletteValueForRaw( final float rawValue )
	{
		if ( rawValue < rawDomainMin() )
			return applyBoundary( leftBoundaryCondition, rawValue, leftSpecialValue );
		if ( isAboveDomain( rawValue ) )
			return applyBoundary( rightBoundaryCondition, rawValue, rightSpecialValue );
		return toPaletteValue( rawValue );
	}

	/**
	 * The color for a raw image value: {@link #getPaletteValueForRaw(float)},
	 * looked up in {@link #getColorScheme()} with alpha forced fully opaque.
	 */
	public final int getRGBForRaw( final float rawValue )
	{
		return getColorScheme().getRGB( getPaletteValueForRaw( rawValue ) );
	}

	/**
	 * Like {@link #getRGBForRaw(float)}, but carrying the color stop's own
	 * alpha component instead of forcing full opacity -- see
	 * {@link ColorScheme#getRGBA(float)}.
	 */
	public final int getRGBAForRaw( final float rawValue )
	{
		return getColorScheme().getRGBA( getPaletteValueForRaw( rawValue ) );
	}

	private float applyBoundary( final BoundaryCondition condition, final float rawValue, final float specialValue )
	{
		switch ( condition )
		{
			case CYCLE:
				return toPaletteValue( cycled( rawValue ) );
			case SPECIAL:
				return specialValue;
			case CLAMP:
			default:
				// Both a color scheme and a PresetFunc already clamp an
				// out-of-domain input to their nearest edge, so passing the
				// value straight through is CLAMP's actual behavior.
				return toPaletteValue( rawValue );
		}
	}

	/**
	 * {@code rawValue} wrapped back into {@code [rawDomainMin(), rawDomainMax())}.
	 * <p>
	 * Uses the "remainder, then add the modulus back if still negative" idiom
	 * (as {@code MappingModel#cyclicOffset} already does) rather than a plain
	 * {@code %}, which returns a <em>negative</em> result for a negative
	 * dividend -- e.g. {@code -0.5 % 3 == -0.5}, still outside the domain it
	 * was supposed to wrap into. {@link Math#floorMod(int, int)} would be the
	 * ready-made equivalent, but it has no floating-point overload.
	 */
	private float cycled( final float rawValue )
	{
		final float domainMin = rawDomainMin();
		final float period = rawDomainMax() - domainMin;
		float offset = ( rawValue - domainMin ) % period;
		if ( offset < 0 )
			offset += period;
		return domainMin + offset;
	}
}
