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
 * An ordered list of evenly spaced color stops, plus whether they are meant to
 * be blended or used as individually chosen colors
 * ({@link #isInterpolated()}). An immutable value object: the raw material a
 * {@link ColorScheme} is built from, and the unit the LUT editor loads, names,
 * compares and hands around.
 * <p>
 * Deliberately not a {@link ColorTable}, though it is loaded from
 * {@code ColorTable}-shaped resources and can be built from one (see
 * {@link #of(ColorTable)}). {@code ColorTable} additionally requires
 * {@code lookupARGB(min, max, value)} -- a raw image value against a display
 * range -- which is a raw-value-mapping concern owned by
 * {@code bdv.tools.brightness.palette}'s wrappers, and which could only ever be
 * answered here by assuming a linear transfer function, silently wrong for any
 * other {@code PresetFunc}. A palette is the colors alone; what raw value
 * reaches which color is decided elsewhere.
 * <p>
 * Stops are always evenly spaced. The predecessor of this class
 * ({@code ColorTableLut}) carried an explicit position per stop, but nothing
 * ever produced an unevenly spaced one -- the bundled resources are a plain
 * ordered color list -- and its own {@code lookupARGB} already ignored the
 * positions, so the capability is not reproduced here.
 *
 * @see ContinuousColorScheme
 * @see DiscreteColorScheme
 */
public final class Palette
{
	/**
	 * A black-to-white gradient, the placeholder used whenever no real palette
	 * has been chosen yet. Safe to share, as {@code Palette} is immutable.
	 */
	public static final Palette DEFAULT = new Palette(
			new int[] { ARGBType.rgba( 0, 0, 0, 255 ), ARGBType.rgba( 255, 255, 255, 255 ) }, true );

	/** Color stops, packed ARGB (see {@link ARGBType#rgba(int, int, int, int)}); always at least 2. */
	private final int[] stops;

	private final boolean interpolated;

	/**
	 * @param stops        the color stops, packed ARGB, in order; at least 2. Copied, so the
	 *                     caller's array stays its own.
	 * @param interpolated whether these stops are meant to be smoothly blended (a continuous
	 *                     palette like viridis) rather than used as discrete, individually
	 *                     chosen colors (a qualitative/categorical palette like tab10); see
	 *                     {@link #isInterpolated()}.
	 * @throws IllegalArgumentException if there are fewer than 2 stops.
	 */
	public Palette( final int[] stops, final boolean interpolated )
	{
		if ( stops.length < 2 )
			throw new IllegalArgumentException( "a palette needs at least 2 color stops, got " + stops.length );
		this.stops = stops.clone();
		this.interpolated = interpolated;
	}

	/**
	 * The palette holding {@code colorTable}'s entries, one stop per entry, in
	 * order -- the single adapter from a foreign {@link ColorTable} (e.g.
	 * imglib2's own {@link net.imglib2.display.ColorTable8}, which
	 * {@code BigDataViewer} sets up its default grayscale converter with).
	 * <p>
	 * A plain {@code ColorTable} has no notion of being categorical, so the
	 * result is {@link #isInterpolated()}.
	 */
	public static Palette of( final ColorTable colorTable )
	{
		final int n = colorTable.getLength();
		// Some tables (e.g. the default grayscale ColorTable8) carry only RGB,
		// with no ALPHA component; those stops are fully opaque.
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
	public int getLength()
	{
		return stops.length;
	}

	/** The packed-ARGB color stop at {@code index}. */
	public int getStop( final int index )
	{
		return stops[ index ];
	}

	/** The color stops, packed ARGB, in order. A copy: {@code Palette} is immutable. */
	public int[] getStops()
	{
		return stops.clone();
	}

	/**
	 * Whether these stops are meant to be smoothly blended rather than used as
	 * discrete, individually chosen colors. Declared by the palette itself (a
	 * bundled resource's {@code color_interpolation} field), and what picks
	 * between a {@link ContinuousColorScheme} and a {@link DiscreteColorScheme}
	 * for it -- never a user choice.
	 */
	public boolean isInterpolated()
	{
		return interpolated;
	}

	/**
	 * Two palettes are equal when they have exactly the same stops in the same
	 * order and the same {@link #isInterpolated()} flag. This is how a loaded
	 * palette's resource name is recovered when only the colors are known (see
	 * {@code LutPalettes#findName}).
	 */
	@Override
	public boolean equals( final Object obj )
	{
		if ( this == obj )
			return true;
		if ( !( obj instanceof Palette ) )
			return false;
		final Palette other = ( Palette ) obj;
		return interpolated == other.interpolated && Arrays.equals( stops, other.stops );
	}

	@Override
	public int hashCode()
	{
		return 31 * Arrays.hashCode( stops ) + Boolean.hashCode( interpolated );
	}

	@Override
	public String toString()
	{
		return "Palette[" + stops.length + " stops, " + ( interpolated ? "interpolated" : "discrete" ) + "]";
	}
}
