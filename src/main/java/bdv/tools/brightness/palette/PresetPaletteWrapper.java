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
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.presetfunc.PresetFunc;

/**
 * Maps a raw image value to a color through a {@link PresetFunc} (raw value
 * -> palette value) and then a {@link ColorScheme} (palette value -> color),
 * owning what neither of those is allowed to know about: what happens when a
 * raw value falls outside the preset function's domain on either side
 * ({@link #getLeftBoundaryCondition()}/{@link #getRightBoundaryCondition()}).
 * <p>
 * Pipeline: {@code rawValue -> boundary handling -> PresetFunc -> paletteValue
 * -> ColorScheme -> RGB/RGBA}. A raw value strictly below
 * {@link PresetFunc#getMin()} or strictly above {@link PresetFunc#getMax()}
 * has its boundary condition applied first; a value inside the range goes
 * straight to {@link PresetFunc#getPaletteValueForRaw(double)}.
 * <p>
 * Whether the palette value blends between stops or snaps to one is entirely
 * the {@link ColorScheme}'s call, not this wrapper's: with a
 * {@link ContinuousColorScheme} the preset function's output is interpolated
 * (a smooth gradient); with a {@link DiscreteColorScheme} the very same output
 * is floored to a single stop (a categorical palette showing the curve's value
 * as a stepped color). The wrapper is identical either way -- the
 * discrete-vs-continuous distinction lives in the scheme alone.
 * <p>
 * Responsibility split: the {@link ColorScheme} only ever turns a palette value
 * into a color; the {@link PresetFunc} only ever turns an in-range raw value
 * into a palette value; this class only ever decides what an out-of-range raw
 * value resolves to and then delegates to the other two. The boundary
 * conditions live here, never in the color scheme or the preset function.
 * <p>
 * This is the only {@link PaletteWrapper} implementation (a discrete- and a
 * continuous-specific one used to exist separately; the discrete-vs-continuous
 * difference turned out to be entirely the {@link ColorScheme}'s, so they
 * merged into this one class), so it implements the interface directly rather
 * than through an intermediate abstract base.
 */
public class PresetPaletteWrapper implements PaletteWrapper
{
	/** Default {@link BoundaryCondition#SPECIAL} color: fully transparent, so an out-of-range value renders as "nothing there" (a background) until a color is chosen. */
	public static final int DEFAULT_SPECIAL_COLOR = 0x00000000;

	private ColorScheme colorScheme;

	private PresetFunc presetFunc;

	private BoundaryCondition leftBoundaryCondition;

	private BoundaryCondition rightBoundaryCondition;

	private int leftSpecialColor = DEFAULT_SPECIAL_COLOR;

	private int rightSpecialColor = DEFAULT_SPECIAL_COLOR;

	/**
	 * @param colorScheme            the palette this wrapper feeds; {@code paletteValue -> color} lives
	 *                               entirely there (see the class javadoc). A {@link DiscreteColorScheme}
	 *                               floors the preset function's output to a stop, a
	 *                               {@link ContinuousColorScheme} interpolates it.
	 * @param presetFunc             the transform this wrapper feeds; {@code rawValue -> paletteValue} for
	 *                               values inside {@code [presetFunc.getMin(), presetFunc.getMax()]} lives
	 *                               entirely there (see the class javadoc).
	 * @param leftBoundaryCondition  applied when {@code rawValue < presetFunc.getMin()}.
	 * @param rightBoundaryCondition applied when {@code rawValue > presetFunc.getMax()}.
	 * @throws IllegalArgumentException if {@code presetFunc.getPaletteRangeLength()} does not match
	 *                                  {@code colorScheme.getPaletteRangeLength()} -- the preset function would
	 *                                  then be scaling its output to a different domain than the color
	 *                                  scheme actually has.
	 */
	public PresetPaletteWrapper( final ColorScheme colorScheme, final PresetFunc presetFunc,
	                             final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
		this.presetFunc = Objects.requireNonNull( presetFunc, "presetFunc" );
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
		requireMatchingPaletteRangeLength( colorScheme, presetFunc );
	}

	/**
	 * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
	 */
	public PresetPaletteWrapper( final ColorScheme colorScheme, final PresetFunc presetFunc )
	{
		this( colorScheme, presetFunc, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );
	}

	@Override
	public ColorScheme getColorScheme()
	{
		return colorScheme;
	}

	/** @throws IllegalArgumentException if the new color scheme's {@code getPaletteRangeLength()} no longer matches {@link #getPresetFunc()}'s. */
	public void setColorScheme( final ColorScheme colorScheme )
	{
		Objects.requireNonNull( colorScheme, "colorScheme" );
		requireMatchingPaletteRangeLength( colorScheme, presetFunc );
		this.colorScheme = colorScheme;
	}

	public PresetFunc getPresetFunc()
	{
		return presetFunc;
	}

	/** @throws IllegalArgumentException if the new preset function's {@code getPaletteRangeLength()} no longer matches {@link #getColorScheme()}'s. */
	public void setPresetFunc( final PresetFunc presetFunc )
	{
		Objects.requireNonNull( presetFunc, "presetFunc" );
		requireMatchingPaletteRangeLength( colorScheme, presetFunc );
		this.presetFunc = presetFunc;
	}

