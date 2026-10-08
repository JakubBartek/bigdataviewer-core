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
package bdv.tools.brightness;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.colorscheme.Palette;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

/** Loads the actual bundled LUT resources. */
public class LutPalettesTest
{
	@Test
	public void testDiscoverNamesFindsKnownPalettes()
	{
		final List< String > names = LutPalettes.discoverNames();

		Assert.assertTrue( names.contains( "Accent" ) );
		Assert.assertTrue( names.contains( "viridis" ) );
		Assert.assertTrue( names.contains( "tab10" ) );
	}

	@Test
	public void testDiscoverNamesIsSortedCaseInsensitively()
	{
		final List< String > names = LutPalettes.discoverNames();

		for ( int i = 1; i < names.size(); i++ )
			Assert.assertTrue( names.get( i - 1 ).compareToIgnoreCase( names.get( i ) ) <= 0 );
	}

	@Test
	public void testLoadReturnsNullForUnknownName()
	{
		Assert.assertNull( LutPalettes.load( "this-palette-does-not-exist" ) );
	}

	/** A case-insensitive filesystem would find {@code gray.json}; {@code LutEditorDialog} relies on {@code null}. */
	@Test
	public void testLoadDoesNotMatchGrayToBundledGray()
	{
		Assert.assertNotNull( LutPalettes.load( "gray" ) );
		Assert.assertNull( LutPalettes.load( "Gray" ) );
	}

	@Test
	public void testLoadMatchesNamesCaseSensitively()
	{
		final List< String > names = LutPalettes.discoverNames();
		final Set< String > exact = new HashSet<>( names );
		for ( final String name : names )
		{
			final Set< String > variants = new HashSet<>();
			variants.add( name.toUpperCase( Locale.ROOT ) );
			variants.add( name.toLowerCase( Locale.ROOT ) );
			variants.add( swapCase( name.substring( 0, 1 ) ) + name.substring( 1 ) );
			for ( final String variant : variants )
				if ( !exact.contains( variant ) )
					Assert.assertNull( "load( \"" + variant + "\" ) should not resolve to " + name, LutPalettes.load( variant ) );
		}
	}

	private static String swapCase( final String s )
	{
		final StringBuilder sb = new StringBuilder( s.length() );
		for ( final char c : s.toCharArray() )
			sb.append( Character.isUpperCase( c ) ? Character.toLowerCase( c ) : Character.toUpperCase( c ) );
		return sb.toString();
	}

	/** Accent.json has 8 entries; count and order are preserved. */
	@Test
	public void testLoadParsesFixesRGBA()
	{
		final Palette lut = LutPalettes.load( "Accent" );

		Assert.assertNotNull( lut );
		Assert.assertEquals( 8, lut.getLength() );

		// index "0": [0.4980392156862745, 0.788235294117647, 0.4980392156862745, 1.0]
		final int argb = lut.getStop( 0 );
		Assert.assertEquals( 127, ARGBType.red( argb ) );
		Assert.assertEquals( 201, ARGBType.green( argb ) );
		Assert.assertEquals( 127, ARGBType.blue( argb ) );
		Assert.assertEquals( 255, ARGBType.alpha( argb ) );
	}

	/** Value comparison only: a {@link Palette} is immutable, so sharing an instance is fine. */
	@Test
	public void testLoadIsRepeatable()
	{
		final Palette first = LutPalettes.load( "tab10" );
		final Palette second = LutPalettes.load( "tab10" );

		Assert.assertEquals( first, second );
		Assert.assertEquals( first.getLength(), second.getLength() );
	}

	@Test
	public void testLoadHandlesLargeContinuousPalette()
	{
		final Palette lut = LutPalettes.load( "viridis" );

		Assert.assertNotNull( lut );
		Assert.assertEquals( 256, lut.getLength() );
	}

	@Test
	public void testLoadReflectsColorInterpolationDeclaration()
	{
		Assert.assertFalse( LutPalettes.load( "Accent" ).isInterpolated() );
		Assert.assertTrue( LutPalettes.load( "viridis" ).isInterpolated() );
	}

	/** By value, not identity. */
	@Test
	public void testFindNameRecoversLoadedPalettesName()
	{
		Assert.assertEquals( "tab10", LutPalettes.findName( LutPalettes.load( "tab10" ) ) );
		Assert.assertEquals( "viridis", LutPalettes.findName( LutPalettes.load( "viridis" ) ) );
	}

	@Test
	public void testFindNameReturnsNullForUnmatchedPalette()
	{
		Assert.assertNull( LutPalettes.findName( Palette.DEFAULT ) );
	}

	/** The default {@link ColorTable8} ramp is exactly the bundled {@code gist_gray}. */
	@Test
	public void testFindNameMatchesPaletteAdaptedFromForeignColorTable()
	{
		Assert.assertEquals( "gist_gray", LutPalettes.findName( Palette.of( new ColorTable8() ) ) );
	}

	/** The cache survives the first call. */
	@Test
	public void testFindNameCacheIsReusable()
	{
		Assert.assertEquals( "tab10", LutPalettes.findName( LutPalettes.load( "tab10" ) ) );

		final Palette first = LutPalettes.load( "tab10" );
		Assert.assertEquals( "tab10", LutPalettes.findName( first ) );
		Assert.assertEquals( "viridis", LutPalettes.findName( LutPalettes.load( "viridis" ) ) );
	}
}
