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
	public void testWorksWithFewControlPoints()
	{
		// A sparse, unevenly spaced categorical-style palette.
		final ColorTableLut lut = new ColorTableLut(
				new double[] { 0.0, 0.2, 1.0 },
				new double[] { 1.0, 0.0, 0.0 },
				new double[] { 0.0, 1.0, 0.0 },
				new double[] { 0.0, 0.0, 1.0 },
				new double[] { 1.0, 1.0, 1.0 } );

		Assert.assertEquals( 0xffff0000, lut.lookupARGB( 0, 1, 0.0 ) );
		Assert.assertEquals( 0xff00ff00, lut.lookupARGB( 0, 1, 0.2 ) );
		Assert.assertEquals( 0xff0000ff, lut.lookupARGB( 0, 1, 1.0 ) );
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

	/**
	 * Three distinctly-colored control points at t = 0, 0.5, 1.0.
	 */
	private static ColorTableLut threeColorPalette()
	{
		return new ColorTableLut(
				new double[] { 0.0, 0.5, 1.0 },
				new double[] { 1.0, 0.0, 0.0 },
				new double[] { 0.0, 1.0, 0.0 },
				new double[] { 0.0, 0.0, 1.0 },
				new double[] { 1.0, 1.0, 1.0 } );
	}

	@Test
	public void testStaticLookupInterpolateMatchesPlainLookup()
	{
		final ColorTableLut lut = threeColorPalette();
		Assert.assertEquals( lut.lookupARGB( 0, 1, 0.3 ), ColorTableLut.lookupARGB( lut, 0, 1, 0.3, ValueMatching.INTERPOLATE ) );
	}

	@Test
	public void testStaticLookupTruncateHoldsPreviousControlPointWithoutBlending()
	{
		final ColorTableLut lut = threeColorPalette();

		// Anything in [0, 0.5) holds the color at t=0 (red).
		Assert.assertEquals( 0xffff0000, ColorTableLut.lookupARGB( lut, 0, 1, 0.0, ValueMatching.TRUNCATE ) );
		Assert.assertEquals( 0xffff0000, ColorTableLut.lookupARGB( lut, 0, 1, 0.49, ValueMatching.TRUNCATE ) );
		// [0.5, 1.0) holds the color at t=0.5 (green).
		Assert.assertEquals( 0xff00ff00, ColorTableLut.lookupARGB( lut, 0, 1, 0.5, ValueMatching.TRUNCATE ) );
		Assert.assertEquals( 0xff00ff00, ColorTableLut.lookupARGB( lut, 0, 1, 0.99, ValueMatching.TRUNCATE ) );
		// t=1.0 holds the color at t=1.0 (blue).
		Assert.assertEquals( 0xff0000ff, ColorTableLut.lookupARGB( lut, 0, 1, 1.0, ValueMatching.TRUNCATE ) );
	}

	@Test
	public void testStaticLookupIgnoresMatchingForNonLutPaletteColorTables()
	{
		final ColorTable8 ct = new ColorTable8();
		// A plain ColorTable8 has no concept of stepped control points, so
		// Truncate must fall back to its normal (interpolated) lookup.
		Assert.assertEquals(
				ct.lookupARGB( 0, 255, 100 ),
				ColorTableLut.lookupARGB( ct, 0, 255, 100, ValueMatching.TRUNCATE ) );
	}

	private static final double[] UNEVEN_POSITIONS = { 0.0, 0.1, 0.3, 1.0 };

	@Test
	public void testAverageIntervalSize()
	{
		// Gaps: 0.1, 0.2, 0.7 -- average = 1.0 / 3.
		Assert.assertEquals( 1.0 / 3, ColorTableLut.averageIntervalSize( UNEVEN_POSITIONS ), 1e-9 );
	}

	@Test
	public void testMirroredLastIntervalSize()
	{
		// Last gap: 1.0 - 0.3.
		Assert.assertEquals( 0.7, ColorTableLut.mirroredLastIntervalSize( UNEVEN_POSITIONS ), 1e-9 );
	}

	@Test
	public void testFirstIntervalSize()
	{
		// First gap: 0.1 - 0.0.
		Assert.assertEquals( 0.1, ColorTableLut.firstIntervalSize( UNEVEN_POSITIONS ), 1e-9 );
	}

	@Test
	public void testIntervalSizeFunctionsHandleDegenerateInput()
	{
		Assert.assertEquals( 0.0, ColorTableLut.averageIntervalSize( new double[] { 0.5 } ), 1e-9 );
		Assert.assertEquals( 0.0, ColorTableLut.mirroredLastIntervalSize( new double[] { 0.5 } ), 1e-9 );
		Assert.assertEquals( 0.0, ColorTableLut.firstIntervalSize( new double[] { 0.5 } ), 1e-9 );
		Assert.assertEquals( 0.0, ColorTableLut.averageIntervalSize( new double[ 0 ] ), 1e-9 );
	}

	/**
	 * Regression test: without an estimated size for the last color's
	 * interval, it would only ever be picked at the single point t == 1,
	 * instead of a proper band matching the other colors' share of the bar.
	 */
	@Test
	public void testLookupARGBQualitativeGivesLastColorAFairShareOfTheBar()
	{
		final ColorTableLut lut = threeColorPalette();
		final double lastIntervalSize = ColorTableLut.mirroredLastIntervalSize( ColorTableLut.colorPositions( lut ) );

		// The extended bar is split into three equal thirds -- red, green, blue.
		Assert.assertEquals( 0xffff0000, ColorTableLut.lookupARGBQualitative( lut, 0.0, lastIntervalSize ) );
		Assert.assertEquals( 0xffff0000, ColorTableLut.lookupARGBQualitative( lut, 0.3, lastIntervalSize ) );
		Assert.assertEquals( 0xff00ff00, ColorTableLut.lookupARGBQualitative( lut, 0.5, lastIntervalSize ) );
		// Crucially, the whole last third of the bar is blue, not just t == 1.
		Assert.assertEquals( 0xff0000ff, ColorTableLut.lookupARGBQualitative( lut, 0.7, lastIntervalSize ) );
		Assert.assertEquals( 0xff0000ff, ColorTableLut.lookupARGBQualitative( lut, 1.0, lastIntervalSize ) );
	}

	@Test
	public void testLookupARGBQualitativeFallsBackForSingleEntryTable()
	{
		final ColorTable8 ct = new ColorTable8( new byte[] { 10 }, new byte[] { 20 }, new byte[] { 30 } );
		Assert.assertEquals( ct.lookupARGB( 0, 1, 0.5 ), ColorTableLut.lookupARGBQualitative( ct, 0.5, 0.1 ) );
	}
}
