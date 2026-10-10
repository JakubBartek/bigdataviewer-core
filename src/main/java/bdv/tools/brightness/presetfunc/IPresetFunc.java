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
 * Converts a raw image value over {@code [getMin(), getMax()]} into a scheme
 * value in {@code [0, getSchemeRange()]}. Knows nothing about colors or
 * boundary conditions.
 */
public interface IPresetFunc
{
	/** Maps to scheme value {@code 0}, except for {@link CustomInterpPresetFunc}. */
	double getMin();

	/** Maps to {@link #getSchemeRange()}, except for {@link CustomInterpPresetFunc}. */
	double getMax();

	/** Must equal the color scheme's, hence a whole fix count. */
	int getSchemeRange();

	/**
	 * Clamps {@code rawValue} into the domain. All {@code double}: a
	 * {@code float} merges integers above 2^24 (label ids).
	 */
	double getSchemeValueForRaw( double rawValue );

	/** A copy with the same shape over a new {@code [min, max]}. */
	IPresetFunc withRange( double min, double max );
}
