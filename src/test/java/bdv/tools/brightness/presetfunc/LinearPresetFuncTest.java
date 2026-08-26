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
 * Test cases for {@link LinearPresetFunc}. Endpoint and out-of-range behavior
 * shared by every {@link PresetFunc} is covered generically by
 * {@link AbstractPresetFuncTest}; this only checks the shape distinctive to
 * this class: proportional, unlike the others.
 */
public class LinearPresetFuncTest
{
	/** min=100, max=200, paletteRangeLength=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static LinearPresetFunc scaled()
	{
		return new LinearPresetFunc( 100f, 200f, 10 );
	}

	@Test
	public void testProportionalToRawValue()
	{
		final LinearPresetFunc f = scaled();
		Assert.assertEquals( 2.5f, f.getPaletteValueForRaw( 125f ), 1e-4f );
		Assert.assertEquals( 5.0f, f.getPaletteValueForRaw( 150f ), 1e-4f );
		Assert.assertEquals( 7.5f, f.getPaletteValueForRaw( 175f ), 1e-4f );
	}

	@Test
	public void testUnitRangeIsExactlyTheIdentity()
	{
		final LinearPresetFunc f = new LinearPresetFunc( 0f, 1f, 1 );
		for ( float raw = 0f; raw <= 1f; raw += 0.1f )
			Assert.assertEquals( raw, f.getPaletteValueForRaw( raw ), 1e-5f );
	}
}
