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
 * Test cases for {@link StepPresetFunc}: what a step size in raw units
 * actually does. The shared getters/endpoint behavior is covered generically
 * in {@link AbstractPresetFuncTest}; what is distinctive here is that the
 * shape is parameterized in the caller's own units rather than by a fixed
 * constant -- so the step size survives a range change, and the domain's far
 * end is derived from it rather than given.
 */
public class StepPresetFuncTest
{
	// -- the derived domain --------------------------------------------------

	/**
	 * The defining property: the far end of the domain is
	 * {@code min + stepSize * paletteRangeLength}, the raw value at which the
	 * ramp has just run off the end of the palette. Stop {@code i} covers raw
	 * {@code [min + i * stepSize, min + (i + 1) * stepSize)}, so the last stop's
	 * far edge -- and hence {@link StepPresetFunc#getMax()} -- is at
	 * {@code stepSize * paletteRangeLength} above {@code min}, with no
	 * off-by-one either way.
	 */
	@Test
	public void testMaxIsDerivedFromMinAndStepSize()
	{
		Assert.assertEquals( 6.0, new StepPresetFunc( 0, 3, 2.0 ).getMax(), 0.0 );
		Assert.assertEquals( 3.0, new StepPresetFunc( 0, 3, 1.0 ).getMax(), 0.0 );
		Assert.assertEquals( 1.5, new StepPresetFunc( 0, 3, 0.5 ).getMax(), 0.0 );
		Assert.assertEquals( 110.0, new StepPresetFunc( 100, 10, 1.0 ).getMax(), 0.0 );
		Assert.assertEquals( -7.0, new StepPresetFunc( -10, 3, 1.0 ).getMax(), 0.0 );
	}

