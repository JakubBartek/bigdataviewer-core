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
 * Converts a raw image value into a palette value -- nothing else. Owns the
 * raw-value range it is defined over ({@link #getMin()}/{@link #getMax()})
 * and the length of the palette-value range it maps into
 * ({@link #getDelkaIntervalu()}, same meaning as
 * {@code IColorScheme#getDelkaIntervalu()}: a {@code paletteValue} this
 * produces is meant to land in {@code [0, getDelkaIntervalu()]}, ready to
 * feed a continuous color scheme with that same domain length).
 * <p>
 * Implementations must not know anything about RGB, RGBA, color schemes,
 * palettes, or boundary conditions -- turning a palette value into a color is
 * a separate concern ({@code IColorScheme}), and deciding what to do with a
 * raw value outside {@code [getMin(), getMax()]} is another
 * ({@code ContinuousPaletteWrapper}, not yet implemented). This interface
 * only ever computes {@code paletteValue = f(rawValue)}; {@code getMin()}/
 * {@code getMax()} are exposed so a caller can make that boundary decision
 * without this class needing to know it is being made.
 */
public interface PresetFunc
{
	/**
	 * Raw value this function's domain starts at. Maps to palette value
	 * {@code 0} for every implementation here except
	 * {@link CustomInterpPresetFunc}, whose shape is user-defined.
	 */
	float getMin();

	/**
	 * Raw value this function's domain ends at. Maps to palette value
	 * {@link #getDelkaIntervalu()} for every implementation here except
	 * {@link CustomInterpPresetFunc}, whose shape is user-defined.
	 */
	float getMax();

	/** Length of the palette-value range {@link #getPaletteValueForRaw(float)} maps into; see the class javadoc. */
	float getDelkaIntervalu();

	/**
	 * {@code paletteValue = f(rawValue)}. {@code rawValue} is not assumed to
	 * already be inside {@code [getMin(), getMax()]} -- implementations clamp
	 * it themselves rather than producing an undefined or wildly
	 * extrapolated result, but a caller that cares whether {@code rawValue}
	 * was actually in range should check {@link #getMin()}/{@link #getMax()}
	 * itself beforehand (see the class javadoc).
	 */
	float getPaletteValueForRaw( float rawValue );
}
