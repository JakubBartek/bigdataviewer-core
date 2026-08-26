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
package bdv.tools.brightness.colorscheme;

/**
 * Converts a palette value into a color -- nothing else. A "palette value" is
 * a position already expressed in this scheme's own domain (see
 * {@link #getPaletteRangeLength()}), e.g. "the 2.5th color stop"; it is not a raw
 * image value.
 * <p>
 * Implementations must not know about raw image values, a raw value's
 * min/max, step size, boundary conditions, or any value-transformation
 * function -- turning a raw pixel value into a palette value is a separate
 * concern ({@code bdv.tools.brightness.palette}'s palette wrappers, and the
 * {@code bdv.tools.brightness.presetfunc} transforms they use), kept out of this
 * interface so a color scheme can be built, tested and swapped independently
 * of how it ends up being fed.
 *
 * @see DiscreteColorScheme
 * @see ContinuousColorScheme
 */
public interface ColorScheme
{
	/**
	 * The color at {@code paletteValue}, packed as ARGB (see
	 * {@link net.imglib2.type.numeric.ARGBType#rgba(int, int, int, int)}) with
	 * alpha forced fully opaque ({@code 0xff}) regardless of the underlying
	 * color stop's own alpha -- see {@link #getRGBA(float)} to read that alpha
	 * instead.
	 * <p>
	 * {@code paletteValue} outside this scheme's domain (see
	 * {@link #getPaletteRangeLength()}) is not an error: the nearest edge stop's
	 * color is returned instead of throwing.
	 */
	int getRGB( float paletteValue );

	/**
	 * Like {@link #getRGB(float)}, but carrying the color stop's own alpha
	 * component instead of forcing full opacity.
	 */
	int getRGBA( float paletteValue );

	/**
	 * The length of this scheme's valid palette-value domain, which always
	 * starts at {@code 0}. How the far end is included differs by
	 * implementation -- see {@link DiscreteColorScheme#getPaletteRangeLength()}
	 * and {@link ContinuousColorScheme#getPaletteRangeLength()}.
	 */
	int getPaletteRangeLength();
}
