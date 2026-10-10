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

package bdv.tools.brightness.converter;

import java.util.Objects;

import net.imglib2.converter.Converter;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * Renders a real-valued source through a {@link IColorSchemeWrapper}. The display
 * range ({@linkplain #getMin() min}/{@linkplain #getMax() max}) is forwarded to
 * {@link IColorSchemeWrapper#setRawDomain(double, double)} on every change.
 *
 * @param <R> source pixel type.
 */
public class ColorSchemeConverter<R extends RealType<R>> extends AbstractLinearRange
        implements Converter<R, ARGBType>, ColorConverter {
    private IColorSchemeWrapper wrapper;

    /**
     * @param wrapper the wrapper each sample is mapped through.
     * @param min     display-range minimum (maps to the start of the scheme).
     * @param max     display-range maximum (maps to the end of the scheme).
     */
    public ColorSchemeConverter(final IColorSchemeWrapper wrapper, final double min, final double max) {
        super(min, max);
        this.wrapper = Objects.requireNonNull(wrapper, "wrapper");
        wrapper.setRawDomain(min, max);
    }

    public IColorSchemeWrapper getWrapper() {
        return wrapper;
    }

    /**
     * Swap in a different wrapper, re-applying the current display range to it.
     */
    public void setWrapper(final IColorSchemeWrapper wrapper) {
        this.wrapper = Objects.requireNonNull(wrapper, "wrapper");
        syncDomain();
    }

    @Override
    public void convert(final R input, final ARGBType output) {
        // RGBA keeps a transparent SPECIAL boundary color
        output.set(wrapper.getRGBAForRaw(input.getRealDouble()));
    }

    @Override
    public void setMin(final double min) {
        super.setMin(min);
        syncDomain();
    }

    @Override
    public void setMax(final double max) {
        super.setMax(max);
        syncDomain();
    }

    /**
     * Skipped while the range is momentarily inverted between {@code setMin}/{@code setMax}.
     */
    private void syncDomain() {
        if (wrapper != null && max > min) {
            wrapper.setRawDomain(min, max);
        }
    }

    @Override
    public ARGBType getColor() {
        return new ARGBType(ARGBType.rgba(255, 255, 255, 255));
    }

    @Override
    public void setColor(final ARGBType c) {
        // colors come from the wrapper
    }

    @Override
    public boolean supportsColor() {
        return false;
    }
}