	/**
	 * Each stop owns exactly {@code stepSize} raw values, and the derived
	 * maximum is the first raw value past the last stop -- where a discrete
	 * scheme's clamp holds it on the last stop and a {@code CYCLE} boundary
	 * wraps it to the first.
	 */
	@Test
	public void testEachStopOwnsOneStepSizeWorthOfRawValues()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 0.0, f.getPaletteValueForRaw( 0 ), 1e-9 );
		Assert.assertEquals( 0.5, f.getPaletteValueForRaw( 1 ), 1e-9 );
		Assert.assertEquals( 1.0, f.getPaletteValueForRaw( 2 ), 1e-9 );
		Assert.assertEquals( 2.0, f.getPaletteValueForRaw( 4 ), 1e-9 );
		// getMax(): one past the last stop, which the color scheme clamps back onto it.
		Assert.assertEquals( 3.0, f.getPaletteValueForRaw( 6 ), 1e-9 );
	}

	/**
	 * A smaller step size does not fit more passes into a fixed domain -- there
	 * is no fixed domain to fit them into. It makes each color cover fewer raw
	 * values, so the whole palette spans a narrower range and the boundary
	 * condition takes over sooner. Repeating the palette is
	 * {@code BoundaryCondition.CYCLE}'s job, checked at the wrapper level in
	 * {@code PresetPaletteWrapperTest}.
	 */
	@Test
	public void testSmallerStepSizeNarrowsTheDomainRatherThanRepeatingThePalette()
	{
		final StepPresetFunc narrow = new StepPresetFunc( 0, 3, 1.0 );
		final StepPresetFunc wide = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 3.0, narrow.getMax(), 0.0 );
		Assert.assertEquals( 6.0, wide.getMax(), 0.0 );
		// Raw 4 is inside the wide one's domain and past the narrow one's, so
		// the narrow one clamps it to the top of the palette instead of wrapping.
		Assert.assertEquals( 2.0, wide.getPaletteValueForRaw( 4 ), 1e-9 );
		Assert.assertEquals( 3.0, narrow.getPaletteValueForRaw( 4 ), 1e-9 );
	}

	/** A larger step size widens the domain; the palette is still traversed exactly once. */
	@Test
	public void testLargerStepSizeWidensTheDomain()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 6.0 );

		Assert.assertEquals( 18.0, f.getMax(), 0.0 );
		Assert.assertEquals( 0.0, f.getPaletteValueForRaw( 0 ), 1e-9 );
		Assert.assertEquals( 1.0, f.getPaletteValueForRaw( 6 ), 1e-9 );
		Assert.assertEquals( 3.0, f.getPaletteValueForRaw( 18 ), 1e-9 );
	}

	// -- the default step size -----------------------------------------------

	/**
	 * {@link StepPresetFunc#defaultStepSize} is the one place a caller's desired
	 * maximum enters: it converts that maximum into the step size whose derived
	 * {@link StepPresetFunc#getMax()} lands back on it.
	 */
	@Test
	public void testDefaultStepSizeLandsTheDerivedMaxOnTheRequestedOne()
	{
		Assert.assertEquals( 2.0, StepPresetFunc.defaultStepSize( 0, 6, 3 ), 1e-9 );
		Assert.assertEquals( 1.0, StepPresetFunc.defaultStepSize( 0, 3, 3 ), 1e-9 );

		final StepPresetFunc f = new StepPresetFunc( 0, 3, StepPresetFunc.defaultStepSize( 0, 6, 3 ) );
		Assert.assertEquals( 6.0, f.getMax(), 0.0 );
	}

	/**
	 * At the default step size the function is a plain ramp over the whole
	 * palette -- the behavior of a discrete palette with no step size chosen --
	 * so the endpoint reaches {@code getPaletteRangeLength()} rather than
	 * wrapping back to 0.
	 */
	@Test
	public void testDefaultStepSizeSpreadsThePaletteOnce()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 2.0 );

		Assert.assertEquals( 0.0, f.getPaletteValueForRaw( 0 ), 1e-6 );
		Assert.assertEquals( 1.0, f.getPaletteValueForRaw( 2 ), 1e-6 );
		Assert.assertEquals( 2.0, f.getPaletteValueForRaw( 4 ), 1e-6 );
		Assert.assertEquals( 3.0, f.getPaletteValueForRaw( 6 ), 1e-6 );
	}

	// -- re-ranging ----------------------------------------------------------

	/**
	 * The step size is in raw units, so a display-range change must leave it
	 * alone -- the opposite of every other {@link PresetFunc}, whose shape is
	 * stretched to the new range. Since the domain's far end follows from the
	 * step size, that means {@code withRange} can only move where the palette
	 * starts: the {@code max} it is handed is ignored outright.
	 */
	@Test
	public void testWithRangeKeepsTheStepSizeAndIgnoresTheGivenMax()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );

		final StepPresetFunc moved = f.withRange( 10, 999 );
		Assert.assertEquals( 1.0, moved.getStepSize(), 1e-9 );
		Assert.assertEquals( 10.0, moved.getMin(), 0.0 );
		Assert.assertEquals( 13.0, moved.getMax(), 0.0 );

		// The same max with a different min, and a wildly different max with the
		// same min, both land on the same domain width -- max carries no
		// information at all.
		Assert.assertEquals( f.withRange( 10, 12 ).getMax(), f.withRange( 10, 1e9 ).getMax(), 0.0 );

		// one raw unit is still one stop, exactly as before
		Assert.assertEquals( 1.5, moved.getPaletteValueForRaw( 11.5 ), 1e-9 );
	}

	@Test
	public void testWithRangeDoesNotMutateTheOriginal()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );
		f.withRange( 100, 200 );

		Assert.assertEquals( 0.0, f.getMin(), 0.0 );
		Assert.assertEquals( 3.0, f.getMax(), 0.0 );
	}

	// -- exactness at stop boundaries ----------------------------------------

	/**
	 * Regression: a raw value sitting exactly on a color-stop boundary must
	 * produce exactly that whole palette value, because a discrete scheme floors
	 * whatever it is given and a boundary that lands a hair low picks the stop
	 * before it. Two routes to the answer used to get this wrong by about one
	 * part in 10^16 -- reaching the position as {@code t * periods}, which
	 * multiplied two separately-rounded copies of {@code (max - min)} back
	 * together, and returning a {@code [0, 1]} fraction for the caller to scale
	 * by the stop count, which divided by that count only to multiply it back.
	 * Neither was visible while the result was narrowed to {@code float} on the
	 * way out, since that narrowing rounded the difference away.
	 */
	@Test
	public void testStopBoundariesAreExactWholeNumbers()
	{
		final StepPresetFunc f = new StepPresetFunc( 0, 3, 1.0 );

		// Exact, with no tolerance: that is the whole point.
		Assert.assertEquals( 0.0, f.getPaletteValueForRaw( 0 ), 0.0 );
		Assert.assertEquals( 1.0, f.getPaletteValueForRaw( 1 ), 0.0 );
		Assert.assertEquals( 2.0, f.getPaletteValueForRaw( 2 ), 0.0 );
		Assert.assertEquals( 3.0, f.getPaletteValueForRaw( 3 ), 0.0 );
	}

	// -- properties ----------------------------------------------------------
	//
	// The repeated-color regressions found here were all first noticed by
	// picking a configuration by hand and seeing wrong colors, with nothing
	// about it to say why that one and not its neighbour. The tests below state
	// the property instead and check it across a range of configurations.

	/**
	 * With one raw unit per stop, every integer raw value in the domain lands on
	 * its own stop, for every stop count and every starting point -- no two
	 * consecutive raw values may share a color.
	 */
	@Test
	public void testOneRawUnitPerStopGivesEveryIntegerItsOwnStop()
	{
		for ( int stops = 2; stops <= 64; stops++ )
			for ( int min = -50; min <= 50; min += 5 )
			{
				final StepPresetFunc f = new StepPresetFunc( min, stops, 1.0 );
				for ( int i = 0; i <= stops; i++ )
					Assert.assertEquals( "stops=" + stops + " min=" + min + " raw=" + ( min + i ),
							i, f.getPaletteValueForRaw( min + i ), 0.0 );
			}
	}

	/**
	 * The same property where the arithmetic cannot be exact: a step size that
	 * is not a dyadic fraction, and a domain far from the origin so subtracting
	 * {@code min} cancels the low bits of the raw value away. A raw value on
	 * stop boundary {@code k} must still produce exactly {@code k} -- these are
	 * the cases whose error is thousands of ULPs of the quotient, and so exactly
	 * the ones a tolerance measured in ULPs of the quotient would miss (see
	 * {@link StepPresetFunc#paletteValueForClampedRaw(double)}).
	 */
	@Test
	public void testStopBoundariesResolveExactlyForNonDyadicStepSizesFarFromTheOrigin()
	{
		final double[] awkwardStepSizes = { 0.3, 1.0 / 3.0, 0.7, 1.1, 7.7, 0.123456789, Math.PI };
		final double[] awkwardOrigins = { 4610.39727228942, -8123.7, 0.1, -0.3, 65535.5 };

		for ( int stops = 2; stops <= 40; stops++ )
			for ( final double stepSize : awkwardStepSizes )
				for ( final double min : awkwardOrigins )
				{
					final StepPresetFunc f = new StepPresetFunc( min, stops, stepSize );
					for ( int k = 0; k <= stops; k++ )
						Assert.assertEquals( "stops=" + stops + " step=" + stepSize + " min=" + min + " boundary=" + k,
								k, Math.floor( f.getPaletteValueForRaw( min + k * stepSize ) ), 0.0 );
				}
	}

	/**
	 * The default step size means "one pass across the requested range", so the
	 * top of that range must reach the <em>last</em> stop -- for every stop count
	 * and range, not just the ones where {@code (max - min) / stops * stops}
	 * happens to come back to {@code max - min} exactly. Before the domain was
	 * derived, this failed for 580 of 7176 sampled pairs, sending the top of the
	 * display range to the first color instead of the last.
	 */
	@Test
	public void testDefaultStepSizeAlwaysReachesTheLastStop()
	{
		final double[] spans = { 1, 37, 255, 1000, 4095, 65535, 65536, 1e6 };
		for ( int stops = 2; stops <= 300; stops++ )
			for ( final double min : new double[] { 0, 1, -1000, 12.5 } )
				for ( final double span : spans )
				{
					final double max = min + span;
					final StepPresetFunc f = new StepPresetFunc( min, stops, StepPresetFunc.defaultStepSize( min, max, stops ) );
					final String where = "stops=" + stops + " range=[" + min + "," + max + "]";

					// The requested top of the range is the last stop's far edge.
					// Compared to a few ULPs of the range's own magnitude rather
					// than of max: max can be 0 (range [-1000, 0]) while the
					// rounding that reaches it is of the span, so a tolerance
					// scaled to max would be zero exactly where it is needed.
					final double spanUlps = 4 * Math.ulp( Math.max( Math.abs( min ), Math.abs( max ) ) );
					Assert.assertEquals( where + " derived max", max, f.getMax(), spanUlps );
					// ...so it produces the top of the palette, which a discrete
					// scheme clamps onto the last stop -- never wrapping to the first.
					Assert.assertEquals( where + " at max", stops, f.getPaletteValueForRaw( max ), 1e-9 );
					Assert.assertTrue( where + " at max must not wrap to 0",
							f.getPaletteValueForRaw( max ) > stops - 1 );
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

	/**
	 * A step size too fine to resolve at the given magnitude leaves the derived
	 * maximum equal to the minimum, which is not a usable domain -- rejected
	 * rather than silently producing a function whose every raw value is the
	 * first stop.
	 */
	@Test( expected = IllegalArgumentException.class )
	public void testRejectsAStepSizeBelowTheResolutionOfTheMinimum()
	{
		new StepPresetFunc( 1e300, 3, 1.0 );
	}
}
