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

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.presetfunc.PresetFunc;

/**
 * Maps a raw image value to a color through a {@link PresetFunc} (raw value
 * -> palette value) and then a {@link ContinuousColorScheme} (palette value
 * -> color), owning what neither of those is allowed to know about: what
 * happens when a raw value falls outside the preset function's domain on
 * either side ({@link #getLeftBoundaryCondition()}/
 * {@link #getRightBoundaryCondition()}).
 * <p>
 * Pipeline: {@code rawValue -> boundary handling -> PresetFunc -> paletteValue
 * -> ContinuousColorScheme -> RGB/RGBA}. A raw value strictly below
 * {@link PresetFunc#getMin()} or strictly above {@link PresetFunc#getMax()}
 * has its boundary condition applied first; a value inside the range goes
 * straight to {@link PresetFunc#getPaletteValueForRaw(float)}.
 * <p>
 * Responsibility split: {@link ContinuousColorScheme} only ever turns a
 * palette value into a color; {@link PresetFunc} only ever turns an in-range
 * raw value into a palette value; this class only ever decides what an
 * out-of-range raw value resolves to and then delegates to the other two. The
 * boundary conditions live here, never in the color scheme or the preset
 * function.
 */
public class ContinuousPaletteWrapper extends AbstractPaletteWrapper
{
	private ContinuousColorScheme colorScheme;

	private PresetFunc presetFunc;

	/**
	 * @param colorScheme            the palette this wrapper feeds; {@code paletteValue -> color} lives
	 *                               entirely there (see the class javadoc).
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
	public ContinuousPaletteWrapper( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc,
	                                  final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		super( leftBoundaryCondition, rightBoundaryCondition );
		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
		this.presetFunc = Objects.requireNonNull( presetFunc, "presetFunc" );
		requireMatchingPaletteRangeLength( colorScheme, presetFunc );
	}

	/**
	 * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
	 */
	public ContinuousPaletteWrapper( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc )
	{
		this( colorScheme, presetFunc, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );
	}

	@Override
	public ContinuousColorScheme getColorScheme()
	{
		return colorScheme;
	}

	@Override
	float rawDomainMin()
	{
		return presetFunc.getMin();
	}

	@Override
	float rawDomainMax()
	{
		return presetFunc.getMax();
	}

	/** The continuous domain is closed ({@code [0, paletteRangeLength]}), so {@link #rawDomainMax()} itself is still inside it. */
	@Override
	boolean isAboveDomain( final float rawValue )
	{
		return rawValue > rawDomainMax();
	}

	@Override
	float toPaletteValue( final float rawValue )
	{
		return presetFunc.getPaletteValueForRaw( rawValue );
	}

	/** @throws IllegalArgumentException if the new color scheme's {@code getPaletteRangeLength()} no longer matches {@link #getPresetFunc()}'s. */
	public void setColorScheme( final ContinuousColorScheme colorScheme )
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

	/** Re-ranges the {@link #getPresetFunc()} over {@code [min, max]}, keeping its shape and palette-range length (see {@link PresetFunc#withRange(float, float)}). */
	@Override
	public void setRawDomain( final double min, final double max )
	{
		if ( !( max > min ) )
			throw new IllegalArgumentException( "max must be strictly greater than min, got min=" + min + ", max=" + max );
		this.presetFunc = presetFunc.withRange( ( float ) min, ( float ) max );
	}

	private static void requireMatchingPaletteRangeLength( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc )
	{
		if ( colorScheme.getPaletteRangeLength() != presetFunc.getPaletteRangeLength() )
			throw new IllegalArgumentException( "colorScheme.getPaletteRangeLength() (" + colorScheme.getPaletteRangeLength() +
					") must match presetFunc.getPaletteRangeLength() (" + presetFunc.getPaletteRangeLength() + ")" );
	}
}
