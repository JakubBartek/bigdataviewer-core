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

import java.util.Random;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Generates random gray spheres in 3D
 */
public class ExampleCircles3D {

    public static void main(final String[] args) {
        final int width = 256;
        final int height = 256;
        final int depth = 256;

        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(width, height, depth);

        for (final UnsignedByteType pixel : img) {
            pixel.set(60);
        }

        final Random random = new Random();
        final RandomAccess<UnsignedByteType> ra = img.randomAccess();

        final int numberOfSpheres = 40;

        for (int i = 0; i < numberOfSpheres; i++) {
            final int cx = random.nextInt(width);
            final int cy = random.nextInt(height);
            final int cz = random.nextInt(depth);
            final int radius = random.nextInt(25) + 10;

            final int grayValue = random.nextInt(135) + 120;

            final int minX = Math.max(0, cx - radius);
            final int maxX = Math.min(width - 1, cx + radius);
            final int minY = Math.max(0, cy - radius);
            final int maxY = Math.min(height - 1, cy + radius);
            final int minZ = Math.max(0, cz - radius);
            final int maxZ = Math.min(depth - 1, cz + radius);

            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {

                        final int dx = x - cx;
                        final int dy = y - cy;
                        final int dz = z - cz;

                        if ((dx * dx) + (dy * dy) + (dz * dz) <= (radius * radius)) {
                            ra.setPosition(x, 0);
                            ra.setPosition(y, 1);
                            ra.setPosition(z, 2);
                            ra.get().set(grayValue);
                        }
                    }
                }
            }
        }

        BdvFunctions.show(img, "Random 3D Spheres Test");

    }
}
