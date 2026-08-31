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

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link Palette}: the stop list itself, the
 * {@link Palette#of(net.imglib2.display.ColorTable) adapter} from a foreign
 * {@code ColorTable}, and the value equality {@code LutPalettes#findName}
 * relies on. How a palette's stops turn into colors is a
 * {@link ColorScheme} concern, covered in {@link ContinuousColorSchemeTest}/
 * {@link DiscreteColorSchemeTest} rather than here.
 */
public class PaletteTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	@Test
	public void testKeepsStopsInOrder()
	{
		final Palette palette = new Palette( new int[] { RED, GREEN }, true );

		Assert.assertEquals( 2, palette.getLength() );
		Assert.assertEquals( RED, palette.getStop( 0 ) );
		Assert.assertEquals( GREEN, palette.getStop( 1 ) );
		Assert.assertArrayEquals( new int[] { RED, GREEN }, palette.getStops() );
	}

	@Test( expected = IllegalArgumentException.class )
	public void testRejectsTooFewStops()
	{
		new Palette( new int[] { RED }, true );
	}

	/**
	 * A {@code Palette} is immutable, which is what lets
	 * {@code LutPalettes#findName} share one cached instance between callers.
	 * Neither the array it was built from nor the one it hands out may be a
	 * way back in.
	 */
	@Test
	public void testIsImmutable()
	{
		final int[] source = { RED, GREEN };
		final Palette palette = new Palette( source, true );

		source[ 0 ] = GREEN;
		Assert.assertEquals( RED, palette.getStop( 0 ) );

		palette.getStops()[ 1 ] = RED;
		Assert.assertEquals( GREEN, palette.getStop( 1 ) );
	}

	@Test
	public void testInterpolatedFlagIsCarried()
	{
		Assert.assertTrue( new Palette( new int[] { RED, GREEN }, true ).isInterpolated() );
		Assert.assertFalse( new Palette( new int[] { RED, GREEN }, false ).isInterpolated() );
	}

	@Test
	public void testDefaultIsAnInterpolatedBlackToWhiteRamp()
	{
		Assert.assertEquals( 2, Palette.DEFAULT.getLength() );
		Assert.assertTrue( Palette.DEFAULT.isInterpolated() );
		Assert.assertEquals( 0xff000000, Palette.DEFAULT.getStop( 0 ) );
		Assert.assertEquals( 0xffffffff, Palette.DEFAULT.getStop( 1 ) );
	}

	// -- equality ------------------------------------------------------------

	/**
	 * Value equality is what recovers a palette's resource name from its
	 * colors alone (see {@code LutPalettes#findName}), so it has to hold
	 * across separately built instances -- identity would defeat the purpose.
	 */
	@Test
	public void testEqualityIsByValue()
	{
		final Palette a = new Palette( new int[] { RED, GREEN }, true );
		final Palette b = new Palette( new int[] { RED, GREEN }, true );

		Assert.assertEquals( a, b );
		Assert.assertEquals( a.hashCode(), b.hashCode() );
	}

	@Test
	public void testDiffersByStopsAndByInterpolatedFlag()
	{
		final Palette base = new Palette( new int[] { RED, GREEN }, true );

		Assert.assertNotEquals( base, new Palette( new int[] { GREEN, RED }, true ) );
		Assert.assertNotEquals( base, new Palette( new int[] { RED, GREEN, RED }, true ) );
		// The flag is part of a palette's identity: the same colors meant as
		// discrete categories are a different palette from the same colors
		// meant as a gradient.
		Assert.assertNotEquals( base, new Palette( new int[] { RED, GREEN }, false ) );
	}

	// -- adapting a foreign ColorTable ---------------------------------------

	/**
	 * imglib2's default {@link ColorTable8} carries only RGB (3 components,
	 * no ALPHA) and is the table {@code BigDataViewer} sets its default
	 * converter up with, so adapting it must fill in full opacity rather than
	 * reach for a component that isn't there.
	 */
	@Test
	public void testOfColorTableWithoutAlphaComponentIsOpaque()
	{
		final ColorTable8 grayscale = new ColorTable8();
		Assert.assertEquals( 3, grayscale.getComponentCount() );

		final Palette palette = Palette.of( grayscale );

		Assert.assertEquals( 256, palette.getLength() );
		Assert.assertEquals( 0xff000000, palette.getStop( 0 ) );
		Assert.assertEquals( 0xffffffff, palette.getStop( 255 ) );
		Assert.assertEquals( 255, ARGBType.alpha( palette.getStop( 128 ) ) );
	}

	/** A plain {@code ColorTable} declares no categorical intent, so an adapted palette is interpolated. */
	@Test
	public void testOfColorTableIsInterpolated()
	{
		Assert.assertTrue( Palette.of( new ColorTable8() ).isInterpolated() );
	}

	@Test
	public void testOfColorTableKeepsEntryOrder()
	{
		final ColorTable8 table = new ColorTable8(
				new byte[] { ( byte ) 255, 0 },
				new byte[] { 0, ( byte ) 255 },
				new byte[] { 0, 0 } );

		final Palette palette = Palette.of( table );

		Assert.assertEquals( 2, palette.getLength() );
		Assert.assertEquals( RED, palette.getStop( 0 ) );
		Assert.assertEquals( GREEN, palette.getStop( 1 ) );
	}
}
