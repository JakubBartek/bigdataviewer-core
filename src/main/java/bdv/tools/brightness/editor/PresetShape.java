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
package bdv.tools.brightness.editor;

import bdv.tools.brightness.presetfunc.AlphaSigmoidPresetFunc;
import bdv.tools.brightness.presetfunc.AtanPresetFunc;
import bdv.tools.brightness.presetfunc.ExpPresetFunc;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.tools.brightness.presetfunc.LogPresetFunc;
import bdv.tools.brightness.presetfunc.IPresetFunc;
import bdv.tools.brightness.presetfunc.SigmoidPresetFunc;
import bdv.tools.brightness.presetfunc.TanPresetFunc;

/**
 * Predefined shapes for the mapping {@link Curve}, sampled from the
 * corresponding {@link IPresetFunc} into draggable control points. A curve
 * still holding a shape's sample renders as the shape itself, not its
 * piecewise-linear approximation (see {@link ColorSchemeWrapperFactory}).
 */
public enum PresetShape {
    LINEAR("Linear", LinearPresetFunc::new),
    LOG("Log", LogPresetFunc::new),
    EXP("Exp", ExpPresetFunc::new),
    SIGMOID("Sigmoid", SigmoidPresetFunc::new),
    ALPHA_SIGMOID("α-Sigmoid", AlphaSigmoidPresetFunc::new),
    TAN("Tan", TanPresetFunc::new),
    ATAN("Atan", AtanPresetFunc::new);

    /**
     * Control points per non-linear preset; {@link #LINEAR} uses two.
     */
    private static final int NUM_POINTS = 9;

    /**
     * Far below one pixel of a drag, far above the rounding of inverting twice.
     */
    private static final double KNOT_TOLERANCE = 1e-12;

    private final String label;

    private final IPresetFuncFactory factory;

    PresetShape(final String label, final IPresetFuncFactory factory) {
        this.label = label;
        this.factory = factory;
    }

    @Override
    public String toString() {
        return label;
    }

    public IPresetFunc create(final double min, final double max, final int schemeRange) {
        return factory.create(min, max, schemeRange);
    }

    /**
     * The shape whose control points these are, or {@code null} once they have
     * been edited.
     */
    public static PresetShape matching(final double[] xs, final double[] ys) {
        for (final PresetShape shape : values()) {
            if (closeTo(shape.xs(), xs) && closeTo(shape.ys(), ys)) {
                return shape;
            }
        }
        return null;
    }

    private static boolean closeTo(final double[] expected, final double[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (!(Math.abs(expected[i] - actual[i]) <= KNOT_TOLERANCE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Control point x positions in [0, 1].
     */
    public double[] xs() {
        if (this == LINEAR) {
            return new double[]{0.0, 1.0};
        }
        final double[] xs = new double[NUM_POINTS];
        for (int i = 0; i < NUM_POINTS; i++) {
            xs[i] = i / (double) (NUM_POINTS - 1);
        }
        return xs;
    }

    /**
     * Control point values in [0, 1], matching {@link #xs()}.
     */
    public double[] ys() {
        if (this == LINEAR) {
            return new double[]{0.0, 1.0};
        }
        // over [0, 1] with a scheme range of 1, raw and scheme values are the fractions themselves
        final IPresetFunc shape = create(0.0, 1.0, 1);
        final double[] xs = xs();
        final double[] ys = new double[xs.length];
        for (int i = 0; i < xs.length; i++) {
            ys[i] = Math.max(0.0, Math.min(1.0, shape.getSchemeValueForRaw(xs[i])));
        }
        return ys;
    }

    @FunctionalInterface
    private interface IPresetFuncFactory {
        IPresetFunc create(double min, double max, int schemeRange);
    }
}
