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
