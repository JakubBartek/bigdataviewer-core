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
package bdv.tools.brightness.presetfunc;

import org.junit.Assert;
import org.junit.Test;

/**
 * Shared behavior is covered by {@link AbstractPresetFuncTest}; this covers
 * the derived domain and the step size surviving a range change.
 */
public class StepPresetFuncTest
{
	// -- the derived domain --------------------------------------------------

	/** {@code getMax() == min + stepSize * schemeRange}, no off-by-one. */
	@Test
	public void testMaxIsDerivedFromMinAndStepSize()
	{
		Assert.assertEquals( 6.0, new StepPresetFunc( 0, 3, 2.0 ).getMax(), 0.0 );
		Assert.assertEquals( 3.0, new StepPresetFunc( 0, 3, 1.0 ).getMax(), 0.0 );
		Assert.assertEquals( 1.5, new StepPresetFunc( 0, 3, 0.5 ).getMax(), 0.0 );
		Assert.assertEquals( 110.0, new StepPresetFunc( 100, 10, 1.0 ).getMax(), 0.0 );
		Assert.assertEquals( -7.0, new StepPresetFunc( -10, 3, 1.0 ).getMax(), 0.0 );
	}

	@Test
	public void testEachFixOwnsOneStepSizeWorthOfRawValues()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 0.0, f.getSchemeValueForRaw( 0 ), 1e-9 );
		Assert.assertEquals( 0.5, f.getSchemeValueForRaw( 1 ), 1e-9 );
		Assert.assertEquals( 1.0, f.getSchemeValueForRaw( 2 ), 1e-9 );
		Assert.assertEquals( 2.0, f.getSchemeValueForRaw( 4 ), 1e-9 );
		// getMax(): one past the last fix
		Assert.assertEquals( 3.0, f.getSchemeValueForRaw( 6 ), 1e-9 );
	}

	/** Repetition is {@code BoundaryCondition.CYCLE}'s job; see {@code PresetColorSchemeWrapperTest}. */
	@Test
	public void testSmallerStepSizeNarrowsTheDomainRatherThanRepeatingTheScheme()
	{
		final StepPresetFunc narrow = new StepPresetFunc( 0, 3, 1.0 );
		final StepPresetFunc wide = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 3.0, narrow.getMax(), 0.0 );
		Assert.assertEquals( 6.0, wide.getMax(), 0.0 );
		// past the narrow domain: clamped, not wrapped
		Assert.assertEquals( 2.0, wide.getSchemeValueForRaw( 4 ), 1e-9 );
		Assert.assertEquals( 3.0, narrow.getSchemeValueForRaw( 4 ), 1e-9 );
	}

	/** A larger step size widens the domain; the scheme is still traversed exactly once. */
	@Test
	public void testLargerStepSizeWidensTheDomain()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 6.0 );

		Assert.assertEquals( 18.0, f.getMax(), 0.0 );
		Assert.assertEquals( 0.0, f.getSchemeValueForRaw( 0 ), 1e-9 );
		Assert.assertEquals( 1.0, f.getSchemeValueForRaw( 6 ), 1e-9 );
		Assert.assertEquals( 3.0, f.getSchemeValueForRaw( 18 ), 1e-9 );
	}

	// -- the default step size -----------------------------------------------

	@Test
	public void testDefaultStepSizeLandsTheDerivedMaxOnTheRequestedOne()
	{
		Assert.assertEquals( 2.0, StepPresetFunc.defaultStepSize( 0, 6, 3 ), 1e-9 );
		Assert.assertEquals( 1.0, StepPresetFunc.defaultStepSize( 0, 3, 3 ), 1e-9 );

		final StepPresetFunc f = new StepPresetFunc( 0, 3, StepPresetFunc.defaultStepSize( 0, 6, 3 ) );
		Assert.assertEquals( 6.0, f.getMax(), 0.0 );
	}

	/** The endpoint reaches {@code getSchemeRange()} rather than wrapping to 0. */
	@Test
	public void testDefaultStepSizeSpreadsTheSchemeOnce()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 0.0, f.getSchemeValueForRaw( 0 ), 1e-6 );
		Assert.assertEquals( 1.0, f.getSchemeValueForRaw( 2 ), 1e-6 );
		Assert.assertEquals( 2.0, f.getSchemeValueForRaw( 4 ), 1e-6 );
		Assert.assertEquals( 3.0, f.getSchemeValueForRaw( 6 ), 1e-6 );
	}

	// -- re-ranging ----------------------------------------------------------

	/** Unlike every other {@link IPresetFunc}, the shape is not stretched to the new range. */
	@Test
	public void testWithRangeKeepsTheStepSizeAndIgnoresTheGivenMax()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );

		final StepPresetFunc moved = f.withRange( 10, 999 );
		Assert.assertEquals( 1.0, moved.getStepSize(), 1e-9 );
		Assert.assertEquals( 10.0, moved.getMin(), 0.0 );
		Assert.assertEquals( 13.0, moved.getMax(), 0.0 );

		Assert.assertEquals( f.withRange( 10, 12 ).getMax(), f.withRange( 10, 1e9 ).getMax(), 0.0 );

		// one raw unit is still one fix, exactly as before
		Assert.assertEquals( 1.5, moved.getSchemeValueForRaw( 11.5 ), 1e-9 );
	}

	@Test
	public void testWithRangeDoesNotMutateTheOriginal()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );
		f.withRange( 100, 200 );

		Assert.assertEquals( 0.0, f.getMin(), 0.0 );
		Assert.assertEquals( 3.0, f.getMax(), 0.0 );
	}

	// -- exactness at fix boundaries ----------------------------------------

	/**
	 * Regression: a boundary landing a hair low floors to the previous fix.
	 * Round trips through a normalized fraction caused this, hidden while the
	 * result was narrowed to {@code float}.
	 */
	@Test
	public void testFixBoundariesAreExactWholeNumbers()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );

		Assert.assertEquals( 0.0, f.getSchemeValueForRaw( 0 ), 0.0 );
		Assert.assertEquals( 1.0, f.getSchemeValueForRaw( 1 ), 0.0 );
		Assert.assertEquals( 2.0, f.getSchemeValueForRaw( 2 ), 0.0 );
		Assert.assertEquals( 3.0, f.getSchemeValueForRaw( 3 ), 0.0 );
	}

	// -- properties ----------------------------------------------------------

	@Test
	public void testOneRawUnitPerFixGivesEveryIntegerItsOwnFix()
	{
		for ( int fixes = 2; fixes <= 64; fixes++ )
			for ( int min = -50; min <= 50; min += 5 )
			{
				final StepPresetFunc f = new StepPresetFunc( min, fixes, 1.0 );
				for ( int i = 0; i <= fixes; i++ )
					Assert.assertEquals( "fixes=" + fixes + " min=" + min + " raw=" + ( min + i ),
							i, f.getSchemeValueForRaw( min + i ), 0.0 );
			}
	}

	/**
	 * Non-dyadic step sizes far from the origin, where the error is thousands
	 * of ULPs of the quotient.
	 */
	@Test
	public void testFixBoundariesResolveExactlyForNonDyadicStepSizesFarFromTheOrigin()
	{
		final double[] awkwardStepSizes = { 0.3, 1.0 / 3.0, 0.7, 1.1, 7.7, 0.123456789, Math.PI };
		final double[] awkwardOrigins = { 4610.39727228942, -8123.7, 0.1, -0.3, 65535.5 };

		for ( int fixes = 2; fixes <= 40; fixes++ )
			for ( final double stepSize : awkwardStepSizes )
				for ( final double min : awkwardOrigins )
				{
					final StepPresetFunc f = new StepPresetFunc( min, fixes, stepSize );
					for ( int k = 0; k <= fixes; k++ )
						Assert.assertEquals( "fixes=" + fixes + " step=" + stepSize + " min=" + min + " boundary=" + k,
								k, Math.floor( f.getSchemeValueForRaw( min + k * stepSize ) ), 0.0 );
				}
	}

	/** Once failed for 580 of 7176 pairs, sending the top of the range to the first color. */
	@Test
	public void testDefaultStepSizeAlwaysReachesTheLastFix()
	{
		final double[] spans = { 1, 37, 255, 1000, 4095, 65535, 65536, 1e6 };
		for ( int fixes = 2; fixes <= 300; fixes++ )
			for ( final double min : new double[] { 0, 1, -1000, 12.5 } )
				for ( final double span : spans )
				{
					final double max = min + span;
					final StepPresetFunc f = new StepPresetFunc( min, fixes, StepPresetFunc.defaultStepSize( min, max, fixes ) );
					final String where = "fixes=" + fixes + " range=[" + min + "," + max + "]";

					// ULPs of the operands, not of max, which can be 0
					final double spanUlps = 4 * Math.ulp( Math.max( Math.abs( min ), Math.abs( max ) ) );
					Assert.assertEquals( where + " derived max", max, f.getMax(), spanUlps );
					Assert.assertEquals( where + " at max", fixes, f.getSchemeValueForRaw( max ), 1e-9 );
					Assert.assertTrue( where + " at max must not wrap to 0",
							f.getSchemeValueForRaw( max ) > fixes - 1 );
				}
	}

	// -- validation ----------------------------------------------------------

	@Test
	public void testRejectsNonPositiveStepSize()
	{
		for ( final double bad : new double[] { 0.0, -1.0, Double.NaN } )
		{
			try
			{
				new StepPresetFunc( 0, 3, bad );
				Assert.fail( "expected IllegalArgumentException for stepSize " + bad );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}

	/** The derived maximum would equal the minimum. */
	@Test( expected = IllegalArgumentException.class )
	public void testRejectsAStepSizeBelowTheResolutionOfTheMinimum()
	{
		new StepPresetFunc( 1e300, 3, 1.0 );
	}
}
