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
 * Test cases for {@link SigmoidPresetFunc}. Endpoint and out-of-range
 * behavior shared by every {@link PresetFunc} is covered generically by
 * {@link AbstractPresetFuncTest}; this only checks the shape distinctive to
 * this class. Expected values computed independently from the exact same
 * formula {@code MappingPreset#SIGMOID} uses ({@code k = 10}), not derived
 * from this implementation.
 */
public class SigmoidPresetFuncTest
{
	/** min=100, max=200, paletteRangeLength=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static SigmoidPresetFunc scaled()
	{
		return new SigmoidPresetFunc( 100f, 200f, 10 );
	}

	@Test
	public void testShapeAtRepresentativeValues()
	{
		final SigmoidPresetFunc f = scaled();
		Assert.assertEquals( 0.70104f, f.getPaletteValueForRaw( 125f ), 1e-3f );
		Assert.assertEquals( 5.0f, f.getPaletteValueForRaw( 150f ), 1e-3f );
		Assert.assertEquals( 9.29896f, f.getPaletteValueForRaw( 175f ), 1e-3f );
	}

	/** The logistic is symmetric about the midpoint, so its normalized form must be self-symmetric there regardless of steepness. */
	@Test
	public void testMidpointIsExactlyHalfway()
	{
		Assert.assertEquals( 5f, scaled().getPaletteValueForRaw( 150f ), 1e-4f );
	}

	/** Steep through the middle: two points equally spaced around the midpoint should straddle it more than a linear ramp would. */
	@Test
	public void testRisesFasterThroughTheMiddleThanLinear()
	{
		final SigmoidPresetFunc sigmoid = scaled();
		final LinearPresetFunc linear = new LinearPresetFunc( 100f, 200f, 10 );

		// Just past the midpoint, sigmoid should already be further along than linear.
		Assert.assertTrue( sigmoid.getPaletteValueForRaw( 155f ) > linear.getPaletteValueForRaw( 155f ) );
		// Symmetric point below the midpoint: sigmoid lags behind linear.
		Assert.assertTrue( sigmoid.getPaletteValueForRaw( 145f ) < linear.getPaletteValueForRaw( 145f ) );
	}
}
