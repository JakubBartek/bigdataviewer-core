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
package bdv.tools.brightness.palette;

/**
 * Small numeric helpers shared by the {@code *PaletteWrapper} classes in this
 * package, split out to avoid duplicating {@link #floorMod(float, float)}
 * between {@link DiscretePaletteWrapper} and {@link ContinuousPaletteWrapper}
 * (both need it for {@link BoundaryCondition#CYCLE}).
 */
final class FloatMath
{
	private FloatMath()
	{
	}

	/**
	 * Floating-point equivalent of {@link Math#floorMod(int, int)} (which has
	 * no float overload): wraps {@code value} into {@code [0, modulus)}.
	 * Unlike Java's {@code %} operator, which returns a <em>negative</em>
	 * result for a negative {@code value} (e.g. {@code -0.5 % 3 == -0.5}, still
	 * outside the domain it was supposed to wrap into) -- the same
	 * "remainder, then add the modulus back if still negative" idiom already
	 * used for cyclic wrapping in {@code MappingModel#cyclicOffset}.
	 */
	static float floorMod( final float value, final float modulus )
	{
		float m = value % modulus;
		if ( m < 0 )
			m += modulus;
		return m;
	}
}
