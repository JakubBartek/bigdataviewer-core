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

import bdv.tools.brightness.colorscheme.ColorSchemeHelpers;
import bdv.tools.brightness.colorscheme.IColorScheme;
import bdv.tools.brightness.presetfunc.IPresetFunc;

/**
 * Maps a raw image value to a color: {@code rawValue -> boundary handling ->
 * IPresetFunc -> schemeValue -> IColorScheme -> RGB/RGBA}. The preset function
 * handles in-domain values, the color scheme turns scheme values into colors
 * (interpolating or flooring, depending on whether it is a
 * {@link bdv.tools.brightness.colorscheme.ContinuousColorScheme} or
 * {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme}), and this
 * class owns only what happens outside the domain.
 */
public class PresetColorSchemeWrapper implements IColorSchemeWrapper {
    /**
     * Default {@link BoundaryCondition#SPECIAL} color: fully transparent.
     */
    public static final int DEFAULT_SPECIAL_COLOR = 0x00000000;

    /**
     * Raw-value ULPs within which {@link #cycled(double)} treats a value as on a
     * period boundary. The largest error measured is an eighth of a ULP.
     */
    private static final int WRAP_SNAP_ULPS = 4;

    private IColorScheme colorScheme;

    private IPresetFunc presetFunc;

    private BoundaryCondition leftBoundaryCondition;

    private BoundaryCondition rightBoundaryCondition;

    private int leftSpecialColor = DEFAULT_SPECIAL_COLOR;

    private int rightSpecialColor = DEFAULT_SPECIAL_COLOR;

    /**
     * @param leftBoundaryCondition  applied when {@code rawValue < presetFunc.getMin()}.
     * @param rightBoundaryCondition applied when {@code rawValue > presetFunc.getMax()}.
     * @throws IllegalArgumentException if the scheme ranges of
     *                                  {@code colorScheme} and {@code presetFunc} differ.
     */
    public PresetColorSchemeWrapper(final IColorScheme colorScheme, final IPresetFunc presetFunc,
                                    final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition) {
        this.colorScheme = Objects.requireNonNull(colorScheme, "colorScheme");
        this.presetFunc = Objects.requireNonNull(presetFunc, "presetFunc");
        this.leftBoundaryCondition = Objects.requireNonNull(leftBoundaryCondition, "leftBoundaryCondition");
        this.rightBoundaryCondition = Objects.requireNonNull(rightBoundaryCondition, "rightBoundaryCondition");
        requireMatchingSchemeRange(colorScheme, presetFunc);
    }

    /**
     * Same as the full constructor, with both boundary conditions set to {@link BoundaryCondition#CLAMP}.
     */
    public PresetColorSchemeWrapper(final IColorScheme colorScheme, final IPresetFunc presetFunc) {
        this(colorScheme, presetFunc, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP);
    }

    @Override
    public IColorScheme getColorScheme() {
        return colorScheme;
    }

    /**
     * @throws IllegalArgumentException if the new color scheme's {@code getRange()} no longer matches {@link #getPresetFunc()}'s {@code getSchemeRange()}.
     */
    public void setColorScheme(final IColorScheme colorScheme) {
        Objects.requireNonNull(colorScheme, "colorScheme");
        requireMatchingSchemeRange(colorScheme, presetFunc);
        this.colorScheme = colorScheme;
    }

    public IPresetFunc getPresetFunc() {
        return presetFunc;
    }

    /**
     * @throws IllegalArgumentException if the new preset function's {@code getSchemeRange()} no longer matches {@link #getColorScheme()}'s {@code getRange()}.
     */
    public void setPresetFunc(final IPresetFunc presetFunc) {
        Objects.requireNonNull(presetFunc, "presetFunc");
        requireMatchingSchemeRange(colorScheme, presetFunc);
        this.presetFunc = presetFunc;
    }

    /**
     * Re-ranges the {@link #getPresetFunc()} over {@code [min, max]}, keeping its shape and scheme range (see {@link IPresetFunc#withRange(double, double)}).
     */
    @Override
    public void setRawDomain(final double min, final double max) {
        if (!(max > min)) {
            throw new IllegalArgumentException("max must be strictly greater than min, got min=" + min + ", max=" + max);
        }
        this.presetFunc = presetFunc.withRange(min, max);
    }

    // -- boundary conditions -------------------------------------------------

    public BoundaryCondition getLeftBoundaryCondition() {
        return leftBoundaryCondition;
    }

    public void setLeftBoundaryCondition(final BoundaryCondition leftBoundaryCondition) {
        this.leftBoundaryCondition = Objects.requireNonNull(leftBoundaryCondition, "leftBoundaryCondition");
    }

    public BoundaryCondition getRightBoundaryCondition() {
        return rightBoundaryCondition;
    }

