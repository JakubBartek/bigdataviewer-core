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
 * What a {@link DiscretePaletteWrapper} (one day also a continuous
 * equivalent) does when a raw value's palette value falls outside its color
 * scheme's domain, independently for the left ({@code paletteValue < 0}) and
 * right ({@code paletteValue >= scheme.getDelkaIntervalu()}) side.
 * <p>
 * Deliberately owned by the wrapper, not the color scheme it wraps: a color
 * scheme already has its own fixed edge behavior for an out-of-domain value
 * (see {@code IColorScheme}, which always clamps), but boundary handling is a
 * raw-value-mapping concern, kept out of the color scheme so it can stay
 * ignorant of anything upstream of a palette value.
 */
public enum BoundaryCondition
{
	/**
	 * Pass the palette value through unchanged. The color scheme's own
	 * {@code getRGB}/{@code getRGBA} already clamp an out-of-domain palette
	 * value to its nearest edge color, so this is that clamping, simply left
	 * to happen rather than pre-empted here.
	 */
	CLAMP,

	/**
	 * Wrap the palette value around to the opposite side of the domain (e.g.
	 * one step below {@code 0} resolves to the last valid palette value),
	 * instead of collapsing onto a single edge color -- for data that is
	 * itself cyclic (e.g. hue, or a phase angle).
	 */
	CYCLE,

	/**
	 * Use a fixed, user-supplied palette value instead of the one that was
	 * actually computed -- e.g. to always show a dedicated background color
	 * for out-of-range raw values, distinct from either edge of the palette.
	 * See {@link DiscretePaletteWrapper#getLeftSpecialValue()}/
	 * {@link DiscretePaletteWrapper#getRightSpecialValue()}.
	 */
	SPECIAL
}
