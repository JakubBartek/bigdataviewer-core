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
public class ContinuousPaletteWrapper
{
	private ContinuousColorScheme colorScheme;

	private PresetFunc presetFunc;

	private BoundaryCondition leftBoundaryCondition;

	private BoundaryCondition rightBoundaryCondition;

	private float leftSpecialValue;

	private float rightSpecialValue;

	/**
	 * @param colorScheme            the palette this wrapper feeds; {@code paletteValue -> color} lives
	 *                               entirely there (see the class javadoc).
	 * @param presetFunc             the transform this wrapper feeds; {@code rawValue -> paletteValue} for
	 *                               values inside {@code [presetFunc.getMin(), presetFunc.getMax()]} lives
	 *                               entirely there (see the class javadoc).
	 * @param leftBoundaryCondition  applied when {@code rawValue < presetFunc.getMin()}.
	 * @param rightBoundaryCondition applied when {@code rawValue > presetFunc.getMax()}.
	 * @throws IllegalArgumentException if {@code presetFunc.getDelkaIntervalu()} does not match
	 *                                  {@code colorScheme.getDelkaIntervalu()} -- the preset function would
	 *                                  then be scaling its output to a different domain than the color
	 *                                  scheme actually has.
	 */
	public ContinuousPaletteWrapper( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc,
	                                  final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition )
	{
		this.colorScheme = Objects.requireNonNull( colorScheme, "colorScheme" );
		this.presetFunc = Objects.requireNonNull( presetFunc, "presetFunc" );
		requireMatchingDelkaIntervalu( colorScheme, presetFunc );
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
	}

	/**
	 * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
	 */
	public ContinuousPaletteWrapper( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc )
	{
		this( colorScheme, presetFunc, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );
	}

	public ContinuousColorScheme getColorScheme()
	{
		return colorScheme;
	}

	/** @throws IllegalArgumentException if the new color scheme's {@code getDelkaIntervalu()} no longer matches {@link #getPresetFunc()}'s. */
	public void setColorScheme( final ContinuousColorScheme colorScheme )
	{
		Objects.requireNonNull( colorScheme, "colorScheme" );
		requireMatchingDelkaIntervalu( colorScheme, presetFunc );
		this.colorScheme = colorScheme;
	}

	public PresetFunc getPresetFunc()
	{
		return presetFunc;
	}

	/** @throws IllegalArgumentException if the new preset function's {@code getDelkaIntervalu()} no longer matches {@link #getColorScheme()}'s. */
	public void setPresetFunc( final PresetFunc presetFunc )
	{
		Objects.requireNonNull( presetFunc, "presetFunc" );
		requireMatchingDelkaIntervalu( colorScheme, presetFunc );
		this.presetFunc = presetFunc;
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
	 * The color for a raw image value: applies the left/right boundary
	 * condition if {@code rawValue} falls outside
	 * {@code [presetFunc.getMin(), presetFunc.getMax()]}, otherwise converts it
	 * straight to a palette value via {@link #getPresetFunc()}, then looks up
	 * the result in {@link #getColorScheme()}.
	 */
	public int getRGBForRaw( final float rawValue )
	{
		final float paletteValue;
		if ( rawValue < presetFunc.getMin() )
			paletteValue = apply( leftBoundaryCondition, rawValue, true );
		else if ( rawValue > presetFunc.getMax() )
			paletteValue = apply( rightBoundaryCondition, rawValue, false );
		else
			paletteValue = presetFunc.getPaletteValueForRaw( rawValue );

		return colorScheme.getRGB( paletteValue );
	}

	private float apply( final BoundaryCondition condition, final float rawValue, final boolean isLeft )
	{
		switch ( condition )
		{
			case CYCLE:
				final float span = presetFunc.getMax() - presetFunc.getMin();
				final float cycledRawValue = presetFunc.getMin() + FloatMath.floorMod( rawValue - presetFunc.getMin(), span );
				return presetFunc.getPaletteValueForRaw( cycledRawValue );
			case SPECIAL:
				return isLeft ? leftSpecialValue : rightSpecialValue;
			case CLAMP:
			default:
				// PresetFunc#getPaletteValueForRaw already clamps an out-of-domain
				// raw value to its nearest end, so simply calling it is CLAMP's
				// actual behavior.
				return presetFunc.getPaletteValueForRaw( rawValue );
		}
	}

	private static void requireMatchingDelkaIntervalu( final ContinuousColorScheme colorScheme, final PresetFunc presetFunc )
	{
		if ( colorScheme.getDelkaIntervalu() != presetFunc.getDelkaIntervalu() )
			throw new IllegalArgumentException( "colorScheme.getDelkaIntervalu() (" + colorScheme.getDelkaIntervalu() +
					") must match presetFunc.getDelkaIntervalu() (" + presetFunc.getDelkaIntervalu() + ")" );
	}
}