	/** Re-ranges the {@link #getPresetFunc()} over {@code [min, max]}, keeping its shape and palette-range length (see {@link PresetFunc#withRange(double, double)}). */
	@Override
	public void setRawDomain( final double min, final double max )
	{
		if ( !( max > min ) )
			throw new IllegalArgumentException( "max must be strictly greater than min, got min=" + min + ", max=" + max );
		this.presetFunc = presetFunc.withRange( min, max );
	}

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
	 * color itself is only observable through {@link #getRGBForRaw(double)}/
	 * {@link #getRGBAForRaw(double)}.
	 */
	@Override
	public double getPaletteValueForRaw( final double rawValue )
	{
		final BoundaryCondition hit = boundaryHit( rawValue );
		if ( hit == BoundaryCondition.CYCLE )
			return presetFunc.getPaletteValueForRaw( cycled( rawValue ) );
		// CLAMP, SPECIAL, or in-domain: convert straight through. Both a color
		// scheme and a PresetFunc already clamp an out-of-domain input to their
		// nearest edge, so this is CLAMP's actual behavior.
		return presetFunc.getPaletteValueForRaw( rawValue );
	}

	/**
	 * The color for a raw image value, fully opaque: the {@link BoundaryCondition#SPECIAL}
	 * color (forced opaque) if the value hit a SPECIAL boundary, otherwise
	 * {@link #getPaletteValueForRaw(double)} looked up in {@link #getColorScheme()}.
	 */
	@Override
	public int getRGBForRaw( final double rawValue )
	{
		final Integer special = specialColorForRaw( rawValue );
		if ( special != null )
			return special | 0xff000000;
		return colorScheme.getRGB( getPaletteValueForRaw( rawValue ) );
	}

	/**
	 * Like {@link #getRGBForRaw(double)}, but carrying alpha instead of forcing
	 * full opacity -- both the color stop's own alpha and, crucially, a
	 * transparent {@link BoundaryCondition#SPECIAL} color (see
	 * {@link ColorScheme#getRGBA(double)}).
	 */
	@Override
	public int getRGBAForRaw( final double rawValue )
	{
		final Integer special = specialColorForRaw( rawValue );
		if ( special != null )
			return special;
		return colorScheme.getRGBA( getPaletteValueForRaw( rawValue ) );
	}

	/** Which boundary {@code rawValue} hits and with what condition, or {@code null} if it is inside the domain. */
	private BoundaryCondition boundaryHit( final double rawValue )
	{
		if ( rawValue < presetFunc.getMin() )
			return leftBoundaryCondition;
		if ( isAboveDomain( rawValue ) )
			return rightBoundaryCondition;
		return null;
	}

	/**
	 * Whether {@code rawValue} is past the right edge of the domain.
	 * <p>
	 * Under {@link BoundaryCondition#CYCLE} the domain is half-open,
	 * {@code [min, max)}: {@code max} represents the same point as {@code min}
	 * (like 360{@code deg} and 0{@code deg} on a color wheel), so it must wrap
	 * back to the first stop rather than resolve to the last one, matching
	 * {@link #cycled(double)}'s own half-open target range. Otherwise (CLAMP or
	 * SPECIAL) the domain is closed: {@code presetFunc.getMax()} maps to
	 * palette value {@code paletteRangeLength}, which the color scheme resolves
	 * to its last stop (a continuous scheme's final stop, or a discrete scheme
	 * flooring {@code N} back to stop {@code N - 1}), so it is still in-domain.
	 */
	private boolean isAboveDomain( final double rawValue )
	{
		return rightBoundaryCondition == BoundaryCondition.CYCLE
				? rawValue >= presetFunc.getMax()
				: rawValue > presetFunc.getMax();
	}

	/** The SPECIAL color for {@code rawValue} if it hit a SPECIAL boundary, else {@code null} (so the palette-value path is used instead). */
	private Integer specialColorForRaw( final double rawValue )
	{
		if ( rawValue < presetFunc.getMin() && leftBoundaryCondition == BoundaryCondition.SPECIAL )
			return leftSpecialColor;
		if ( isAboveDomain( rawValue ) && rightBoundaryCondition == BoundaryCondition.SPECIAL )
			return rightSpecialColor;
		return null;
	}

	/**
	 * {@code rawValue} wrapped back into {@code [presetFunc.getMin(), presetFunc.getMax())}.
	 * <p>
	 * Uses the "remainder, then add the modulus back if still negative" idiom
	 * rather than a plain {@code %}, which returns a <em>negative</em> result
	 * for a negative dividend -- e.g. {@code -0.5 % 3 == -0.5}, still outside
	 * the domain it was supposed to wrap into. {@link Math#floorMod(int, int)}
	 * would be the ready-made equivalent, but it has no floating-point overload.
	 */
	private double cycled( final double rawValue )
	{
		final double domainMin = presetFunc.getMin();
		final double period = presetFunc.getMax() - domainMin;
		double offset = ( rawValue - domainMin ) % period;
		if ( offset < 0 )
			offset += period;
		return domainMin + offset;
	}

	private static void requireMatchingPaletteRangeLength( final ColorScheme colorScheme, final PresetFunc presetFunc )
	{
		if ( colorScheme.getPaletteRangeLength() != presetFunc.getPaletteRangeLength() )
			throw new IllegalArgumentException( "colorScheme.getPaletteRangeLength() (" + colorScheme.getPaletteRangeLength() +
					") must match presetFunc.getPaletteRangeLength() (" + presetFunc.getPaletteRangeLength() + ")" );
	}
}
