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

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorTable8;

/**
 * Test cases for {@link ColorTableLut}.
 */
public class ColorTableLutTest
{
	@Test
	public void testEndpoints()
	{
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 1.0, 1.0 } );

		Assert.assertEquals( 0xff000000, lut.lookupARGB( 0, 255, 0 ) );
		Assert.assertEquals( 0xffff0000, lut.lookupARGB( 0, 255, 255 ) );
	}

	@Test
	public void testIsInterpolatedDefaultsToTrue()
	{
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 1.0, 1.0 } );

		Assert.assertTrue( lut.isInterpolated() );
		Assert.assertTrue( ColorTableLut.isInterpolated( lut ) );
	}

	@Test
	public void testIsInterpolatedHonorsExplicitConstructorArgument()
	{
		final ColorTableLut qualitative = new ColorTableLut(
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 1.0, 1.0 },
				false );

		Assert.assertFalse( qualitative.isInterpolated() );
		Assert.assertFalse( ColorTableLut.isInterpolated( qualitative ) );
	}

	@Test
	public void testIsInterpolatedDefaultsToTrueForNonLutPaletteColorTables()
	{
		Assert.assertTrue( ColorTableLut.isInterpolated( new ColorTable8() ) );
	}

	@Test
	public void testInterpolatesBetweenControlPoints()
	{
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 1.0, 1.0 } );

		final int argb = lut.lookupARGB( 0, 255, 127.5 );
		final int r = ( argb >> 16 ) & 0xFF;
		Assert.assertEquals( 128, r, 1 );
	}

	@Test
	public void testLookupARGBTreatsStopsAsEvenlySpaced()
	{
		// A sparse, unevenly spaced (gaps of 0.2 and 0.8) categorical-style
		// palette. lookupARGB delegates to ContinuousColorScheme, which always
		// treats stops as evenly spaced -- get/getResampled still honor the
		// actual positions (see e.g. LutPalettesTest).
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 0.2, 1.0 },
				new double[] { 1.0, 0.0, 0.0 },
				new double[] { 0.0, 1.0, 0.0 },
				new double[] { 0.0, 0.0, 1.0 },
				new double[] { 1.0, 1.0, 1.0 } );

		Assert.assertEquals( 0xffff0000, lut.lookupARGB( 0, 1, 0.0 ) );
		Assert.assertEquals( 0xff0000ff, lut.lookupARGB( 0, 1, 1.0 ) );
		// the middle stop (green) sits at the evenly-spaced midpoint (t=0.5),
		// not at its actual position (0.2)
		Assert.assertEquals( 0xff00ff00, lut.lookupARGB( 0, 1, 0.5 ) );
		// at the stop's actual (unevenly-spaced) position, the result is a
		// blend toward it rather than its exact color
		Assert.assertEquals( 0xff996600, lut.lookupARGB( 0, 1, 0.2 ) );
	}

	@Test
	public void testClampsOutOfRangeValues()
	{
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 1.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 0.0, 0.0 },
				new double[] { 1.0, 1.0 } );

		Assert.assertEquals( lut.lookupARGB( 0, 255, 0 ), lut.lookupARGB( 0, 255, -50 ) );
		Assert.assertEquals( lut.lookupARGB( 0, 255, 255 ), lut.lookupARGB( 0, 255, 500 ) );
	}

	@Test( expected = IllegalArgumentException.class )
	public void testRejectsTooFewControlPoints()
	{
		new ColorTableLut( new double[] { 0.0 }, new double[] { 0.0 }, new double[] { 0.0 }, new double[] { 0.0 }, new double[] { 1.0 } );
	}

}