    public void setRightBoundaryCondition(final BoundaryCondition rightBoundaryCondition) {
        this.rightBoundaryCondition = Objects.requireNonNull(rightBoundaryCondition, "rightBoundaryCondition");
    }

    /**
     * Packed-ARGB color for values left of the domain when the left condition is {@link BoundaryCondition#SPECIAL}.
     */
    public int getLeftSpecialColor() {
        return leftSpecialColor;
    }

    public void setLeftSpecialColor(final int leftSpecialColor) {
        this.leftSpecialColor = leftSpecialColor;
    }

    /**
     * As {@link #getLeftSpecialColor()}, for a raw value that hits the right boundary.
     */
    public int getRightSpecialColor() {
        return rightSpecialColor;
    }

    public void setRightSpecialColor(final int rightSpecialColor) {
        this.rightSpecialColor = rightSpecialColor;
    }

    // -- mapping -------------------------------------------------------------

    /**
     * {@link BoundaryCondition#SPECIAL} has no scheme value, so here it behaves
     * like {@link BoundaryCondition#CLAMP}; the special color only shows up in
     * {@link #getRGBForRaw(double)}/{@link #getRGBAForRaw(double)}.
     */
    @Override
    public double getSchemeValueForRaw(final double rawValue) {
        final BoundaryCondition hit = boundaryHit(rawValue);
        if (hit == BoundaryCondition.CYCLE) {
            return presetFunc.getSchemeValueForRaw(cycled(rawValue));
        }
        // the preset function clamps on its own
        return presetFunc.getSchemeValueForRaw(rawValue);
    }

    /**
     * A SPECIAL boundary color is forced opaque here.
     */
    @Override
    public int getRGBForRaw(final double rawValue) {
        final Integer special = specialColorForRaw(rawValue);
        if (special != null) {
            return ColorSchemeHelpers.opaque(special);
        }
        return colorScheme.getRGB(getSchemeValueForRaw(rawValue));
    }

    @Override
    public int getRGBAForRaw(final double rawValue) {
        final Integer special = specialColorForRaw(rawValue);
        if (special != null) {
            return special;
        }
        return colorScheme.getRGBA(getSchemeValueForRaw(rawValue));
    }

    /**
     * Which boundary {@code rawValue} hits and with what condition, or {@code null} if it is inside the domain.
     */
    private BoundaryCondition boundaryHit(final double rawValue) {
        if (rawValue < presetFunc.getMin()) {
            return leftBoundaryCondition;
        }
        if (isAboveDomain(rawValue)) {
            return rightBoundaryCondition;
        }
        return null;
    }

    /**
     * Under {@link BoundaryCondition#CYCLE} the domain is half-open
     * {@code [min, max)}, since {@code max} is the same point as {@code min}
     * (like 360 and 0 degrees). Otherwise it is closed and {@code max} resolves
     * to the last fix.
     */
    private boolean isAboveDomain(final double rawValue) {
        return rightBoundaryCondition == BoundaryCondition.CYCLE
                ? rawValue >= presetFunc.getMax()
                : rawValue > presetFunc.getMax();
    }

    /**
     * The SPECIAL color for {@code rawValue} if it hit a SPECIAL boundary, else {@code null} (so the scheme-value path is used instead).
     */
    private Integer specialColorForRaw(final double rawValue) {
        if (rawValue < presetFunc.getMin() && leftBoundaryCondition == BoundaryCondition.SPECIAL) {
            return leftSpecialColor;
        }
        if (isAboveDomain(rawValue) && rightBoundaryCondition == BoundaryCondition.SPECIAL) {
            return rightSpecialColor;
        }
        return null;
    }

    /**
     * {@code rawValue} wrapped into {@code [presetFunc.getMin(), presetFunc.getMax())}.
     */
    private double cycled(final double rawValue) {
        final double domainMin = presetFunc.getMin();
        final double period = presetFunc.getMax() - domainMin;
        double offset = (rawValue - domainMin) % period;
        if (offset < 0) {
            offset += period;
        }
        // Within a hair of a whole period, resolve to the start, not the end:
        // the two are opposite ends of the scheme, and the user means decimal
        // periods (6.6 % 0.6 is 0.5999999999999999 in binary). Tolerance is in
        // raw ULPs because subtracting domainMin cancels the low bits.
        final double tolerance = WRAP_SNAP_ULPS * Math.ulp(Math.max(Math.abs(rawValue), Math.abs(domainMin)));
        if (offset <= tolerance || period - offset <= tolerance) {
            return domainMin;
        }
        return domainMin + offset;
    }

    private static void requireMatchingSchemeRange(final IColorScheme colorScheme, final IPresetFunc presetFunc) {
        if (colorScheme.getRange() != presetFunc.getSchemeRange()) {
            throw new IllegalArgumentException("colorScheme.getRange() (" + colorScheme.getRange() +
                    ") must match presetFunc.getSchemeRange() (" + presetFunc.getSchemeRange() + ")");
        }
    }
}
