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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import bdv.tools.brightness.LutPalettes;
import net.imglib2.type.numeric.ARGBType;

public class CustomColorsPaletteTest
{
	@Test
	public void testIsAnOpaqueRampFromBlackToTheColor()
	{
		final int color = ARGBType.rgba( 30, 200, 90, 255 );
		final CustomColorsPalette palette = new CustomColorsPalette( color );

		assertEquals( color, palette.getColor() );
		assertEquals( 2, palette.getLength() );
		assertEquals( ARGBType.rgba( 0, 0, 0, 255 ), palette.getStop( 0 ) );
		assertEquals( color, palette.getStop( 1 ) );
		assertTrue( palette.isInterpolated() );
	}

	/** Unlike {@link LegacyBdvColorPalette}, neither end takes the given color's alpha. */
	@Test
	public void testIgnoresTheColorsAlpha()
	{
		for ( final int alpha : new int[] { 0, 1, 77, 254 } )
		{
			final CustomColorsPalette palette = new CustomColorsPalette( ARGBType.rgba( 255, 0, 0, alpha ) );

			assertEquals( ARGBType.rgba( 255, 0, 0, 255 ), palette.getColor() );
			assertEquals( new CustomColorsPalette( ARGBType.rgba( 255, 0, 0, 255 ) ), palette );
			assertEquals( ARGBType.rgba( 0, 0, 0, 255 ), palette.getStop( 0 ) );
		}
	}

	/** A palette is a value: where its colors came from must not make it unequal to another with the same colors. */
	@Test
	public void testEqualsAnyPaletteWithTheSameStops()
	{
		final int color = ARGBType.rgba( 255, 0, 255, 255 );
		final Palette plain = new Palette( new int[] { ARGBType.rgba( 0, 0, 0, 255 ), color }, true );

		assertEquals( plain, new CustomColorsPalette( color ) );
		assertEquals( plain.hashCode(), new CustomColorsPalette( color ).hashCode() );
		assertEquals( new LegacyBdvColorPalette( color ), new CustomColorsPalette( color ) );
		assertEquals( Palette.DEFAULT, new CustomColorsPalette( ARGBType.rgba( 255, 255, 255, 255 ) ) );
		assertNotEquals( new LegacyBdvColorPalette( ARGBType.rgba( 255, 0, 255, 77 ) ), new CustomColorsPalette( color ) );
	}

	@Test
	public void testClassicsAreDistinctOpaqueRamps()
	{
		final Map< String, CustomColorsPalette > classics = CustomColorsPalette.classics();
		assertEquals( 8, classics.size() );

		final Set< Integer > colors = new HashSet<>();
		for ( final CustomColorsPalette palette : classics.values() )
		{
			assertEquals( ARGBType.rgba( 0, 0, 0, 255 ), palette.getStop( 0 ) );
			assertEquals( 255, ARGBType.alpha( palette.getColor() ) );
			assertTrue( "listed twice: " + palette, colors.add( palette.getColor() ) );
		}
		assertEquals( "Grayscale", classics.keySet().iterator().next() );
	}

	/** Otherwise a case-insensitive filesystem lets a bundled palette shadow it. */
	@Test
	public void testNoClassicIsNamedLikeABundledPalette()
	{
		final Set< String > bundled = new HashSet<>();
		for ( final String name : LutPalettes.discoverNames() )
			bundled.add( name.toLowerCase( Locale.ROOT ) );
		assertFalse( "no bundled palettes found", bundled.isEmpty() );

		for ( final String name : CustomColorsPalette.classics().keySet() )
			assertFalse( name + " is a bundled palette's name", bundled.contains( name.toLowerCase( Locale.ROOT ) ) );
	}

	@Test( expected = UnsupportedOperationException.class )
	public void testClassicsCannotBeChanged()
	{
		CustomColorsPalette.classics().put( "Black", new CustomColorsPalette( 0 ) );
	}
}
