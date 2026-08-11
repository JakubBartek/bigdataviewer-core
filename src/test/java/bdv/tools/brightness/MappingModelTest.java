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
	/**
	 * Positions {@code 0, 1/(n-1), ..., 1} for {@code n} evenly spaced
	 * colors -- the layout of a fixed-resolution table like
	 * {@link net.imglib2.display.ColorTable8}. Most cases here only care
	 * about the color <em>count</em>, not uneven spacing (which
	 * {@link #testCyclicPositionRespectsUnevenColorSpacing} covers
	 * separately), so this keeps them readable.
	 */
	private static double[] uniform( final int colorCount )
	{
		final int n = Math.max( 1, colorCount );
		final double[] positions = new double[ n ];
		for ( int i = 0; i < n; i++ )
			positions[ i ] = n > 1 ? i / ( double ) ( n - 1 ) : 0.0;
		return positions;
	}

	@Test
	public void testDefaultIsLinear()
	{
		final MappingModel model = new MappingModel();
		Assert.assertEquals( MappingPreset.LINEAR, model.getPreset() );
		Assert.assertEquals( RangeMode.FIT, model.getRangeMode() );
		Assert.assertEquals( ValueMatching.INTERPOLATE, model.getValueMatching() );

		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 100, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 128, model.mapToLutIndex( 50, 0, 100, uniform( 256 ) ), 1 );
	}

	@Test
	public void testFitClampsOutOfRange()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.FIT );

		Assert.assertEquals( 0, model.mapToLutIndex( -50, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 150, 0, 100, uniform( 256 ) ) );
	}

	@Test
	public void testCyclicUsesLutColorCountAsPeriodAnchoredAtMinIgnoringMax()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );

		final int n = 10;
		// A value of exactly min lands on the first color; min+n-1 on the last.
		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 1000, uniform( n ) ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 9, 0, 1000, uniform( n ) ) );

		// Wraps every n values (e.g. label ids cycling through the palette).
		Assert.assertEquals( model.mapToLutIndex( 0, 0, 1000, uniform( n ) ), model.mapToLutIndex( 10, 0, 1000, uniform( n ) ) );
		Assert.assertEquals( model.mapToLutIndex( 3, 0, 1000, uniform( n ) ), model.mapToLutIndex( 23, 0, 1000, uniform( n ) ) );

		// max is ignored entirely in Cyclic mode.
		Assert.assertEquals( model.mapToLutIndex( 5, 0, 1000, uniform( n ) ), model.mapToLutIndex( 5, 0, 1, uniform( n ) ) );

		// min anchors the cycle: a value of exactly min always gets the first
		// color, no matter what min itself is (e.g. setting min to 5 means
		// value 5 gets whatever color is first in the palette).
		Assert.assertEquals( 0, model.mapToLutIndex( 5, 5, 1000, uniform( n ) ) );
		Assert.assertNotEquals( model.mapToLutIndex( 5, 0, 1000, uniform( n ) ), model.mapToLutIndex( 5, 5, 1000, uniform( n ) ) );
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
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 0, 0, uniform( n ), n, false ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.cyclicPosition( n - 1, 0, uniform( n ), n, false ), 1e-9 );
		Assert.assertEquals( 0.5, MappingModel.cyclicPosition( 4.5, 0, uniform( n ), n, false ), 1e-9 );
		// Wraps every n, independent of how many periods away.
		Assert.assertEquals( MappingModel.cyclicPosition( 3, 0, uniform( n ), n, false ), MappingModel.cyclicPosition( 3 + 5 * n, 0, uniform( n ), n, false ), 1e-9 );
		// Degenerate color count never divides by zero.
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 7, 0, uniform( 1 ), 1, false ), 1e-9 );
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 7, 0, uniform( 0 ), 1, false ), 1e-9 );

		// Anchored at min: a value of exactly min always lands on position 0
		// (the palette's first color), regardless of what min is.
		Assert.assertEquals( 0.0, MappingModel.cyclicPosition( 5, 5, uniform( n ), n, false ), 1e-9 );
		Assert.assertEquals( 1.0, MappingModel.cyclicPosition( 5 + n - 1, 5, uniform( n ), n, false ), 1e-9 );
		Assert.assertEquals( MappingModel.cyclicPosition( 5, 5, uniform( n ), n, false ), MappingModel.cyclicPosition( 5 + n, 5, uniform( n ), n, false ), 1e-9 );

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

		Assert.assertEquals( 0, model.mapToLutIndex( 5, 0, 100, uniform( 1 ) ) );
		Assert.assertEquals( 0, model.mapToLutIndex( 500, 0, 100, uniform( 0 ) ) );
	}

	/**
	 * Treat-min-as-background is available in both range modes, but the
	 * exact cutoff differs: Cyclic reserves the whole unit interval
	 * {@code [min, min + 1)} (see {@link #testBackgroundCoversWholeUnitIntervalNotJustExactMin}
	 * for why), while Fit -- which has no notion of discrete steps -- simply
	 * treats anything at or below {@code min} as background, with no "+1"
	 * widening (a raw "+1" would be meaningless for Fit's arbitrary,
	 * possibly sub-1-wide, continuous range).
	 */
	@Test
	public void testTreatMinAsBackgroundThresholdDependsOnRangeMode()
	{
		final MappingModel model = new MappingModel();
		model.setTreatMinAsBackground( true );
		model.setBackgroundColor( 0xff112233 );

		// Fit: exactly at or below min is background...
		model.setRangeMode( RangeMode.FIT );
		Assert.assertTrue( model.isBackgroundValue( 5, 5 ) );
		Assert.assertTrue( model.isBackgroundValue( 0, 5 ) );
		Assert.assertEquals( 0xff112233, model.getBackgroundColor() );
		// ...but, unlike Cyclic, values strictly between min and min+1 are
		// NOT background -- Fit has no per-label interval to reserve.
		Assert.assertFalse( model.isBackgroundValue( 5.5, 5 ) );
		Assert.assertFalse( model.isBackgroundValue( 6, 5 ) );

		// Cyclic: a raw value exactly equal to min is flagged as background...
		model.setRangeMode( RangeMode.CYCLIC );
		Assert.assertTrue( model.isBackgroundValue( 5, 5 ) );

		// Values below min are background too (out-of-range, low side)...
		Assert.assertTrue( model.isBackgroundValue( 0, 5 ) );
		// ...but values at/above min+1 are not (they cycle normally).
		Assert.assertFalse( model.isBackgroundValue( 6, 5 ) );

		// Disabling the flag means no value is ever flagged as background,
		// in either mode.
		model.setTreatMinAsBackground( false );
		Assert.assertFalse( model.isBackgroundValue( 5, 5 ) );
		model.setRangeMode( RangeMode.FIT );
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
	 * Regression test: the reserved background interval {@code [min, min + 1)}
	 * is always exactly 1 raw unit wide -- it must NOT scale with
	 * {@link MappingModel#setCyclicPeriod}. The background cutoff is defined
	 * purely by {@code min} as entered by the user; the period only governs
	 * how the (non-background) colors themselves cycle.
	 */
	@Test
	public void testCyclicBackgroundThresholdIndependentOfPeriod()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );

		final double min = 5;

		for ( final double period : new double[] { 1, 3, 10, 100 } )
		{
			model.setCyclicPeriod( period );
			Assert.assertTrue( "period=" + period, model.isBackgroundValue( 5.9, min ) );
			Assert.assertFalse( "period=" + period, model.isBackgroundValue( 6.0, min ) );
		}
	}

	/**
	 * Regression test: with a non-default period, the first color after the
	 * fixed 1-unit background reservation must still land exactly at
	 * {@code min + 1} (not at {@code min + period / colorCount}, which the
	 * background interval was briefly, incorrectly, tied to) -- i.e. the
	 * color cycle itself starts right where the fixed background interval
	 * ends, regardless of period.
	 */
	@Test
	public void testCyclicFirstColorStartsRightAfterFixedBackgroundInterval()
	{
		final int n = 4;
		final double[] positions = new double[ n ];
		for ( int i = 0; i < n; i++ )
			positions[ i ] = i / ( double ) ( n - 1 );

		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );
		model.setCyclicPeriod( 40 ); // 10 raw units per color slot

		final double min = 5;
		Assert.assertEquals( positions[ 0 ], MappingModel.cyclicPosition( 6, min, positions, 40, true ), 1e-9 );
		Assert.assertEquals( positions[ 1 ], MappingModel.cyclicPosition( 16, min, positions, 40, true ), 1e-9 );
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
		Assert.assertEquals( 200, model.mapToLutIndex( 0, 0, 100, uniform( 51 ) ) );
		Assert.assertEquals( 148, model.mapToLutIndex( 25, 0, 100, uniform( 51 ) ) );
	}

	@Test
	public void testApplyPresetChangesShape()
	{
		final MappingModel model = new MappingModel();
		model.applyPreset( MappingPreset.LOG );

		Assert.assertEquals( MappingPreset.LOG, model.getPreset() );
		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 100, 0, 100, uniform( 256 ) ) );
		// Log curve rises faster than linear near the low end.
		Assert.assertTrue( model.mapToLutIndex( 10, 0, 100, uniform( 256 ) ) > 26 );
	}

	/**
	 * {@link MappingPreset#LINEAR} must be represented by exactly its two
	 * endpoints, not extra intermediate points: a straight line needs no
	 * more than that, and sampling additional points would each get
	 * independently rounded to the nearest integer, introducing rounding
	 * noise a true identity line shouldn't have (see
	 * {@link #testAllPaletteColorsReachableInCyclicModeForVariousPaletteSizes}
	 * for the bug this caused).
	 */
	@Test
	public void testLinearPresetIsExactlyTwoPoints()
	{
		Assert.assertArrayEquals( new double[] { 0.0, 1.0 }, MappingPreset.LINEAR.xs(), 1e-9 );
		Assert.assertArrayEquals( new int[] { 0, 255 }, MappingPreset.LINEAR.ys() );
	}

	@Test
	public void testInvertCurveFlipsMapping()
	{
		final MappingModel model = new MappingModel();

		// Default Linear preset increases; after inverting it must decrease.
		Assert.assertEquals( 0, model.mapToLutIndex( 0, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 255, model.mapToLutIndex( 100, 0, 100, uniform( 256 ) ) );

		model.invertCurve();

		Assert.assertEquals( 255, model.mapToLutIndex( 0, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( 0, model.mapToLutIndex( 100, 0, 100, uniform( 256 ) ) );
	}

	@Test
	public void testInvertCurveAppliesOnTopOfHandDraggedEdits()
	{
		final MappingModel model = new MappingModel();
		model.getCurve().setPoints( new double[] { 0.0, 0.5, 1.0 }, new int[] { 0, 100, 255 } );

		model.invertCurve();

		Assert.assertEquals( 255, model.getCurve().getY( 0 ) );
		Assert.assertEquals( 155, model.getCurve().getY( 1 ) );
		Assert.assertEquals( 0, model.getCurve().getY( 2 ) );
	}

	/**
	 * ValueMatching must NOT affect mapToLutIndex: the curve is always
	 * evaluated smoothly there. Quantizing at the curve stage (in addition to
	 * the palette stage, see {@link ColorTableLutTest}) was the root cause of a
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

		final int smooth25 = model.mapToLutIndex( 25, 0, 100, uniform( 256 ) );
		final int smooth60 = model.mapToLutIndex( 60, 0, 100, uniform( 256 ) );

		model.setValueMatching( ValueMatching.TRUNCATE );
		Assert.assertEquals( smooth25, model.mapToLutIndex( 25, 0, 100, uniform( 256 ) ) );
		Assert.assertEquals( smooth60, model.mapToLutIndex( 60, 0, 100, uniform( 256 ) ) );
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
		final ColorTableLut palette = new ColorTableLut( positions, red, green, blue, alpha );

		for ( final ValueMatching matching : ValueMatching.values() )
		{
			final MappingModel model = new MappingModel();
			model.setRangeMode( RangeMode.CYCLIC );
			model.setValueMatching( matching );

			final Set< Integer > seenReds = new HashSet<>();
			for ( int label = 0; label < n; label++ )
			{
				final int lutIndex = model.mapToLutIndex( label, 0, 1000, uniform( n ) );
				final int argb = ColorTableLut.lookupARGB( palette, 0, 255, lutIndex, matching );
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
		final int period = uneven.length;

		Assert.assertEquals( uneven[ 0 ], MappingModel.cyclicPosition( 0, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 1 ], MappingModel.cyclicPosition( 1, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 2 ], MappingModel.cyclicPosition( 2, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 3 ], MappingModel.cyclicPosition( 3, 0, uneven, period, false ), 1e-9 );

		// Halfway between two colors interpolates linearly between their
		// actual (unevenly spaced) positions, not a uniform step.
		Assert.assertEquals( ( uneven[ 1 ] + uneven[ 2 ] ) / 2, MappingModel.cyclicPosition( 1.5, 0, uneven, period, false ), 1e-9 );

		// Wraps every n = uneven.length values, back to the first color.
		Assert.assertEquals( uneven[ 0 ], MappingModel.cyclicPosition( 4, 0, uneven, period, false ), 1e-9 );
	}

	/**
	 * Unlike {@link MappingModel#cyclicPosition}, {@link MappingModel#steppedCyclicPosition}
	 * must not interpolate towards the next label at all -- every raw value
	 * within one label's unit interval snaps to that label's own exact
	 * position (see {@link #testMapToLutIndexForColorDoesNotBleedWhenCurveIsInverted}
	 * for why this matters).
	 */
	@Test
	public void testSteppedCyclicPositionSnapsInsteadOfInterpolating()
	{
		final double[] uneven = { 0.0, 0.05, 0.9, 1.0 };
		final int period = uneven.length;

		// Exact labels match cyclicPosition.
		Assert.assertEquals( uneven[ 0 ], MappingModel.steppedCyclicPosition( 0, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 1 ], MappingModel.steppedCyclicPosition( 1, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 2 ], MappingModel.steppedCyclicPosition( 2, 0, uneven, period, false ), 1e-9 );
		Assert.assertEquals( uneven[ 3 ], MappingModel.steppedCyclicPosition( 3, 0, uneven, period, false ), 1e-9 );

		// Anywhere within label 1's interval [1, 2) stays pinned at
		// uneven[1], unlike cyclicPosition which would already be most of
		// the way towards uneven[2] by v=1.9.
		Assert.assertEquals( uneven[ 1 ], MappingModel.steppedCyclicPosition( 1.9, 0, uneven, period, false ), 1e-9 );

		// Wraps every n = uneven.length values, back to the first color.
		Assert.assertEquals( uneven[ 0 ], MappingModel.steppedCyclicPosition( 4, 0, uneven, period, false ), 1e-9 );
	}

	/**
	 * Regression test: {@link MappingModel#cycleIndex} must change exactly
	 * where the mapping actually wraps back to the palette's first color --
	 * i.e. anchored at min (plus the reserved background unit), not counted
	 * from 0. {@link MappingCurvePanel#drawCurve} previously used
	 * {@code floor(value / period)}, which with min=5, period=4 broke the
	 * drawn line at 8/12/16 while the mapping really wraps at 9/13/17,
	 * putting the sawtooth discontinuities at visibly wrong x positions (and
	 * disagreeing with the control points, which were anchored correctly).
	 */
	@Test
	public void testCycleIndexChangesExactlyWhereCyclicPositionWraps()
	{
		final double[] positions = { 0.0, 1.0 / 3, 2.0 / 3, 1.0 };
		final double period = positions.length;
		final double min = 5;

		for ( final boolean treatMinAsBackground : new boolean[] { false, true } )
		{
			double prev = min + ( treatMinAsBackground ? 1 : 0 );
			for ( double v = prev; v <= prev + 12; v += 0.25 )
			{
				final boolean indexChanged =
						MappingModel.cycleIndex( v, min, period, treatMinAsBackground )
								!= MappingModel.cycleIndex( prev, min, period, treatMinAsBackground );
				// A real wrap is where the normalized position drops back
				// towards the palette's first color instead of advancing.
				final double tPrev = MappingModel.cyclicPosition( prev, min, positions, period, treatMinAsBackground );
				final double tNow = MappingModel.cyclicPosition( v, min, positions, period, treatMinAsBackground );
				final boolean actuallyWrapped = tNow < tPrev - 1e-9;

				Assert.assertEquals( "bg=" + treatMinAsBackground + " v=" + v, actuallyWrapped, indexChanged );
				prev = v;
			}
		}
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
		final int period = uneven.length;

		for ( double raw = 0; raw < uneven.length - 1; raw += 0.25 )
		{
			final double t = MappingModel.cyclicPosition( raw, 0, uneven, period, false );
			Assert.assertEquals( "raw=" + raw, raw, MappingModel.inverseCyclicPosition( t, uneven, period, false ), 1e-9 );
		}
	}

	/**
	 * Regression test for the "missing colors" bug, this time with a
	 * genuinely unevenly-spaced palette (see {@link ColorTableLut#colorPositions}):
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
		final ColorTableLut palette = new ColorTableLut( positions, red, green, blue, alpha );
		final double[] colorPositions = ColorTableLut.colorPositions( palette );

		for ( final ValueMatching matching : ValueMatching.values() )
		{
			final MappingModel model = new MappingModel();
			model.setRangeMode( RangeMode.CYCLIC );
			model.setValueMatching( matching );

			final Set< Integer > seenReds = new HashSet<>();
			for ( int label = 0; label < n; label++ )
			{
				final int lutIndex = model.mapToLutIndex( label, 0, 1000, colorPositions );
				final int argb = ColorTableLut.lookupARGB( palette, 0, 255, lutIndex, matching );
				seenReds.add( ( argb >> 16 ) & 0xFF );
			}
			Assert.assertEquals( "matching=" + matching, n, seenReds.size() );
		}
	}

	/**
	 * Regression test: with the default (Linear) curve preset, cycling
	 * through every raw label value must reach every one of the palette's
	 * colors exactly once per cycle, for every palette size -- not just
	 * ones that happen not to collide with the curve's own internal
	 * control-point grid. This previously failed for a 12-color palette
	 * (e.g. the "Set3" qualitative colormap) under Cyclic + Truncate: before
	 * {@link MappingPreset#LINEAR} was special-cased to exactly 2 control
	 * points, it sampled 9 independently-rounded ones, and interpolating
	 * between two of those could deviate from the true identity line by up
	 * to about 1 LUT index -- enough to push one label's lutIndex across a
	 * palette color's Truncate boundary, making that color repeat while its
	 * neighbor never appeared. Sweeping many sizes here (rather than just
	 * n=12) guards against the same class of bug at whichever size it next
	 * happens to line up badly with the curve's grid.
	 */
	@Test
	public void testAllPaletteColorsReachableInCyclicModeForVariousPaletteSizes()
	{
		for ( int n = 2; n <= 20; n++ )
		{
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
			final ColorTableLut palette = new ColorTableLut( positions, red, green, blue, alpha );

			// Default (Linear) preset -- exercises MappingPreset.LINEAR
			// directly, not a hand-crafted curve.
			final MappingModel model = new MappingModel();
			model.setRangeMode( RangeMode.CYCLIC );
			model.setValueMatching( ValueMatching.TRUNCATE );

			final Set< Integer > seenReds = new HashSet<>();
			for ( int label = 0; label < n; label++ )
			{
				final int lutIndex = model.mapToLutIndex( label, 0, 1000, positions );
				final int argb = ColorTableLut.lookupARGB( palette, 0, 255, lutIndex, ValueMatching.TRUNCATE );
				seenReds.add( ( argb >> 16 ) & 0xFF );
			}
			Assert.assertEquals( "n=" + n, n, seenReds.size() );
		}
	}

	/**
	 * Regression test: {@link ColorTableLut#lookupARGBQualitative}'s band
	 * extension (meant for a static, non-cyclic bar, where the last color
	 * would otherwise collapse to a single point) must not be applied on top
	 * of Cyclic mode's own mapping -- {@link MappingModel#cyclicPosition}
	 * already reserves the last color's own full raw-value interval via its
	 * flat-hold branch, so the plain (non-qualitative) lookup already gives
	 * every color, including the last, a correct, contiguous, non-bleeding
	 * interval. Stacking the qualitative extension on top rescales the whole
	 * [0, 1] range and pulls the last color's interval earlier, making it
	 * bleed into the tail of the second-to-last color's interval -- i.e. the
	 * last color appears to show up twice before the actual wrap.
	 */
	@Test
	public void testCyclicTruncateGivesEachColorItsOwnContiguousIntervalWithoutBleeding()
	{
		final int n = 4;
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
		final ColorTableLut palette = new ColorTableLut( positions, red, green, blue, alpha );

		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setValueMatching( ValueMatching.TRUNCATE );

		for ( int label = 0; label < n; label++ )
		{
			final int expectedRed = ( int ) Math.round( 255 * red[ label ] );
			// Densely sample most of the raw-value interval [label, label + 1)
			// (up to label + 0.9, clear of the next interval's boundary
			// tolerance zone -- see Curve's rounding-noise epsilon -- so this
			// doesn't depend on exactly where that tolerance ends): it must
			// consistently resolve to this color, with no bleed-through from
			// the neighboring (in particular, the last) color.
			for ( int step = 0; step < 19; step++ )
			{
				final double v = label + step * 0.05;
				final int lutIndex = model.mapToLutIndex( v, 0, 1000, positions );
				final int argb = ColorTableLut.lookupARGB( palette, 0, 255, lutIndex, ValueMatching.TRUNCATE );
				final int redLevel = ( argb >> 16 ) & 0xFF;
				Assert.assertEquals( "label=" + label + " v=" + v, expectedRed, redLevel );
			}
		}
	}

	/**
	 * Regression test: {@link MappingModel#mapToLutIndex} sweeps continuously
	 * across each label's raw-value interval (needed so
	 * {@link MappingCurvePanel#drawCurve} can show the curve's own true
	 * shape) -- but under {@link RangeMode#CYCLIC} + {@link ValueMatching#TRUNCATE},
	 * if the curve decreases across that interval (e.g. after
	 * {@link MappingModel#invertCurve()}), that continuous sweep makes the
	 * actual color lookup fall through to the <em>previous</em> label's
	 * color for almost the whole interval -- Truncate holds whichever
	 * control point was most recently passed in increasing-position order,
	 * and a decreasing sweep passes the interval's own position immediately
	 * and keeps going. Reported symptom: with a 10-color palette, the last
	 * color barely appeared while the first dominated roughly two labels'
	 * worth of space instead of one. {@link MappingModel#mapToLutIndexForColor}
	 * must not have this problem: every label gets its own color, for its
	 * whole interval, regardless of curve direction.
	 */
	@Test
	public void testMapToLutIndexForColorDoesNotBleedWhenCurveIsInverted()
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
		final ColorTableLut palette = new ColorTableLut( positions, red, green, blue, alpha );

		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setValueMatching( ValueMatching.TRUNCATE );
		model.invertCurve();

		// Inverted: label L consistently shows color (n - 1 - L), not just
		// at the exact integer point but across its whole interval.
		for ( int label = 0; label < n; label++ )
		{
			final int expectedRed = ( int ) Math.round( 255 * red[ n - 1 - label ] );
			for ( int step = 0; step < 19; step++ )
			{
				final double v = label + step * 0.05;
				final int lutIndex = model.mapToLutIndexForColor( v, 0, 1000, positions );
				final int argb = ColorTableLut.lookupARGB( palette, 0, 255, lutIndex, ValueMatching.TRUNCATE );
				final int redLevel = ( argb >> 16 ) & 0xFF;
				Assert.assertEquals( "label=" + label + " v=" + v, expectedRed, redLevel );
			}
		}
	}

	/**
	 * {@link MappingModel#mapToLutIndexForColor}'s label-snapping only makes
	 * sense for Cyclic + a stepped {@link ValueMatching}; it must be a no-op
	 * (identical to {@link MappingModel#mapToLutIndex}) for Interpolate,
	 * where continuous blending across labels is the whole point, and for
	 * {@link RangeMode#FIT}, which has no per-label interval structure to
	 * begin with.
	 */
	@Test
	public void testMapToLutIndexForColorMatchesMapToLutIndexOutsideCyclicTruncate()
	{
		final double[] positions = { 0.0, 0.25, 0.5, 0.75, 1.0 };
		final MappingModel model = new MappingModel();
		model.invertCurve();

		model.setRangeMode( RangeMode.CYCLIC );
		model.setValueMatching( ValueMatching.INTERPOLATE );
		for ( double v = 0; v < 5; v += 0.37 )
			Assert.assertEquals( "v=" + v, model.mapToLutIndex( v, 0, 1000, positions ), model.mapToLutIndexForColor( v, 0, 1000, positions ) );

		model.setRangeMode( RangeMode.FIT );
		model.setValueMatching( ValueMatching.TRUNCATE );
		for ( double v = 0; v < 100; v += 7.3 )
			Assert.assertEquals( "v=" + v, model.mapToLutIndex( v, 0, 100, positions ), model.mapToLutIndexForColor( v, 0, 100, positions ) );
	}

	@Test
	public void testCopyFromDoesNotAlias()
	{
		final MappingModel source = new MappingModel();
		source.applyPreset( MappingPreset.SIGMOID );
		source.setRangeMode( RangeMode.CYCLIC );
		source.setValueMatching( ValueMatching.TRUNCATE );
		source.setTreatMinAsBackground( true );
		source.setBackgroundColor( 0xffaabbcc );

		final MappingModel copy = new MappingModel();
		copy.copyFrom( source );

		Assert.assertEquals( source.getPreset(), copy.getPreset() );
		Assert.assertEquals( source.getRangeMode(), copy.getRangeMode() );
		Assert.assertEquals( source.getValueMatching(), copy.getValueMatching() );
		Assert.assertEquals( source.isTreatMinAsBackground(), copy.isTreatMinAsBackground() );
		Assert.assertEquals( source.getBackgroundColor(), copy.getBackgroundColor() );
		Assert.assertEquals( source.mapToLutIndex( 42, 0, 100, uniform( 256 ) ), copy.mapToLutIndex( 42, 0, 100, uniform( 256 ) ) );

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
