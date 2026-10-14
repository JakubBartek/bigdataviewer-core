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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.Test;

public class MappingCurvePanelTest {

    private static final double[] ORIGINS = {0, 0.1, -7.3, 1000, 12345.678, 1e6};

    @Test
    public void quarterTickLabelsForTypicalRanges() {
        assertLabels(0, 255, "0", "64", "128", "191", "255");
        assertLabels(0, 65535, "0", "16384", "32768", "49151", "65535");
        assertLabels(0, 1, "0.00", "0.25", "0.50", "0.75", "1.00");
        assertLabels(0, 10, "0.0", "2.5", "5.0", "7.5", "10.0");
        assertLabels(0, 4, "0", "1", "2", "3", "4");
        assertLabels(0.1, 4.1, "0.1", "1.1", "2.1", "3.1", "4.1");
        assertLabels(1.1, 1.2, "1.100", "1.125", "1.150", "1.175", "1.200");
        assertLabels(0, 0.01, "0.0000", "0.0025", "0.0050", "0.0075", "0.0100");
        assertLabels(0, 1000.3, "0", "250", "500", "750", "1000");
    }

    @Test
    public void quarterTickLabelsForColorIndicesOfFewFixes() {
        assertLabels(0, 1, "0.00", "0.25", "0.50", "0.75", "1.00");
        assertLabels(0, 2, "0.0", "0.5", "1.0", "1.5", "2.0");
        assertLabels(0, 5, "0.0", "1.3", "2.5", "3.8", "5.0");
        assertLabels(0, 8, "0", "2", "4", "6", "8");
    }

    @Test
    public void quarterTickLabelsNeverShowNegativeZero() {
        assertLabels(-1, 1, "-1.0", "-0.5", "0.0", "0.5", "1.0");
        assertLabels(-0.3, 0.3, "-0.30", "-0.15", "0.00", "0.15", "0.30");
    }

    @Test
    public void axisValueLabelForTypicalValues() {
        assertEquals("64", MappingCurvePanel.axisValueLabel(63.75, 0, 255));
        assertEquals("1235", MappingCurvePanel.axisValueLabel(1234.56, 0, 65535));
        assertEquals("0.37", MappingCurvePanel.axisValueLabel(0.3721, 0, 1));
        assertEquals("3.1", MappingCurvePanel.axisValueLabel(3.14159, 0, 10));
        assertEquals("1.134", MappingCurvePanel.axisValueLabel(1.1337, 1.1, 1.2));
        assertEquals("0.0012", MappingCurvePanel.axisValueLabel(0.00123, 0, 0.01));
        assertEquals("0.00", MappingCurvePanel.axisValueLabel(-0.004, -1, 1));
    }

    @Test
    public void axisValueLabelOutdoesWholeTicksWhenValuesBetweenThemWouldRoundTooFar() {
        assertEquals("1.5", MappingCurvePanel.axisValueLabel(1.5, 0, 4));
        assertEquals("2.0", MappingCurvePanel.axisValueLabel(2, 0, 4));
        assertEquals("2.3", MappingCurvePanel.axisValueLabel(2.345, 0.1, 4.1));
    }

    /**
     * Within the tick tolerance anywhere on the axis, never with fewer decimals than the ticks.
     */
    @Test
    public void axisValueLabelIsAccurateForEveryRange() {
        for (final double origin : ORIGINS) {
            for (int exponent = -6; exponent <= 6; exponent++) {
                for (int mantissa = 10; mantissa < 100; mantissa++) {
                    final double min = origin;
                    final double max = origin + mantissa * Math.pow(10, exponent - 1);
                    final String range = "[" + min + ", " + max + "]";
                    final double tolerance = MappingCurvePanel.TICK_LABEL_TOLERANCE * (max - min) / 4;
                    final int tickDecimals = new BigDecimal(MappingCurvePanel.quarterTickLabels(min, max)[0]).scale();
                    for (int step = 0; step <= 23; step++) {
                        final double value = min + step / 23.0 * (max - min);
                        final BigDecimal label = new BigDecimal(MappingCurvePanel.axisValueLabel(value, min, max));
                        assertEquals(range + " value " + value, value, label.doubleValue(), tolerance * (1 + 1e-6));
                        assertTrue(range, label.scale() >= tickDecimals);
                    }
                }
            }
        }
    }

    /**
     * Every label is close to its tick, all share the fewest decimals that achieve it, and none coincide.
     */
    @Test
    public void quarterTickLabelsAreAccurateAndMinimalForEveryRange() {
        for (final double origin : ORIGINS) {
            for (int exponent = -6; exponent <= 6; exponent++) {
                for (int mantissa = 10; mantissa < 100; mantissa++) {
                    final double length = mantissa * Math.pow(10, exponent - 1);
                    checkQuarterTickLabels(origin, origin + length);
                }
            }
        }
    }

    private static void checkQuarterTickLabels(final double min, final double max) {
        final String range = "[" + min + ", " + max + "]";
        final String[] labels = MappingCurvePanel.quarterTickLabels(min, max);
        final double tolerance = MappingCurvePanel.TICK_LABEL_TOLERANCE * (max - min) / 4;

        final int decimals = new BigDecimal(labels[0]).scale();
        boolean fewerWouldDo = decimals > 0;
        for (int i = 0; i <= 4; i++) {
            final double tick = min + i / 4.0 * (max - min);
            final BigDecimal label = new BigDecimal(labels[i]);
            assertEquals(range, decimals, label.scale());
            assertEquals(range + " tick " + i, tick, label.doubleValue(), tolerance);
            if (decimals > 0) {
                final double coarser = BigDecimal.valueOf(tick).setScale(decimals - 1, RoundingMode.HALF_UP).doubleValue();
                fewerWouldDo &= Math.abs(coarser - tick) <= tolerance;
            }
        }
        assertFalse(range + " could use fewer decimals", fewerWouldDo);
        for (int i = 0; i < 4; i++) {
            assertNotEquals(range, labels[i], labels[i + 1]);
        }
    }

    private static void assertLabels(final double min, final double max, final String... expected) {
        assertArrayEquals(expected, MappingCurvePanel.quarterTickLabels(min, max));
    }
}
