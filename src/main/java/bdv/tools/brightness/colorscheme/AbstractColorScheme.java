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
package bdv.tools.brightness.colorscheme;

import net.imglib2.display.ColorTable;
import net.imglib2.type.numeric.ARGBType;

/**
 * Shared color-stop storage and RGB/RGBA mechanics for
 * {@link DiscreteColorScheme} and {@link ContinuousColorScheme}: the two only
 * differ in how a palette value resolves to a stop (or a blend of two) --
 * see {@link #colorAt(float)} -- and in {@link IColorScheme#getDelkaIntervalu()}.
 * Package-private: an implementation detail, not part of the public API in
 * {@link IColorScheme}.
 */
abstract class AbstractColorScheme implements IColorScheme
{
	/** Color stops, packed ARGB (see {@link ARGBType#rgba(int, int, int, int)}); always at least 2. */
	final int[] stops;

	AbstractColorScheme( final int[] argbStops )
	{
		if ( argbStops.length < 2 )
			throw new IllegalArgumentException( "a color scheme needs at least 2 color stops, got " + argbStops.length );
		this.stops = argbStops.clone();
	}

	/**
	 * Builds the color stops from an existing palette (see
	 * {@code bdv.tools.brightness.LutPalettes}/{@code ColorTableLut}), one
	 * stop per {@link ColorTable} entry, in order -- the reuse path so a
	 * scheme can be built directly from whatever this project already loads
	 * a palette into, without a separate "color list" format.
	 */
	AbstractColorScheme( final ColorTable colorTable )
	{
		this( stopsOf( colorTable ) );
	}

	private static int[] stopsOf( final ColorTable colorTable )
	{
		final int n = colorTable.getLength();
		final int[] argb = new int[ n ];
		for ( int i = 0; i < n; i++ )
			argb[ i ] = ARGBType.rgba(
					colorTable.get( ColorTable.RED, i ),
					colorTable.get( ColorTable.GREEN, i ),
					colorTable.get( ColorTable.BLUE, i ),
					colorTable.get( ColorTable.ALPHA, i ) );
		return argb;
	}

	@Override
	public final int getRGBA( final float paletteValue )
	{
		return colorAt( paletteValue );
	}

	@Override
	public final int getRGB( final float paletteValue )
	{
		return colorAt( paletteValue ) | 0xff000000;
	}

	/**
	 * The packed ARGB color at {@code paletteValue}. {@code paletteValue} is
	 * not assumed to already be inside this scheme's domain -- implementations
	 * clamp it themselves, so a value outside {@code [0, getDelkaIntervalu()]}
	 * (or half-open equivalent) resolves to its nearest edge stop rather than
	 * throwing or reading out of bounds.
	 */
	abstract int colorAt( float paletteValue );

	/** Linearly interpolates each channel independently between two packed-ARGB stops, {@code t} in {@code [0, 1]}. */
	static int interpolateColor( final int fromARGB, final int toARGB, final float t )
	{
		final int r = interpolateChannel( ARGBType.red( fromARGB ), ARGBType.red( toARGB ), t );
		final int g = interpolateChannel( ARGBType.green( fromARGB ), ARGBType.green( toARGB ), t );
		final int b = interpolateChannel( ARGBType.blue( fromARGB ), ARGBType.blue( toARGB ), t );
		final int a = interpolateChannel( ARGBType.alpha( fromARGB ), ARGBType.alpha( toARGB ), t );
		return ARGBType.rgba( r, g, b, a );
	}

	private static int interpolateChannel( final int from, final int to, final float t )
	{
		return Math.round( from + t * ( to - from ) );
	}
}
