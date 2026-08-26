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
 * Test cases for {@link LogPresetFunc}. Endpoint and out-of-range
 * behavior shared by every {@link PresetFunc} is covered generically by
 * {@link AbstractPresetFuncTest}; this only checks the shape distinctive to
 * this class. Expected values computed independently from the exact same
 * formula {@code MappingPreset#LOG} uses ({@code k = 20}), not derived from
 * this implementation.
 */
public class LogPresetFuncTest
{
	/** min=100, max=200, paletteRangeLength=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static LogPresetFunc scaled()
	{
		return new LogPresetFunc( 100f, 200f, 10 );
	}

	@Test
	public void testShapeAtRepresentativeValues()
	{
		final LogPresetFunc f = scaled();
		Assert.assertEquals( 5.88519f, f.getPaletteValueForRaw( 125f ), 1e-3f );
		Assert.assertEquals( 7.87610f, f.getPaletteValueForRaw( 150f ), 1e-3f );
		Assert.assertEquals( 9.10681f, f.getPaletteValueForRaw( 175f ), 1e-3f );
	}

	/** Rises quickly near the low end: past the midpoint it must already be well past half of paletteRangeLength, unlike a linear ramp. */
	@Test
	public void testRisesFasterThanLinearNearTheLowEnd()
	{
		final LogPresetFunc log = scaled();
		final LinearPresetFunc linear = new LinearPresetFunc( 100f, 200f, 10 );

		Assert.assertTrue( log.getPaletteValueForRaw( 110f ) > linear.getPaletteValueForRaw( 110f ) );
	}

	/** Flattens out near the high end: the gain from the last quarter of the range must be smaller than from the first quarter. */
	@Test
	public void testFlattensOutNearTheHighEnd()
	{
		final LogPresetFunc f = scaled();
		final float gainFirstQuarter = f.getPaletteValueForRaw( 125f ) - f.getPaletteValueForRaw( 100f );
		final float gainLastQuarter = f.getPaletteValueForRaw( 200f ) - f.getPaletteValueForRaw( 175f );
		Assert.assertTrue( gainLastQuarter < gainFirstQuarter );
	}
}
