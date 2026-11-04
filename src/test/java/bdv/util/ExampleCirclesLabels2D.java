/*-
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
package bdv.util;

import java.util.Arrays;
import java.util.List;

import bdv.BigDataViewer;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

/**
 * Generates 18 labeled circles in 2D, arranged in a grid, each carrying a
 * distinct value from 1 to 18. The background is 0. Useful for exercising
 * the LUT editor's discrete schemes and its CYCLE and SPECIAL boundary
 * conditions against label/segmentation-like data.
 */
public class ExampleCirclesLabels2D {

    public static void main(final String[] args) {
        final int numberOfCircles = 18;
        final int columns = 6;
        final int rows = 3;
        final int cellSize = 100;
        final int radius = 35;

        final int width = columns * cellSize;
        final int height = rows * cellSize;

        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(width, height);

        for (final UnsignedByteType pixel : img) {
            pixel.set(0);
        }

        final RandomAccess<UnsignedByteType> ra = img.randomAccess();

        for (int i = 0; i < numberOfCircles; i++) {
            final int col = i % columns;
            final int row = i / columns;
            final int cx = col * cellSize + cellSize / 2;
            final int cy = row * cellSize + cellSize / 2;
            final int value = i + 1;

            final int minX = Math.max(0, cx - radius);
            final int maxX = Math.min(width - 1, cx + radius);
            final int minY = Math.max(0, cy - radius);
            final int maxY = Math.min(height - 1, cy + radius);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    final int dx = x - cx;
                    final int dy = y - cy;

                    if ((dx * dx) + (dy * dy) <= (radius * radius)) {
                        ra.setPosition(x, 0);
                        ra.setPosition(y, 1);
                        ra.get().set(value);
                    }
                }
            }
        }

        final Converter<UnsignedByteType, ARGBType> converter = BigDataViewer.createConverterToARGB(img.getType());
        final SourceAndConverter<UnsignedByteType> sac = createSAC(img, converter, "18 Labeled Circles (1-18, bg=0)");
        final ConverterSetup cs = BigDataViewer.createConverterSetup(sac, 0);

        BdvHandleFrame handle = new BdvHandleFrame(Bdv.options().is2D());
        handle.add(List.of(cs), List.of(sac), 1);
    }

    static <T extends RealType<T>>
    SourceAndConverter<T> createSAC(final RandomAccessibleInterval<T> img,
                                    final Converter<T,ARGBType> converter,
                                    final String title) {
        final double[] pixelResolutionValues = new double[img.numDimensions()];
        Arrays.fill(pixelResolutionValues, 1.0);

        final RandomAccessibleIntervalMipmapSource<T> mipmap = new RandomAccessibleIntervalMipmapSource<>(
            new RandomAccessibleInterval[]{ Views.addDimension(img,0,0) },
            img.getType(),
            new AffineTransform3D[]{new AffineTransform3D()},
            new FinalVoxelDimensions("px", pixelResolutionValues),
            title,
            true );

        return new SourceAndConverter<T>(mipmap, converter);
    }
}
