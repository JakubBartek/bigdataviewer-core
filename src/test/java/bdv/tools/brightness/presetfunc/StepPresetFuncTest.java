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
 * constant -- so the palette can repeat within the domain, and the step size
 * survives a range change.
 */
public class StepPresetFuncTest
{
	@Test
	public void testDefaultStepSizeIsOneFullPass()
	{
		Assert.assertEquals( 2.0, StepPresetFunc.defaultStepSize( 0f, 6f, 3 ), 1e-9 );
		Assert.assertEquals( 1.0, StepPresetFunc.defaultStepSize( 0f, 3f, 3 ), 1e-9 );

		final StepPresetFunc f = new StepPresetFunc( 0f, 6f, 3, StepPresetFunc.defaultStepSize( 0f, 6f, 3 ) );
		Assert.assertEquals( 1.0, f.getPeriods(), 1e-9 );
	}

	/**
	 * At the default step size the function is a plain ramp over the whole
	 * palette -- the behavior a discrete palette has with no step size chosen,
	 * so the endpoint still reaches {@code getPaletteRangeLength()} rather than
	 * wrapping back to 0.
	 */
	@Test
	public void testDefaultStepSizeSpreadsThePaletteOnce()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 6f, 3, 2.0 );

		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 0f ), 1e-6 );
		Assert.assertEquals( 1f, f.getPaletteValueForRaw( 2f ), 1e-6 );
		Assert.assertEquals( 2f, f.getPaletteValueForRaw( 4f ), 1e-6 );
		Assert.assertEquals( 3f, f.getPaletteValueForRaw( 6f ), 1e-6 );
	}

	/**
	 * A step size smaller than the default makes the palette repeat inside the
	 * domain: with 3 stops over [0, 6] and one stop per raw unit, the palette
	 * runs out at raw 3 and starts over.
	 */
	@Test
	public void testSmallerStepSizeRepeatsThePaletteWithinTheDomain()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 6f, 3, 1.0 );
		Assert.assertEquals( 2.0, f.getPeriods(), 1e-9 );

		Assert.assertEquals( 0.5f, f.getPaletteValueForRaw( 0.5f ), 1e-5 );
		Assert.assertEquals( 1.5f, f.getPaletteValueForRaw( 1.5f ), 1e-5 );
		Assert.assertEquals( 2.5f, f.getPaletteValueForRaw( 2.5f ), 1e-5 );
		// second pass: back to the start of the palette
		Assert.assertEquals( 0.5f, f.getPaletteValueForRaw( 3.5f ), 1e-5 );
		Assert.assertEquals( 1.5f, f.getPaletteValueForRaw( 4.5f ), 1e-5 );
		Assert.assertEquals( 2.5f, f.getPaletteValueForRaw( 5.5f ), 1e-5 );
	}

	/** A step size larger than the default simply never reaches the rest of the palette; it does not stretch to fit. */
	@Test
	public void testLargerStepSizeUsesOnlyPartOfThePalette()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 6f, 3, 6.0 );

		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 0f ), 1e-6 );
		Assert.assertEquals( 0.5f, f.getPaletteValueForRaw( 3f ), 1e-6 );
		// the top of the domain only gets a third of the way through the palette
		Assert.assertEquals( 1f, f.getPaletteValueForRaw( 6f ), 1e-6 );
	}

	/**
	 * The step size is in raw units, so a display-range change must leave it
	 * alone and change how many times the palette repeats instead -- the
	 * opposite of every other {@link PresetFunc}, whose shape is stretched to
	 * the new range.
	 */
	@Test
	public void testWithRangeKeepsTheStepSizeAndRescalesThePeriods()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 6f, 3, 1.0 );
		final StepPresetFunc wider = f.withRange( 0f, 12f );

		Assert.assertEquals( 1.0, wider.getStepSize(), 1e-9 );
		Assert.assertEquals( 2.0, f.getPeriods(), 1e-9 );
		Assert.assertEquals( 4.0, wider.getPeriods(), 1e-9 );
		// one raw unit is still one stop, exactly as before
		Assert.assertEquals( 1.5f, wider.getPaletteValueForRaw( 1.5f ), 1e-5 );
	}

	/**
	 * Regression: with stepSize 1 and a domain whose length is not a whole
	 * multiple of {@code paletteRangeLength * stepSize} (10 raw units, 3
	 * stops), consecutive integer raw values must still land on consecutive,
	 * non-repeating stops. Narrowing the normalized {@code t} to {@code float}
	 * before multiplying by {@link #getPeriods()} lost enough precision (e.g.
	 * {@code 0.7f} is actually {@code 0.69999998807907104...}) to push an
	 * exact color-stop boundary a hair below its integer value, so flooring it
	 * landed on the previous stop instead of advancing -- a discrete palette
	 * showing the same color for two consecutive raw values.
	 */
	@Test
	public void testStepSizeOneWithNonDivisibleRangeDoesNotRepeatStops()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 10f, 3, 1.0 );
		final int[] expectedStop = { 0, 1, 2, 0, 1, 2, 0, 1, 2 };
		for ( int r = 0; r < expectedStop.length; r++ )
			Assert.assertEquals( "raw value " + r, expectedStop[ r ], ( int ) Math.floor( f.getPaletteValueForRaw( r ) ) );
	}

	/**
	 * Regression: a raw value sitting exactly on a color-stop boundary must
	 * begin the next pass through the palette, not finish the previous one.
	 * With 3 stops over [0, 11] at one raw unit per stop the palette wraps every
	 * 3 raw units, so raw 3, 6 and 9 each start a fresh pass -- but computing
	 * the position as {@code t * periods} multiplied two separately-rounded
	 * halves of {@code (max - min)} back together, and the roundings did not
	 * cancel: the result came out a hair short of the wrap and so returned
	 * {@code paletteRangeLength}, which a discrete scheme resolves to the
	 * <em>last</em> stop, where 0 (the first) was due. Under
	 * {@code BoundaryCondition.CYCLE} that reads as the last color twice in a
	 * row instead of last-then-first. Whether it happened depended on the exact
	 * domain length, which is why [0, 10] (the case above) is unaffected while
	 * [0, 11] is not.
	 */
	@Test
	public void testExactStopBoundariesBeginTheNextPass()
	{
		final StepPresetFunc f = new StepPresetFunc( 0f, 11f, 3, 1.0 );

		// Exactly 0, not "almost 3": no tolerance, that is the whole point.
		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 3f ), 0f );
		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 6f ), 0f );
		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 9f ), 0f );

		// ...and across the whole domain one raw unit is still one stop.
		for ( int r = 0; r <= 11; r++ )
			Assert.assertEquals( "raw value " + r, r % 3, ( int ) Math.floor( f.getPaletteValueForRaw( r ) ) );
	}

	// -- properties ----------------------------------------------------------
	//
	// The three regressions above were all found by picking a domain length by
	// hand and noticing the colors were wrong -- [0, 10] happens to be fine,
	// [0, 11] is not, and nothing about either says which. The tests below stop
	// guessing and state the property instead, then check it over every domain
	// length in a range. They are what would have caught all three before they
	// shipped.

	/**
	 * The property behind every repeated-color bug found here so far: with one
	 * raw unit per stop, consecutive integer raw values must land on
	 * consecutive stops, wrapping cleanly back to the first, for
	 * <em>every</em> domain length -- not just the ones someone thought to
	 * write a test for.
	 */
	@Test
	public void testOneRawUnitPerStopAdvancesOneStopForEveryDomainLength()
	{
		for ( int stops = 2; stops <= 8; stops++ )
			for ( int max = 2; max <= 120; max++ )
			{
				final StepPresetFunc f = new StepPresetFunc( 0, max, stops, 1.0 );
				// The very top of the domain is excluded when the palette does
				// not repeat across it: a single pass is defined to END on the
				// last stop rather than wrap past it onto the first (see
				// testDefaultStepSizeSpreadsThePaletteOnce). Wherever it does
				// repeat, max is just another stop boundary and is included.
				final int highestWrappingRaw = f.getPeriods() > 1.0 ? max : max - 1;
				for ( int raw = 0; raw <= highestWrappingRaw; raw++ )
					Assert.assertEquals( "stops=" + stops + " domain=[0," + max + "] raw=" + raw,
							raw % stops, ( int ) Math.floor( f.getPaletteValueForRaw( raw ) ) );
			}
	}

	/**
	 * The same property where the arithmetic cannot be exact: a step size that
	 * is not a dyadic fraction, and a domain far from the origin so that
	 * subtracting {@code min} cancels the low bits of the raw value away. A raw
	 * value sitting on stop boundary {@code k} must still resolve to stop
	 * {@code k % stops} -- these are the cases whose error is thousands of ULPs
	 * of the quotient, and they are exactly the ones a tolerance measured in
	 * ULPs of the quotient would miss (see
	 * {@link StepPresetFunc#paletteValueForClampedRaw(double)}).
	 */
	@Test
	public void testStopBoundariesResolveExactlyForNonDyadicStepSizesFarFromTheOrigin()
	{
		final double[] awkwardStepSizes = { 0.3, 1.0 / 3.0, 0.7, 1.1, 7.7, 0.123456789, Math.PI };
		final double[] awkwardOrigins = { 4610.39727228942, -8123.7, 0.1, -0.3, 65535.5 };

		for ( int stops = 2; stops <= 6; stops++ )
			for ( final double stepSize : awkwardStepSizes )
				for ( final double min : awkwardOrigins )
				{
					final int lastStop = 200;
					final StepPresetFunc f = new StepPresetFunc( min, min + lastStop * stepSize, stops, stepSize );
					for ( int k = 0; k < lastStop; k++ )
						Assert.assertEquals( "stops=" + stops + " step=" + stepSize + " min=" + min + " boundary=" + k,
								k % stops, ( int ) Math.floor( f.getPaletteValueForRaw( min + k * stepSize ) ) );
				}
	}

	/**
	 * The default step size means "one pass across the domain", so
	 * {@link StepPresetFunc#getMax()} must reach the <em>last</em> stop -- for
	 * every stop count and display range, not just the ones where
	 * {@code (max - min) / stops * stops} happens to come back to
	 * {@code max - min} exactly. Before the periods snap this failed for 580 of
	 * 7176 sampled pairs, sending the top of the display range to the first
	 * color instead of the last.
	 */
	@Test
	public void testDefaultStepSizeAlwaysReachesTheLastStop()
	{
		final double[] ranges = { 1, 37, 255, 1000, 4095, 65535, 65536, 1e6 };
		for ( int stops = 2; stops <= 300; stops++ )
			for ( final double min : new double[] { 0, 1, -1000, 12.5 } )
				for ( final double span : ranges )
				{
					final double max = min + span;
					final StepPresetFunc f = new StepPresetFunc( min, max, stops, StepPresetFunc.defaultStepSize( min, max, stops ) );
					Assert.assertEquals( "stops=" + stops + " domain=[" + min + "," + max + "] periods",
							1.0, f.getPeriods(), 0.0 );
					Assert.assertEquals( "stops=" + stops + " domain=[" + min + "," + max + "] at max",
							stops - 1, Math.min( stops - 1, ( int ) Math.floor( f.getPaletteValueForRaw( max ) ) ) );
					Assert.assertTrue( "stops=" + stops + " domain=[" + min + "," + max + "] at max must not wrap to 0",
							f.getPaletteValueForRaw( max ) > stops - 1 );
				}
	}

	@Test
	public void testRejectsNonPositiveStepSize()
	{
		for ( final double bad : new double[] { 0.0, -1.0, Double.NaN } )
		{
			try
			{
				new StepPresetFunc( 0f, 6f, 3, bad );
				Assert.fail( "expected IllegalArgumentException for stepSize " + bad );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}
}
