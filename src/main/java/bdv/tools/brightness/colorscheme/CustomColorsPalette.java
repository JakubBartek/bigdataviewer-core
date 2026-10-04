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
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness.colorscheme;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.imglib2.type.numeric.ARGBType;

/**
 * A single-color intensity scale: a two-stop ramp from opaque black up to one
 * opaque color, the way grayscale ramps from black up to white. The usual way
 * a fluorescence channel is shown -- brighter means more signal, in the
 * channel's own color.
 * <p>
 * Opaque at both ends, whatever the color it is given carries in its alpha
 * byte. That is what sets it apart from {@link LegacyBdvColorPalette}, the
 * same ramp built to reproduce an old converter, whose black keeps that
 * converter's alpha. For an opaque color the two are the same palette and
 * compare {@link #equals equal}.
 * <p>
 * The palette is {@link #isInterpolated() interpolated}: the ramp is a
 * continuous blend, not two categories.
 *
 * @author Jakub Bartek
 */
public final class CustomColorsPalette extends Palette
{
	private static final Map< String, CustomColorsPalette > CLASSICS = classicsByName();

	private final int color;

	/**
	 * @param rgb the color the ramp rises to, packed as in
	 *            {@link ARGBType#rgba(int, int, int, int)}. Its alpha byte is
	 *            ignored; the ramp is opaque.
	 */
	public CustomColorsPalette( final int rgb )
	{
		super( new int[] { ARGBType.rgba( 0, 0, 0, 255 ), opaque( rgb ) }, true );
		this.color = opaque( rgb );
	}

	/** The opaque color this palette ramps up to from black, packed ARGB. */
	public int getColor()
	{
		return color;
	}

	/**
	 * The single-color scales image analysis tools conventionally offer for a
	 * channel, by display name, in the order a chooser lists them: grayscale,
	 * the additive primaries, their complements, and orange. Unmodifiable.
	 * <p>
	 * The names are deliberately not any bundled palette's, ignoring case:
	 * "gray" and "Grays" are bundled, and on a case-insensitive file system a
	 * bundled resource is found under either spelling.
	 */
	public static Map< String, CustomColorsPalette > classics()
	{
		return CLASSICS;
	}

	private static Map< String, CustomColorsPalette > classicsByName()
	{
		final Map< String, CustomColorsPalette > classics = new LinkedHashMap<>();
		classics.put( "Gray", new CustomColorsPalette( ARGBType.rgba( 255, 255, 255, 255 ) ) );
		classics.put( "Red", new CustomColorsPalette( ARGBType.rgba( 255, 0, 0, 255 ) ) );
		classics.put( "Green", new CustomColorsPalette( ARGBType.rgba( 0, 255, 0, 255 ) ) );
		classics.put( "Blue", new CustomColorsPalette( ARGBType.rgba( 0, 0, 255, 255 ) ) );
		classics.put( "Cyan", new CustomColorsPalette( ARGBType.rgba( 0, 255, 255, 255 ) ) );
		classics.put( "Magenta", new CustomColorsPalette( ARGBType.rgba( 255, 0, 255, 255 ) ) );
		classics.put( "Yellow", new CustomColorsPalette( ARGBType.rgba( 255, 255, 0, 255 ) ) );
		classics.put( "Orange", new CustomColorsPalette( ARGBType.rgba( 255, 128, 0, 255 ) ) );
		return Collections.unmodifiableMap( classics );
	}

	private static int opaque( final int rgb )
	{
		return rgb | 0xff000000;
	}

	@Override
	public String toString()
	{
		return String.format( "CustomColorsPalette[%06x]", color & 0xffffff );
	}
}
