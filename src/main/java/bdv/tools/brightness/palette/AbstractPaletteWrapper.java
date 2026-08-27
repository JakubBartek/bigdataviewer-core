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
abstract class AbstractPaletteWrapper implements PaletteWrapper
{
	/** Default {@link BoundaryCondition#SPECIAL} color: fully transparent, so an out-of-range value renders as "nothing there" (a background) until a color is chosen. */
	public static final int DEFAULT_SPECIAL_COLOR = 0x00000000;

	private BoundaryCondition leftBoundaryCondition;

	private BoundaryCondition rightBoundaryCondition;

	private int leftSpecialColor = DEFAULT_SPECIAL_COLOR;

	private int rightSpecialColor = DEFAULT_SPECIAL_COLOR;

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

	/**
	 * The packed-ARGB color a raw value that hits the left boundary resolves
	 * to, when {@link #getLeftBoundaryCondition()} is
	 * {@link BoundaryCondition#SPECIAL}; otherwise unused. Unlike a palette
	 * value, this is a color in its own right, so it can be anything -- a hue
	 * outside the palette, or (via its alpha) transparent, which is how a
	 * "background" out-of-range value is rendered (see {@link #DEFAULT_SPECIAL_COLOR}).
	 */
	public int getLeftSpecialColor()
	{
		return leftSpecialColor;
	}

	public void setLeftSpecialColor( final int leftSpecialColor )
	{
		this.leftSpecialColor = leftSpecialColor;
	}

	/** As {@link #getLeftSpecialColor()}, for a raw value that hits the right boundary. */
	public int getRightSpecialColor()
	{
		return rightSpecialColor;
	}

	public void setRightSpecialColor( final int rightSpecialColor )
	{
		this.rightSpecialColor = rightSpecialColor;
	}

	// -- mapping -------------------------------------------------------------

	/**
	 * The palette value for a raw image value: applies the left/right boundary
	 * condition if {@code rawValue} falls outside this wrapper's domain,
	 * otherwise converts it directly.
	 * <p>
	 * {@link BoundaryCondition#SPECIAL} is a color-level override with no
	 * palette-value counterpart (its color need not be in the palette at all),
	 * so here it behaves like {@link BoundaryCondition#CLAMP}; the special
	 * color itself is only observable through {@link #getRGBForRaw(float)}/
	 * {@link #getRGBAForRaw(float)}.
	 */
	public final float getPaletteValueForRaw( final float rawValue )
	{
		final BoundaryCondition hit = boundaryHit( rawValue );
		if ( hit == BoundaryCondition.CYCLE )
			return toPaletteValue( cycled( rawValue ) );
		// CLAMP, SPECIAL, or in-domain: convert straight through. Both a color
		// scheme and a PresetFunc already clamp an out-of-domain input to their
		// nearest edge, so this is CLAMP's actual behavior.
		return toPaletteValue( rawValue );
	}

	/**
	 * The color for a raw image value, fully opaque: the {@link BoundaryCondition#SPECIAL}
	 * color (forced opaque) if the value hit a SPECIAL boundary, otherwise
	 * {@link #getPaletteValueForRaw(float)} looked up in {@link #getColorScheme()}.
	 */
	public final int getRGBForRaw( final float rawValue )
	{
		final Integer special = specialColorForRaw( rawValue );
		if ( special != null )
			return special | 0xff000000;
		return getColorScheme().getRGB( getPaletteValueForRaw( rawValue ) );
	}

	/**
	 * Like {@link #getRGBForRaw(float)}, but carrying alpha instead of forcing
	 * full opacity -- both the color stop's own alpha and, crucially, a
	 * transparent {@link BoundaryCondition#SPECIAL} color (see
	 * {@link ColorScheme#getRGBA(float)}).
	 */
	public final int getRGBAForRaw( final float rawValue )
	{
		final Integer special = specialColorForRaw( rawValue );
		if ( special != null )
			return special;
		return getColorScheme().getRGBA( getPaletteValueForRaw( rawValue ) );
	}

	/** Which boundary {@code rawValue} hits and with what condition, or {@code null} if it is inside the domain. */
	private BoundaryCondition boundaryHit( final float rawValue )
	{
		if ( rawValue < rawDomainMin() )
			return leftBoundaryCondition;
		if ( isAboveDomain( rawValue ) )
			return rightBoundaryCondition;
		return null;
	}

	/** The SPECIAL color for {@code rawValue} if it hit a SPECIAL boundary, else {@code null} (so the palette-value path is used instead). */
	private Integer specialColorForRaw( final float rawValue )
	{
		if ( rawValue < rawDomainMin() && leftBoundaryCondition == BoundaryCondition.SPECIAL )
			return leftSpecialColor;
		if ( isAboveDomain( rawValue ) && rightBoundaryCondition == BoundaryCondition.SPECIAL )
			return rightSpecialColor;
		return null;
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
