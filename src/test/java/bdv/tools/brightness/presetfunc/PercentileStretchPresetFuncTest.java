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
 * Test cases for {@link PercentileStretchPresetFunc}. Endpoint and
 * out-of-range behavior shared by every {@link PresetFunc} is covered
 * generically by {@link AbstractPresetFuncTest}; this only checks the shape
 * distinctive to this class. Expected values computed independently from the
 * exact same formula {@code MappingPreset#PERCENTILE_STRETCH} uses (clamp to
 * {@code [0.02, 0.98]} then rescale linearly), not derived from this
 * implementation.
 */
public class PercentileStretchPresetFuncTest
{
	/** min=100, max=200, delkaIntervalu=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static PercentileStretchPresetFunc scaled()
	{
		return new PercentileStretchPresetFunc( 100f, 200f, 10f );
	}

	@Test
	public void testShapeAtRepresentativeValues()
	{
		final PercentileStretchPresetFunc f = scaled();
		Assert.assertEquals( 2.39583f, f.getPaletteValueForRaw( 125f ), 1e-3f );
		Assert.assertEquals( 5.0f, f.getPaletteValueForRaw( 150f ), 1e-3f );
		Assert.assertEquals( 7.60417f, f.getPaletteValueForRaw( 175f ), 1e-3f );
	}

	/** Below t=0.02 (raw 102) the shape is flat-clipped to exactly 0, same as at t=0. */
	@Test
	public void testClipsFlatBelowTheTwoPercentile()
	{
		final PercentileStretchPresetFunc f = scaled();
		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 101f ), 1e-4f );
		Assert.assertEquals( 0f, f.getPaletteValueForRaw( 101.9f ), 1e-4f );
	}

	/** Above t=0.98 (raw 198) the shape is flat-clipped to exactly delkaIntervalu, same as at t=1. */
	@Test
	public void testClipsFlatAboveTheNinetyEightPercentile()
	{
		final PercentileStretchPresetFunc f = scaled();
		Assert.assertEquals( 10f, f.getPaletteValueForRaw( 198.1f ), 1e-4f );
		Assert.assertEquals( 10f, f.getPaletteValueForRaw( 199f ), 1e-4f );
	}
}
