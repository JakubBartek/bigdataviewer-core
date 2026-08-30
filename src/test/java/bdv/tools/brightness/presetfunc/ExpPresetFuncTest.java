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
 * Test cases for {@link ExpPresetFunc}. Endpoint and out-of-range
 * behavior shared by every {@link PresetFunc} is covered generically by
 * {@link AbstractPresetFuncTest}; this only checks the shape distinctive to
 * this class. Expected values computed independently from the formula
 * ({@code k = 4}), not derived from this implementation.
 */
public class ExpPresetFuncTest
{
	/** min=100, max=200, paletteRangeLength=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static ExpPresetFunc scaled()
	{
		return new ExpPresetFunc( 100f, 200f, 10 );
	}

	@Test
	public void testShapeAtRepresentativeValues()
	{
		final ExpPresetFunc f = scaled();
		Assert.assertEquals( 0.32059f, f.getPaletteValueForRaw( 125f ), 1e-3f );
		Assert.assertEquals( 1.19203f, f.getPaletteValueForRaw( 150f ), 1e-3f );
		Assert.assertEquals( 3.56086f, f.getPaletteValueForRaw( 175f ), 1e-3f );
	}

	/** Mirror image of {@link LogPresetFunc}: stays low then rises quickly near the high end, so it lags a linear ramp near the low end. */
	@Test
	public void testStaysBelowLinearNearTheLowEnd()
	{
		final ExpPresetFunc exp = scaled();
		final LinearPresetFunc linear = new LinearPresetFunc( 100f, 200f, 10 );

		Assert.assertTrue( exp.getPaletteValueForRaw( 110f ) < linear.getPaletteValueForRaw( 110f ) );
	}

	/** Gains more in the last quarter of the range than in the first -- the opposite of {@link LogPresetFunc}. */
	@Test
	public void testGainsMoreInTheLastQuarterThanTheFirst()
	{
		final ExpPresetFunc f = scaled();
		final float gainFirstQuarter = f.getPaletteValueForRaw( 125f ) - f.getPaletteValueForRaw( 100f );
		final float gainLastQuarter = f.getPaletteValueForRaw( 200f ) - f.getPaletteValueForRaw( 175f );
		Assert.assertTrue( gainLastQuarter > gainFirstQuarter );
	}
}
