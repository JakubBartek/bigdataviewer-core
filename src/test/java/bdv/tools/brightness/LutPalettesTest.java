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

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.colorscheme.Palette;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link LutPalettes}, loading the actual bundled JSON LUT
 * resources (not just hand-built {@link Palette} instances).
 */
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

	/**
	 * Accent.json's "fixes_RGBA" has 8 entries, each an [r, g, b, a] array in
	 * [0, 1]. Loading it should preserve the color count and the array order
	 * (index 0's color first, etc.).
	 */
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

	/**
	 * Loading the same palette twice yields equal palettes. Deliberately a
	 * value comparison and not {@code assertNotSame}: a {@link Palette} is
	 * immutable, so whether the two calls share an instance is no longer
	 * something a caller can observe or needs protecting from (it was, back
	 * when this handed out a mutable-in-principle {@code ColorTable}).
	 */
	@Test
	public void testLoadIsRepeatable()
	{
		final Palette first = LutPalettes.load( "tab10" );
		final Palette second = LutPalettes.load( "tab10" );

		Assert.assertEquals( first, second );
		Assert.assertEquals( first.getLength(), second.getLength() );
	}

	/**
	 * A large, continuous palette (256 colors) should load with all of its
	 * colors, not truncated/resampled to some other resolution.
	 */
	@Test
	public void testLoadHandlesLargeContinuousPalette()
	{
		final Palette lut = LutPalettes.load( "viridis" );

		Assert.assertNotNull( lut );
		Assert.assertEquals( 256, lut.getLength() );
	}

	/**
	 * Accent.json declares {@code "color_interpolation": false} (it is a
	 * qualitative/categorical palette); viridis.json declares {@code true}
	 * (a continuous palette). {@link #load(String)} parses the file once and
	 * carries the flag on the returned palette itself (see
	 * {@link Palette#isInterpolated()}), rather than requiring a
	 * second, separate parse to find it out.
	 */
	@Test
	public void testLoadReflectsColorInterpolationDeclaration()
	{
		Assert.assertFalse( LutPalettes.load( "Accent" ).isInterpolated() );
		Assert.assertTrue( LutPalettes.load( "viridis" ).isInterpolated() );
	}

	/**
	 * {@link LutPalettes#findName} is the reverse of {@link LutPalettes#load}:
	 * given just a loaded palette (as read back from a converter that doesn't
	 * itself remember which resource it came from), it should recover the
	 * same name -- by a genuine value comparison, not identity.
	 */
	@Test
	public void testFindNameRecoversLoadedPalettesName()
	{
		Assert.assertEquals( "tab10", LutPalettes.findName( LutPalettes.load( "tab10" ) ) );
		Assert.assertEquals( "viridis", LutPalettes.findName( LutPalettes.load( "viridis" ) ) );
	}

	/**
	 * A palette that isn't one of the bundled resources at all (e.g. the
	 * generic placeholder used before any real palette is chosen) has no
	 * name to find.
	 */
	@Test
	public void testFindNameReturnsNullForUnmatchedPalette()
	{
		Assert.assertNull( LutPalettes.findName( Palette.DEFAULT ) );
	}

	/**
	 * A palette adapted from a foreign {@link net.imglib2.display.ColorTable}
	 * is matched on its colors like any other. imglib2's default
	 * {@link ColorTable8} is a 256-entry grayscale ramp, which is exactly the
	 * bundled {@code gist_gray} resource, so that is the name it recovers.
	 * <p>
	 * This is a deliberate change: {@code findName} used to reject anything
	 * that was not one of this project's own table instances outright, so the
	 * grayscale palette BigDataViewer sets its default converter up with came
	 * back unnamed even though a bundled resource matched it exactly. Matching
	 * on colors is what the method is documented to do.
	 */
	@Test
	public void testFindNameMatchesPaletteAdaptedFromForeignColorTable()
	{
		Assert.assertEquals( "gist_gray", LutPalettes.findName( Palette.of( new ColorTable8() ) ) );
	}

	/**
	 * {@link LutPalettes#findName} caches the parsed palettes internally (it
	 * is called on the EDT on every source change, and would otherwise
	 * re-parse every bundled resource each time). This pins down that the
	 * cache is actually reusable rather than consumed by the first call, and
	 * that a freshly loaded palette still matches it afterwards.
	 */
	@Test
	public void testFindNameCacheIsReusable()
	{
		Assert.assertEquals( "tab10", LutPalettes.findName( LutPalettes.load( "tab10" ) ) );

		final Palette first = LutPalettes.load( "tab10" );
		Assert.assertEquals( "tab10", LutPalettes.findName( first ) );
		Assert.assertEquals( "viridis", LutPalettes.findName( LutPalettes.load( "viridis" ) ) );
	}
}
