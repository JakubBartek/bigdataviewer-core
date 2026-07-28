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
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link MappingModel}.
 */
public class MappingModelTest
{
	@Test
	public void testDefaultIsLinear()
	{
		final MappingModel model = new MappingModel();
		Assert.assertEquals( MappingPreset.LINEAR, model.getPreset() );
		Assert.assertEquals( RangeMode.FIT, model.getRangeMode() );
		Assert.assertEquals( ValueMatching.INTERPOLATE, model.getValueMatching() );

		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 100, 256 ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 100, 0, 100, 256 ) );
		Assert.assertEquals( 128, model.mapToLutIndex( 50, 0, 100, 256 ), 1 );
	}

	@Test
	public void testFitClampsOutOfRange()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.FIT );

		Assert.assertEquals( 0, model.mapToLutIndex( -50, 0, 100, 256 ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 150, 0, 100, 256 ) );
	}

	@Test
	public void testCyclicUsesLutColorCountAsPeriodAnchoredAtMinIgnoringMax()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );

		final int n = 10;
		// A value of exactly min lands on the first color; min+n-1 on the last.
		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 1000, n ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 9, 0, 1000, n ) );

		// Wraps every n values (e.g. label ids cycling through the palette).
		Assert.assertEquals( model.mapToLutIndex( 0, 0, 1000, n ), model.mapToLutIndex( 10, 0, 1000, n ) );
		Assert.assertEquals( model.mapToLutIndex( 3, 0, 1000, n ), model.mapToLutIndex( 23, 0, 1000, n ) );

		// max is ignored entirely in Cyclic mode.
		Assert.assertEquals( model.mapToLutIndex( 5, 0, 1000, n ), model.mapToLutIndex( 5, 0, 1, n ) );

		// min anchors the cycle: a value of exactly min always gets the first
		// color, no matter what min itself is (e.g. setting min to 5 means
		// value 5 gets whatever color is first in the palette).
		Assert.assertEquals( 0, model.mapToLutIndex( 5, 5, 1000, n ) );
		Assert.assertNotEquals( model.mapToLutIndex( 5, 0, 1000, n ), model.mapToLutIndex( 5, 5, 1000, n ) );
	}

	/**
	 * {@link MappingCurvePanel} draws/edits the curve using these package-
	 * visible helpers directly (so what's drawn matches what's applied); pin
	 * down their exact contract here.
	 */
	@Test
	public void testCyclicAndFitPositionHelpersMatchMapToLutIndex()
	{
		final int n = 10;
		// A value of k*n + x*(n-1) should land at normalized curve position x.
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 0, 0, n, false ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.cyclicPosition( n - 1, 0, n, false ), 1e-9 );
		Assert.assertEquals( 0.5, MappingModel.cyclicPosition( 4.5, 0, n, false ), 1e-9 );
		// Wraps every n, independent of how many periods away.
		Assert.assertEquals( MappingModel.cyclicPosition( 3, 0, n, false ), MappingModel.cyclicPosition( 3 + 5 * n, 0, n, false ), 1e-9 );
		// Degenerate color count never divides by zero.
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 7, 0, 1, false ), 1e-9 );
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 7, 0, 0, false ), 1e-9 );

		// Anchored at min: a value of exactly min always lands on position 0
		// (the palette's first color), regardless of what min is.
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 5, 5, n, false ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.cyclicPosition( 5 + n - 1, 5, n, false ), 1e-9 );
		Assert.assertEquals( MappingModel.cyclicPosition( 5, 5, n, false ), MappingModel.cyclicPosition( 5 + n, 5, n, false ), 1e-9 );

		Assert.assertEquals( 0.0, MappingModel.fitPosition( 0, 0, 100 ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.fitPosition( 100, 0, 100 ), 1e-9 );
		Assert.assertEquals( 0.5, MappingModel.fitPosition( 50, 0, 100 ), 1e-9 );
		// Clamped outside [min, max].
		Assert.assertEquals( 0.0, MappingModel.fitPosition( -50, 0, 100 ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.fitPosition( 150, 0, 100 ), 1e-9 );
	}

	@Test
	public void testCyclicHandlesDegenerateColorCount()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );

		Assert.assertEquals( 0, model.mapToLutIndex( 5, 0, 100, 1 ) );
		Assert.assertEquals( 0, model.mapToLutIndex( 500, 0, 100, 0 ) );
	}

	@Test
	public void testTreatMinAsBackgroundOnlyAppliesWhenCyclic()
	{
		final MappingModel model = new MappingModel();
		model.setTreatMinAsBackground( true );
		model.setBackgroundColor( 0xff112233 );

		// Not cyclic: background never applies, regardless of the value.
		model.setRangeMode( RangeMode.FIT );
		Assert.assertFalse( model.isBackgroundValue( 5, 5 ) );

		// Cyclic: a raw value exactly equal to min is flagged as background...
		model.setRangeMode( RangeMode.CYCLIC );
		Assert.assertTrue( model.isBackgroundValue( 5, 5 ) );
		Assert.assertEquals( 0xff112233, model.getBackgroundColor() );

		// Values below min are background too (out-of-range, low side)...
		Assert.assertTrue( model.isBackgroundValue( 0, 5 ) );
		// ...but values at/above min+1 are not (they cycle normally).
		Assert.assertFalse( model.isBackgroundValue( 6, 5 ) );

		// Disabling the flag means no value is ever flagged as background.
		model.setTreatMinAsBackground( false );
		Assert.assertFalse( model.isBackgroundValue( 5, 5 ) );
	}

	/**
	 * Regression test: cyclicPosition only ever defines integer inputs
	 * exactly on a palette color, and skips the gap between min and min+1 to
	 * make the cycle start at min+1. Without covering that whole [min, min+1)
	 * interval as background, any value strictly in between (e.g. continuous
	 * image data, or a continuous preview) fell through to mapToLutIndex and
	 * rendered as the palette's *last* color instead of the background color,
	 * making it look like the last color appeared right after the background
	 * and before the first color.
	 */
	@Test
	public void testBackgroundCoversWholeUnitIntervalNotJustExactMin()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );

		final double min = 5;
		Assert.assertTrue( model.isBackgroundValue( min, min ) );
		Assert.assertTrue( model.isBackgroundValue( 5.1, min ) );
		Assert.assertTrue( model.isBackgroundValue( 5.5, min ) );
		Assert.assertTrue( model.isBackgroundValue( 5.999, min ) );

		// min+1 and beyond are real cycled values again, not background.
		Assert.assertFalse( model.isBackgroundValue( 6.0, min ) );
		Assert.assertFalse( model.isBackgroundValue( 6.5, min ) );
	}

	/**
	 * Out-of-range values below min are treated as background too (when
	 * treatMinAsBackground is enabled) -- only values above the reserved
	 * [min, min+1) interval cycle normally. Without this, values below min
	 * would show data as if it were a valid cycled label, which is confusing
	 * for out-of-range/no-data pixels. When the flag is off, out-of-range
	 * values on either side just keep cycling, unaffected.
	 */
	@Test
	public void testBelowMinIsBackgroundOnlyWhenTreatMinAsBackgroundIsEnabled()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );

		final double min = 5;

		model.setTreatMinAsBackground( true );
		Assert.assertTrue( model.isBackgroundValue( 4.9, min ) );
		Assert.assertTrue( model.isBackgroundValue( 0, min ) );
		Assert.assertTrue( model.isBackgroundValue( -1000, min ) );

		// Values above the range still cycle normally either way.
		Assert.assertFalse( model.isBackgroundValue( 1000, min ) );

		model.setTreatMinAsBackground( false );
		Assert.assertFalse( model.isBackgroundValue( 4.9, min ) );
		Assert.assertFalse( model.isBackgroundValue( -1000, min ) );
	}

	@Test
	public void testMapToLutIndexIgnoresBackground()
	{
		// mapToLutIndex never special-cases background; callers must check
		// isBackgroundValue() first and use getBackgroundColor() instead.
		final MappingModel model = new MappingModel();
		model.getCurve().setPoints( new double[] { 0.0, 1.0 }, new int[] { 100, 200 } );
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );

		// Even though 0 is the min here and background is enabled, mapToLutIndex
		// still computes the (raw, uncapped) cyclic mapping for it: with min
		// reserved for the background color, the cycle actually starts at
		// min+1, so value=min lands one step *before* that start (i.e. wraps
		// to the end of the cycle, mathematically) -- this specific value is
		// never reached in practice, since real callers check
		// isBackgroundValue() first and never call mapToLutIndex for it.
		Assert.assertEquals( 200, model.mapToLutIndex( 0, 0, 100, 51 ) );
		Assert.assertEquals( 148, model.mapToLutIndex( 25, 0, 100, 51 ) );
	}

	@Test
	public void testApplyPresetChangesShape()
	{
		final MappingModel model = new MappingModel();
		model.applyPreset( MappingPreset.LOG );

		Assert.assertEquals( MappingPreset.LOG, model.getPreset() );
		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 100, 256 ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 100, 0, 100, 256 ) );
		// Log curve rises faster than linear near the low end.
		Assert.assertTrue( model.mapToLutIndex( 10, 0, 100, 256 ) > 26 );
	}

	/**
	 * ValueMatching must NOT affect mapToLutIndex: the curve is always
	 * evaluated smoothly there. Quantizing at the curve stage (in addition to
	 * the palette stage, see {@link LutPaletteTest}) was the root cause of a
	 * bug where some palette colors became unreachable in Round/Truncate mode
	 * -- the curve's own control points are an unrelated resolution to the
	 * palette's actual color count, so the two independent quantizations
	 * didn't line up.
	 */
	@Test
	public void testValueMatchingDoesNotAffectMapToLutIndex()
	{
		final MappingModel model = new MappingModel();
		model.getCurve().setPoints( new double[] { 0.0, 0.5, 1.0 }, new int[] { 0, 100, 255 } );

		final int smooth25 = model.mapToLutIndex( 25, 0, 100, 256 );
		final int smooth60 = model.mapToLutIndex( 60, 0, 100, 256 );

		model.setValueMatching( ValueMatching.TRUNCATE );
		Assert.assertEquals( smooth25, model.mapToLutIndex( 25, 0, 100, 256 ) );
		Assert.assertEquals( smooth60, model.mapToLutIndex( 60, 0, 100, 256 ) );

		model.setValueMatching( ValueMatching.ROUND );
		Assert.assertEquals( smooth25, model.mapToLutIndex( 25, 0, 100, 256 ) );
		Assert.assertEquals( smooth60, model.mapToLutIndex( 60, 0, 100, 256 ) );
	}

	/**
	 * Regression test for the "missing colors" bug: every one of a palette's
	 * colors must be reachable by cycling through raw label values 0..n-1, no
	 * matter which ValueMatching mode is selected.
	 */
	@Test
	public void testAllPaletteColorsReachableInCyclicModeRegardlessOfValueMatching()
	{
		final int n = 10;
		final double[] positions = new double[ n ];
		final double[] red = new double[ n ];
		final double[] green = new double[ n ];
		final double[] blue = new double[ n ];
		final double[] alpha = new double[ n ];
		for ( int i = 0; i < n; i++ )
		{
			positions[ i ] = i / ( double ) ( n - 1 );
			red[ i ] = i / ( double ) ( n - 1 ); // a distinct red level per color
			green[ i ] = 0;
			blue[ i ] = 0;
			alpha[ i ] = 1;
		}
		final LutPalette palette = new LutPalette( positions, red, green, blue, alpha );

		for ( final ValueMatching matching : ValueMatching.values() )
		{
			final MappingModel model = new MappingModel();
			model.setRangeMode( RangeMode.CYCLIC );
			model.setValueMatching( matching );

			final Set< Integer > seenReds = new HashSet<>();
			for ( int label = 0; label < n; label++ )
			{
				final int lutIndex = model.mapToLutIndex( label, 0, 1000, n );
				final int argb = LutPalette.lookupARGB( palette, 0, 255, lutIndex, matching );
				seenReds.add( ( argb >> 16 ) & 0xFF );
			}
			Assert.assertEquals( "matching=" + matching, n, seenReds.size() );
		}
	}

	/**
	 * Not all LUTs have evenly-spaced colors (e.g. a categorical palette's
	 * control points can cluster some colors closer together than others).
	 * {@link MappingModel#cyclicPosition(double, double, double[], boolean)}
	 * must land integer inputs exactly on the corresponding entry of the
	 * actual, unevenly-spaced positions array -- not on an assumed uniform
	 * {@code k / (n - 1)} step.
	 */
	@Test
	public void testCyclicPositionRespectsUnevenColorSpacing()
	{
		final double[] uneven = { 0.0, 0.05, 0.9, 1.0 };

		Assert.assertEquals( uneven[ 0 ], MappingModel.cyclicPosition( 0, 0, uneven, false ), 1e-9 );
		Assert.assertEquals( uneven[ 1 ], MappingModel.cyclicPosition( 1, 0, uneven, false ), 1e-9 );
		Assert.assertEquals( uneven[ 2 ], MappingModel.cyclicPosition( 2, 0, uneven, false ), 1e-9 );
		Assert.assertEquals( uneven[ 3 ], MappingModel.cyclicPosition( 3, 0, uneven, false ), 1e-9 );

		// Halfway between two colors interpolates linearly between their
		// actual (unevenly spaced) positions, not a uniform step.
		Assert.assertEquals( ( uneven[ 1 ] + uneven[ 2 ] ) / 2, MappingModel.cyclicPosition( 1.5, 0, uneven, false ), 1e-9 );

		// Wraps every n = uneven.length values, back to the first color.
		Assert.assertEquals( uneven[ 0 ], MappingModel.cyclicPosition( 4, 0, uneven, false ), 1e-9 );
	}

	/**
	 * The inverse used by {@link MappingCurvePanel} to draw/hit-test control
	 * points must round-trip {@link MappingModel#cyclicPosition} even when
	 * the palette's colors are unevenly spaced.
	 */
	@Test
	public void testInverseCyclicPositionRoundTripsUnevenColorSpacing()
	{
		final double[] uneven = { 0.0, 0.05, 0.9, 1.0 };

		for ( double raw = 0; raw < uneven.length - 1; raw += 0.25 )
		{
			final double t = MappingModel.cyclicPosition( raw, 0, uneven, false );
			Assert.assertEquals( "raw=" + raw, raw, MappingModel.inverseCyclicPosition( t, uneven, false ), 1e-9 );
		}
	}

	/**
	 * Regression test for the "missing colors" bug, this time with a
	 * genuinely unevenly-spaced palette (see {@link LutPalette#colorPositions}):
	 * every color must still be reachable by cycling through raw label
	 * values 0..n-1, no matter which ValueMatching mode is selected.
	 */
	@Test
	public void testAllPaletteColorsReachableInCyclicModeWithUnevenSpacing()
	{
		final double[] positions = { 0.0, 0.05, 0.1, 0.9, 1.0 };
		final int n = positions.length;
		final double[] red = new double[ n ];
		final double[] green = new double[ n ];
		final double[] blue = new double[ n ];
		final double[] alpha = new double[ n ];
		for ( int i = 0; i < n; i++ )
		{
			red[ i ] = i / ( double ) ( n - 1 ); // a distinct red level per color
			green[ i ] = 0;
			blue[ i ] = 0;
			alpha[ i ] = 1;
		}
		final LutPalette palette = new LutPalette( positions, red, green, blue, alpha );
		final double[] colorPositions = LutPalette.colorPositions( palette );

		for ( final ValueMatching matching : ValueMatching.values() )
		{
			final MappingModel model = new MappingModel();
			model.setRangeMode( RangeMode.CYCLIC );
			model.setValueMatching( matching );

			final Set< Integer > seenReds = new HashSet<>();
			for ( int label = 0; label < n; label++ )
			{
				final int lutIndex = model.mapToLutIndex( label, 0, 1000, colorPositions );
				final int argb = LutPalette.lookupARGB( palette, 0, 255, lutIndex, matching );
				seenReds.add( ( argb >> 16 ) & 0xFF );
			}
			Assert.assertEquals( "matching=" + matching, n, seenReds.size() );
		}
	}

	@Test
	public void testCopyFromDoesNotAlias()
	{
		final MappingModel source = new MappingModel();
		source.applyPreset( MappingPreset.SIGMOID );
		source.setRangeMode( RangeMode.CYCLIC );
		source.setValueMatching( ValueMatching.ROUND );
		source.setTreatMinAsBackground( true );
		source.setBackgroundColor( 0xffaabbcc );

		final MappingModel copy = new MappingModel();
		copy.copyFrom( source );

		Assert.assertEquals( source.getPreset(), copy.getPreset() );
		Assert.assertEquals( source.getRangeMode(), copy.getRangeMode() );
		Assert.assertEquals( source.getValueMatching(), copy.getValueMatching() );
		Assert.assertEquals( source.isTreatMinAsBackground(), copy.isTreatMinAsBackground() );
		Assert.assertEquals( source.getBackgroundColor(), copy.getBackgroundColor() );
		Assert.assertEquals( source.mapToLutIndex( 42, 0, 100, 256 ), copy.mapToLutIndex( 42, 0, 100, 256 ) );

		// Mutating the source afterwards must not affect the copy.
		source.applyPreset( MappingPreset.LINEAR );
		source.setRangeMode( RangeMode.FIT );
		source.setTreatMinAsBackground( false );
		source.setBackgroundColor( 0xff000000 );
		Assert.assertEquals( MappingPreset.SIGMOID, copy.getPreset() );
		Assert.assertEquals( RangeMode.CYCLIC, copy.getRangeMode() );
		Assert.assertTrue( copy.isTreatMinAsBackground() );
		Assert.assertEquals( 0xffaabbcc, copy.getBackgroundColor() );
	}
}
