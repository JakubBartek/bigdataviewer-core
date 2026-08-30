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

/**
 * An S-shaped curve built from a power-law ratio ({@code t^a / (t^a + (1-t)^a)})
 * rather than a logistic -- a gentler, differently-shaped alternative to
 * {@link SigmoidPresetFunc}, with exponent {@code alpha = 0.5}.
 */
public class AlphaSigmoidPresetFunc extends AbstractPresetFunc
{
	private static final double ALPHA = 0.5;

	public AlphaSigmoidPresetFunc( final float min, final float max, final int paletteRangeLength )
	{
		super( min, max, paletteRangeLength );
	}

	@Override
	double shape( final double t )
	{
		final double tp = Math.pow( t, ALPHA );
		final double tq = Math.pow( 1.0 - t, ALPHA );
		if ( tp + tq == 0 )
			return 0;
		return tp / ( tp + tq );
	}

	@Override
	public AlphaSigmoidPresetFunc withRange( final float min, final float max )
	{
		return new AlphaSigmoidPresetFunc( min, max, getPaletteRangeLength() );
	}
}
