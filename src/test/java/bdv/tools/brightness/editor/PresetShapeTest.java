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

import org.junit.Assert;
import org.junit.Test;

public class PresetShapeTest {
    /**
     * Regression: knot values were rounded to multiples of 1/255.
     */
    @Test
    public void testKnotsAreNotRounded() {
        Assert.assertEquals(0.49999999999999994, PresetShape.SIGMOID.ys()[4], 0.0);
        Assert.assertEquals(0.41148094458851686, PresetShape.LOG.ys()[1], 0.0);
    }

    @Test
    public void testEachShapeMatchesItsOwnSample() {
        for (final PresetShape shape : PresetShape.values()) {
            Assert.assertSame(shape, PresetShape.matching(shape.xs(), shape.ys()));
        }
    }

    @Test
    public void testAnEditedSampleMatchesNoShape() {
        final Curve curve = new Curve();
        curve.setPoints(PresetShape.LOG.xs(), PresetShape.LOG.ys());
        curve.setPoint(4, curve.getY(4) + 0.001);

        Assert.assertNull(PresetShape.matching(curve.xsArray(), curve.ysArray()));
    }

    @Test
    public void testAnInvertedSampleMatchesNoShape() {
        for (final PresetShape shape : PresetShape.values()) {
            final Curve curve = new Curve();
            curve.setPoints(shape.xs(), shape.ys());
            curve.invert();

            Assert.assertNull(shape.toString(), PresetShape.matching(curve.xsArray(), curve.ysArray()));
        }
    }

    /**
     * {@code 1 - (1 - y)} is not always {@code y} in floating point.
     */
    @Test
    public void testInvertingTwiceMatchesTheShapeAgain() {
        for (final PresetShape shape : PresetShape.values()) {
            final Curve curve = new Curve();
            curve.setPoints(shape.xs(), shape.ys());
            curve.invert();
            curve.invert();

            Assert.assertSame(shape, PresetShape.matching(curve.xsArray(), curve.ysArray()));
        }
    }
}
