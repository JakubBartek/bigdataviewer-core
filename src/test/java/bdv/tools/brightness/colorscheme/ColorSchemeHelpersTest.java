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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

import net.imglib2.type.numeric.ARGBType;

public class ColorSchemeHelpersTest {
    @Test
    public void interpolateValueHitsEndpointsAndInteriorPoints() {
        assertEquals(10, ColorSchemeHelpers.interpolateValue(10, 250, 0.0));
        assertEquals(70, ColorSchemeHelpers.interpolateValue(10, 250, 0.25));
        assertEquals(130, ColorSchemeHelpers.interpolateValue(10, 250, 0.5));
        assertEquals(190, ColorSchemeHelpers.interpolateValue(10, 250, 0.75));
        assertEquals(250, ColorSchemeHelpers.interpolateValue(10, 250, 1.0));
    }

    @Test
    public void interpolateValueDescends() {
        assertEquals(250, ColorSchemeHelpers.interpolateValue(250, 10, 0.0));
        assertEquals(190, ColorSchemeHelpers.interpolateValue(250, 10, 0.25));
        assertEquals(130, ColorSchemeHelpers.interpolateValue(250, 10, 0.5));
        assertEquals(70, ColorSchemeHelpers.interpolateValue(250, 10, 0.75));
        assertEquals(10, ColorSchemeHelpers.interpolateValue(250, 10, 1.0));
    }

    @Test
    public void interpolateValueRoundsHalfUp() {
        assertEquals(1, ColorSchemeHelpers.interpolateValue(0, 1, 0.5));
        assertEquals(1, ColorSchemeHelpers.interpolateValue(1, 0, 0.5));
    }

    @Test
    public void interpolateValueStaysBetweenEndpointsForEveryChannelPair() {
        final int steps = 64;
        for (int from = 0; from < 256; from++) {
            for (int to = 0; to < 256; to++) {
                assertEquals(from, ColorSchemeHelpers.interpolateValue(from, to, 0.0));
                assertEquals(to, ColorSchemeHelpers.interpolateValue(from, to, 1.0));
                for (int i = 1; i < steps; i++) {
                    final int v = ColorSchemeHelpers.interpolateValue(from, to, (double) i / steps);
                    assertTrue(from + "->" + to + " at " + i + "/" + steps + " gave " + v,
                            v >= Math.min(from, to) && v <= Math.max(from, to));
                }
            }
        }
    }

    @Test
    public void interpolateRGBAInterpolatesEachChannelIndependently() {
        final int from = ARGBType.rgba(0, 255, 10, 0);
        final int to = ARGBType.rgba(255, 0, 250, 128);
        assertEquals(from, ColorSchemeHelpers.interpolateRGBA(from, to, 0.0));
        assertEquals(ARGBType.rgba(64, 191, 70, 32), ColorSchemeHelpers.interpolateRGBA(from, to, 0.25));
        assertEquals(ARGBType.rgba(128, 128, 130, 64), ColorSchemeHelpers.interpolateRGBA(from, to, 0.5));
        assertEquals(ARGBType.rgba(191, 64, 190, 96), ColorSchemeHelpers.interpolateRGBA(from, to, 0.75));
        assertEquals(to, ColorSchemeHelpers.interpolateRGBA(from, to, 1.0));
    }

    @Test
    public void interpolateRGBIsOpaque() {
        final int from = ARGBType.rgba(0, 255, 10, 0);
        final int to = ARGBType.rgba(255, 0, 250, 128);
        assertEquals(ARGBType.rgba(0, 255, 10, 255), ColorSchemeHelpers.interpolateRGB(from, to, 0.0));
        assertEquals(ARGBType.rgba(64, 191, 70, 255), ColorSchemeHelpers.interpolateRGB(from, to, 0.25));
        assertEquals(ARGBType.rgba(128, 128, 130, 255), ColorSchemeHelpers.interpolateRGB(from, to, 0.5));
        assertEquals(ARGBType.rgba(191, 64, 190, 255), ColorSchemeHelpers.interpolateRGB(from, to, 0.75));
        assertEquals(ARGBType.rgba(255, 0, 250, 255), ColorSchemeHelpers.interpolateRGB(from, to, 1.0));
    }

    @Test
    public void interpolateRGBMatchesOpaqueRGBA() {
        final Random random = new Random(42);
        for (int i = 0; i < 10_000; i++) {
            final int from = random.nextInt();
            final int to = random.nextInt();
            final double t = random.nextDouble();
            assertEquals(ColorSchemeHelpers.opaque(ColorSchemeHelpers.interpolateRGBA(from, to, t)),
                    ColorSchemeHelpers.interpolateRGB(from, to, t));
        }
    }

    @Test
    public void opaqueKeepsRGBAndSetsFullAlpha() {
        assertEquals(0xff123456, ColorSchemeHelpers.opaque(0x00123456));
        assertEquals(0xffabcdef, ColorSchemeHelpers.opaque(0x80abcdef));
        assertEquals(0xffabcdef, ColorSchemeHelpers.opaque(0xffabcdef));
    }

    @Test
    public void unitToChannelScalesAndClamps() {
        assertEquals(0, ColorSchemeHelpers.unitToChannel(-0.5));
        assertEquals(0, ColorSchemeHelpers.unitToChannel(0.0));
        assertEquals(64, ColorSchemeHelpers.unitToChannel(0.25));
        assertEquals(128, ColorSchemeHelpers.unitToChannel(0.5));
        assertEquals(191, ColorSchemeHelpers.unitToChannel(0.75));
        assertEquals(255, ColorSchemeHelpers.unitToChannel(1.0));
        assertEquals(255, ColorSchemeHelpers.unitToChannel(1.5));
    }

    @Test
    public void unitToChannelRoundTripsEveryChannel() {
        for (int c = 0; c < 256; c++) {
            assertEquals(c, ColorSchemeHelpers.unitToChannel(c / 255.0));
        }
    }
}
