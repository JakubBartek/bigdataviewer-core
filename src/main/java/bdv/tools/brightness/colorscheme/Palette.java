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

import java.util.Arrays;

import net.imglib2.display.ColorTable;
import net.imglib2.type.numeric.ARGBType;

/**
 * An immutable, ordered list of evenly spaced packed-ARGB color stops, plus
 * whether they are blended ({@link #isInterpolated()}).
 * <p>
 * Deliberately not a {@link ColorTable}: its {@code lookupARGB(min, max, value)}
 * maps raw values, which could only be answered here by assuming a linear
 * transfer function.
 * <p>
 * Subclasses only add named constructors; the value semantics are
 * {@code final}.
 *
 * @see ContinuousColorScheme
 * @see DiscreteColorScheme
 */
public class Palette
{
	/** Black-to-white gradient, used until a palette is chosen. */
	public static final Palette DEFAULT = new Palette(
			new int[] { ARGBType.rgba( 0, 0, 0, 255 ), ARGBType.rgba( 255, 255, 255, 255 ) }, true );

	/** Packed ARGB; always at least 2. */
	private final int[] stops;

	private final boolean interpolated;

	/**
	 * @param stops        packed ARGB, in order; at least 2. Copied.
	 * @param interpolated blended (viridis) rather than categorical (tab10).
	 * @throws IllegalArgumentException if there are fewer than 2 stops.
	 */
	public Palette( final int[] stops, final boolean interpolated )
	{
		if ( stops.length < 2 )
			throw new IllegalArgumentException( "a palette needs at least 2 color stops, got " + stops.length );
		this.stops = stops.clone();
		this.interpolated = interpolated;
	}

	/** One stop per {@code colorTable} entry; always {@link #isInterpolated()}. */
	public static Palette of( final ColorTable colorTable )
	{
		final int n = colorTable.getLength();
		// RGB-only tables (e.g. grayscale ColorTable8) are opaque
		final boolean hasAlpha = colorTable.getComponentCount() > ColorTable.ALPHA;
		final int[] argb = new int[ n ];
		for ( int i = 0; i < n; i++ )
			argb[ i ] = ARGBType.rgba(
					colorTable.get( ColorTable.RED, i ),
					colorTable.get( ColorTable.GREEN, i ),
					colorTable.get( ColorTable.BLUE, i ),
					hasAlpha ? colorTable.get( ColorTable.ALPHA, i ) : 255 );
		return new Palette( argb, true );
	}

	/** The number of color stops. */
	public final int getLength()
	{
		return stops.length;
	}

	/** The packed-ARGB color stop at {@code index}. */
	public final int getStop( final int index )
	{
		return stops[ index ];
	}

	/** A copy of the stops. */
	public final int[] getStops()
	{
		return stops.clone();
	}

	/**
	 * Picks {@link ContinuousColorScheme} over {@link DiscreteColorScheme}.
	 * Declared by the palette (a resource's {@code color_interpolation}), never
	 * by the user.
	 */
	public final boolean isInterpolated()
	{
		return interpolated;
	}

	/** Equal stops and flag; {@code LutPalettes#findName} relies on this. */
	@Override
	public final boolean equals( final Object obj )
	{
		if ( this == obj )
			return true;
		if ( !( obj instanceof Palette ) )
			return false;
		final Palette other = ( Palette ) obj;
		return interpolated == other.interpolated && Arrays.equals( stops, other.stops );
	}

	@Override
	public final int hashCode()
	{
		return 31 * Arrays.hashCode( stops ) + Boolean.hashCode( interpolated );
	}

	@Override
	public String toString()
	{
		return "Palette[" + stops.length + " stops, " + ( interpolated ? "interpolated" : "discrete" ) + "]";
	}
}
