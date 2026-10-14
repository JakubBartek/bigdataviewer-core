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

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;

/**
 * Editing only; evaluating the points is {@link CustomInterpPresetFunc}'s job.
 */
public class CurveTest {
    @Test
    public void testDefaultIsTheIdentityRamp() {
        final Curve curve = new Curve();
        Assert.assertArrayEquals(new double[]{0.0, 1.0}, curve.xsArray(), 0.0);
        Assert.assertArrayEquals(new double[]{0.0, 1.0}, curve.ysArray(), 0.0);
    }

    @Test
    public void testAddPointKeepsXsSorted() {
        final Curve curve = new Curve();
        curve.addPoint(0.75, 0.5);
        curve.addPoint(0.25, 0.125);

        Assert.assertArrayEquals(new double[]{0.0, 0.25, 0.75, 1.0}, curve.xsArray(), 0.0);
        Assert.assertArrayEquals(new double[]{0.0, 0.125, 0.5, 1.0}, curve.ysArray(), 0.0);
    }

    @Test
    public void testAddPointAtAnExistingXUpdatesIt() {
        final Curve curve = new Curve();
        curve.addPoint(0.5, 0.25);
        curve.addPoint(0.5, 0.75);

        Assert.assertEquals(3, curve.getPointCount());
        Assert.assertEquals(0.75, curve.getY(1), 0.0);
    }

    @Test
    public void testAddPointAndSetPointClampToTheUnitSquare() {
        final Curve curve = new Curve();
        curve.addPoint(0.5, 1.5);
        Assert.assertEquals(1.0, curve.getY(1), 0.0);

        curve.setPoint(1, -0.5);
        Assert.assertEquals(0.0, curve.getY(1), 0.0);

        // clamped onto the last point, so it updates rather than adds
        curve.addPoint(1.5, 0.25);
        Assert.assertEquals(3, curve.getPointCount());
        Assert.assertEquals(0.25, curve.getY(2), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddPointRejectsNaN() {
        new Curve().addPoint(0.5, Double.NaN);
    }

    @Test
    public void testRemovePointKeepsTheEndpoints() {
        final Curve curve = new Curve();
        curve.addPoint(0.25, 0.5);
        curve.addPoint(0.75, 0.5);

        curve.removePoint(1);
        Assert.assertArrayEquals(new double[]{0.0, 0.75, 1.0}, curve.xsArray(), 0.0);

        curve.removePoint(0);
        curve.removePoint(2);
        Assert.assertEquals(3, curve.getPointCount());
    }

    @Test
    public void testFindNearestPoint() {
        final Curve curve = new Curve();
        curve.addPoint(0.5, 0.25);

        Assert.assertEquals(1, curve.findNearestPoint(0.52, 0.27, 0.05));
        Assert.assertEquals(-1, curve.findNearestPoint(0.52, 0.27, 0.01));
    }

    @Test
    public void testInvert() {
        final Curve curve = new Curve();
        curve.addPoint(0.5, 0.25);

        curve.invert();

        Assert.assertArrayEquals(new double[]{0.0, 0.5, 1.0}, curve.xsArray(), 0.0);
        Assert.assertArrayEquals(new double[]{1.0, 0.75, 0.0}, curve.ysArray(), 0.0);
    }

    @Test
    public void testSetPointsCopies() {
        final double[] xs = {0.0, 0.5, 1.0};
        final double[] ys = {0.25, 1.0, 0.0};
        final Curve curve = new Curve();
        curve.setPoints(xs, ys);
        xs[1] = 0.75;
        ys[1] = 0.5;

        Assert.assertArrayEquals(new double[]{0.0, 0.5, 1.0}, curve.xsArray(), 0.0);
        Assert.assertArrayEquals(new double[]{0.25, 1.0, 0.0}, curve.ysArray(), 0.0);
    }

    @Test
    public void testSetPointsRejectsInvalidKnotsAndKeepsTheCurve() {
        final double[][][] invalid = {
                {null, {0.0, 1.0}},
                {{0.0, 1.0}, null},
                {{0.0, 1.0}, {0.0, 1.5}},
                {{0.0, 0.5, 0.5, 1.0}, {0.0, 0.5, 0.5, 1.0}},
                {{0.0, 1.0}, {0.0, 0.5, 1.0}},
                {{0.5}, {0.5}},
        };
        for (final double[][] points : invalid) {
            final Curve curve = new Curve();
            try {
                curve.setPoints(points[0], points[1]);
                Assert.fail("expected IllegalArgumentException");
            } catch (final IllegalArgumentException expected) {
                Assert.assertArrayEquals(new double[]{0.0, 1.0}, curve.ysArray(), 0.0);
            }
        }
    }

    /**
     * Any sequence of edits, including out-of-range and near-duplicate input,
     * leaves points {@link CustomInterpPresetFunc} accepts.
     */
    @Test
    public void testEveryEditKeepsValidKnots() {
        final Random random = new Random(42);
        for (int run = 0; run < 200; run++) {
            final Curve curve = new Curve();
            for (int step = 0; step < 50; step++) {
                final int count = curve.getPointCount();
                switch (random.nextInt(5)) {
                    case 0:
                        curve.addPoint(random.nextDouble() * 1.4 - 0.2, random.nextDouble() * 1.4 - 0.2);
                        break;
                    case 1:
                        final double nearX = curve.getX(random.nextInt(count)) + (random.nextDouble() - 0.5) * 4e-6;
                        curve.addPoint(nearX, random.nextDouble());
                        break;
                    case 2:
                        curve.removePoint(random.nextInt(count));
                        break;
                    case 3:
                        curve.setPoint(random.nextInt(count), random.nextDouble() * 1.4 - 0.2);
                        break;
                    default:
                        curve.invert();
                        break;
                }
                CustomInterpPresetFunc.requireValidKnots(curve.xsArray(), curve.ysArray());
            }
        }
    }
}
