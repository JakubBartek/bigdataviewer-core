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

import net.imglib2.display.ColorTable;

/**
 * Test cases for {@link LutPalettes}, loading the actual bundled JSON LUT
 * resources (not just hand-built {@link ColorTableLut} instances).
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
	 * Accent.json's "fixes_RGBA" has 8 entries, keys "0" to "7", each an
	 * [r, g, b, a] array in [0, 1]. Loading it should preserve the color
	 * count and the index order (index 0's color first, etc.).
	 */
	@Test
	public void testLoadParsesFixesRGBA()
	{
		final ColorTable lut = LutPalettes.load( "Accent" );

		Assert.assertNotNull( lut );
		Assert.assertEquals( 8, lut.getLength() );

		// index "0": [0.4980392156862745, 0.788235294117647, 0.4980392156862745, 1.0]
		final int argb = lut.lookupARGB( 0, 255, 0 );
		Assert.assertEquals( 127, ( argb >> 16 ) & 0xFF ); // red
		Assert.assertEquals( 201, ( argb >> 8 ) & 0xFF );  // green
		Assert.assertEquals( 127, argb & 0xFF );           // blue
		Assert.assertEquals( 255, ( argb >> 24 ) & 0xFF );  // alpha
	}

	/**
	 * Loading the same palette twice should not alias any shared mutable
	 * state (each call parses the resource independently).
	 */
	@Test
	public void testLoadIsIndependentAcrossCalls()
	{
		final ColorTable first = LutPalettes.load( "tab10" );
		final ColorTable second = LutPalettes.load( "tab10" );

		Assert.assertNotSame( first, second );
		Assert.assertEquals( first.getLength(), second.getLength() );
	}

	/**
	 * A large, continuous palette (256 colors) should load with all of its
	 * colors, not truncated/resampled to some other resolution.
	 */
	@Test
	public void testLoadHandlesLargeContinuousPalette()
	{
		final ColorTable lut = LutPalettes.load( "viridis" );

		Assert.assertNotNull( lut );
		Assert.assertEquals( 256, lut.getLength() );
	}

	/**
	 * Accent.json declares {@code "color_interpolation": false} (it is a
	 * qualitative/categorical palette); viridis.json declares {@code true}
	 * (a continuous palette). {@link #load(String)} parses the file once and
	 * carries the flag on the returned table itself (see
	 * {@link ColorTableLut#isInterpolated()}), rather than requiring a
	 * second, separate parse to find it out.
	 */
	@Test
	public void testLoadReflectsColorInterpolationDeclaration()
	{
		Assert.assertFalse( ColorTableLut.isInterpolated( LutPalettes.load( "Accent" ) ) );
		Assert.assertTrue( ColorTableLut.isInterpolated( LutPalettes.load( "viridis" ) ) );
	}
}
