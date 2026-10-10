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
package bdv.tools.brightness.editor;

import bdv.tools.brightness.converter.BoundaryCondition;

/**
 * A named snapshot of the LUT editor's mapping settings, excluding the display
 * range, which belongs to the source.
 * <p>
 * Gson-serialized by {@link EditorPresetHelper}; field names are the JSON keys.
 * The legacy keys {@code cyclic}/{@code treatMinAsBackground}/{@code backgroundColor}
 * are read but never written. Fields are boxed so Gson can tell "absent" from
 * "false"/"0".
 */
public class EditorPreset {
    private String name;

    private String colorSchemeName;

    /**
     * {@link BoundaryCondition#name()}; {@code null} in a legacy file.
     */
    private String leftBoundaryCondition;

    /**
     * {@link BoundaryCondition#name()}; {@code null} in a legacy file.
     */
    private String rightBoundaryCondition;

    private Integer leftSpecialColor;

    private Integer rightSpecialColor;

    /**
     * Raw values per color fix for a discrete scheme; {@code null} means {@link LutEditorMapping#AUTO_STEP_SIZE}.
     */
    private Double stepSize;

    private double[] curveXs;

    private int[] curveYs;

    // -- legacy keys, read but never written ---------------------------------

    private Boolean cyclic;

    private Boolean treatMinAsBackground;

    private Integer backgroundColor;

    /**
     * For Gson.
     */
    EditorPreset() {
    }

    public EditorPreset(final String name, final String colorSchemeName,
                        final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition,
                        final int leftSpecialColor, final int rightSpecialColor, final double stepSize,
                        final double[] curveXs, final int[] curveYs) {
        this.name = name;
        this.colorSchemeName = colorSchemeName;
        this.leftBoundaryCondition = leftBoundaryCondition.name();
        this.rightBoundaryCondition = rightBoundaryCondition.name();
        this.leftSpecialColor = leftSpecialColor;
        this.rightSpecialColor = rightSpecialColor;
        this.stepSize = stepSize;
        this.curveXs = curveXs;
        this.curveYs = curveYs;
    }

    public String getName() {
        return name;
    }

    public String getColorSchemeName() {
        return colorSchemeName;
    }

    /**
     * Legacy fallback: treat-min-as-background is {@link BoundaryCondition#SPECIAL}, else cyclic is {@link BoundaryCondition#CYCLE}.
     */
    public BoundaryCondition getLeftBoundaryCondition() {
        if (leftBoundaryCondition != null) {
            return parse(leftBoundaryCondition);
        }
        if (Boolean.TRUE.equals(treatMinAsBackground)) {
            return BoundaryCondition.SPECIAL;
        }
        return legacyRangeMode();
    }

    /**
     * Legacy fallback: cyclic is {@link BoundaryCondition#CYCLE}.
     */
    public BoundaryCondition getRightBoundaryCondition() {
        if (rightBoundaryCondition != null) {
            return parse(rightBoundaryCondition);
        }
        return legacyRangeMode();
    }

    /**
     * Legacy fallback: {@code backgroundColor}.
     */
    public int getLeftSpecialColor() {
        if (leftSpecialColor != null) {
            return leftSpecialColor;
        }
        if (backgroundColor != null) {
            return backgroundColor;
        }
        return LutEditorMapping.DEFAULT_LEFT_SPECIAL_COLOR;
    }

    public int getRightSpecialColor() {
        return rightSpecialColor != null ? rightSpecialColor : LutEditorMapping.DEFAULT_RIGHT_SPECIAL_COLOR;
    }

    public double getStepSize() {
        return stepSize != null ? stepSize : LutEditorMapping.AUTO_STEP_SIZE;
    }

    public double[] getCurveXs() {
        return curveXs;
    }

    public int[] getCurveYs() {
        return curveYs;
    }

    private BoundaryCondition legacyRangeMode() {
        return Boolean.TRUE.equals(cyclic) ? BoundaryCondition.CYCLE : BoundaryCondition.CLAMP;
    }

    /**
     * Unknown names fall back to {@link BoundaryCondition#CLAMP}; preset files are user-editable.
     */
    private static BoundaryCondition parse(final String name) {
        try {
            return BoundaryCondition.valueOf(name);
        } catch (final IllegalArgumentException e) {
            return BoundaryCondition.CLAMP;
        }
    }
}
