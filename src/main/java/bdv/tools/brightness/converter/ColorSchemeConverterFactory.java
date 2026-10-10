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
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness.converter;

import java.util.ArrayList;
import java.util.List;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.LegacyBdvColorScheme;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * Replaces a single-color converter ({@code RealARGBColorConverter} and
 * friends) with a {@link ColorSchemeConverter} that looks the same, so that
 * {@link bdv.tools.brightness.editor.LutEditorDialog} can edit it: same display range, a
 * {@link LinearPresetFunc linear} shape and a {@link LegacyBdvColorScheme}.
 * See that scheme for where the two diverge. A collapsed display range is
 * widened by one raw unit.
 */
public final class ColorSchemeConverterFactory {
    private ColorSchemeConverterFactory() {
    }

    /**
     * Requires a single-color converter over real-typed samples, for the
     * volatile counterpart too, so the source never renders with two different
     * schemes while loading.
     */
    public static boolean canApproximate(final SourceAndConverter<?> soc) {
        if (soc == null) {
            return false;
        }
        final Converter<?, ARGBType> converter = soc.getConverter();
        if (!(converter instanceof ColorConverter) || converter instanceof ColorSchemeConverter) {
            return false;
        }
        if (!isRealTyped(soc)) {
            return false;
        }
        final SourceAndConverter<?> volatileSoc = soc.asVolatile();
        return volatileSoc == null || isRealTyped(volatileSoc);
    }

    /**
     * Swap the converters of {@code soc} and its volatile counterpart, sharing
     * one {@link PresetColorSchemeWrapper}, and return the new one; {@code null} if
     * {@link #canApproximate} fails.
     * <p>
     * The caller must re-point the {@code ConverterSetup} (see
     * {@link bdv.tools.brightness.RealARGBColorConverterSetup#setConverters} and
     * {@link #colorConvertersOf}).
     */
    public static ColorSchemeConverter<?> approximateInPlace(final SourceAndConverter<?> soc) {
        if (!canApproximate(soc)) {
            return null;
        }

        final ColorConverter legacy = (ColorConverter) soc.getConverter();
        final double min = legacy.getMin();
        final double max = legacy.getMax();

        final ContinuousColorScheme scheme = schemeFor(legacy);
        // the legacy converter tolerates a collapsed range, the wrapper does not
        final double hi = max > min ? max : min + 1;
        final PresetColorSchemeWrapper wrapper = new PresetColorSchemeWrapper(scheme,
                new LinearPresetFunc(min, hi, scheme.getRange()));

        final ColorSchemeConverter<?> converted = install(soc, wrapper, min, hi);
        if (soc.asVolatile() != null) {
            install(soc.asVolatile(), wrapper, min, hi);
        }
        return converted;
    }

    /**
     * The {@link ColorConverter}s of {@code soc} and its volatile counterpart, i.e. what its {@code ConverterSetup} drives.
     */
    public static List<ColorConverter> colorConvertersOf(final SourceAndConverter<?> soc) {
        final List<ColorConverter> converters = new ArrayList<>();
        if (soc != null) {
            addIfColorConverter(converters, soc);
            addIfColorConverter(converters, soc.asVolatile());
        }
        return converters;
    }

    /**
     * Falls back to white, the old converter's default, when there is no color to read.
     */
    public static LegacyBdvColorScheme schemeFor(final ColorConverter legacy) {
        final ARGBType color = legacy.supportsColor() ? legacy.getColor() : null;
        return new LegacyBdvColorScheme(color != null ? color.get() : DEFAULT_LEGACY_COLOR);
    }

    /**
     * The color a {@code RealARGBColorConverter} starts out with.
     */
    private static final int DEFAULT_LEGACY_COLOR = ARGBType.rgba(255, 255, 255, 255);

    private static boolean isRealTyped(final SourceAndConverter<?> soc) {
        final Source<?> source = soc.getSpimSource();
        return source != null && source.getType() instanceof RealType;
    }

    private static void addIfColorConverter(final List<ColorConverter> converters, final SourceAndConverter<?> soc) {
        if (soc == null) {
            return;
        }
        final Converter<?, ARGBType> converter = soc.getConverter();
        if (converter instanceof ColorConverter) {
            converters.add((ColorConverter) converter);
        }
    }

    /**
     * Raw types: the {@code RealType} is only checked at runtime ({@link #isRealTyped}).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ColorSchemeConverter<?> install(final SourceAndConverter<?> soc, final PresetColorSchemeWrapper wrapper, final double min, final double max) {
        final ColorSchemeConverter converter = new ColorSchemeConverter<>(wrapper, min, max);
        ((SourceAndConverter) soc).setConverter(converter);
        return converter;
    }
}
