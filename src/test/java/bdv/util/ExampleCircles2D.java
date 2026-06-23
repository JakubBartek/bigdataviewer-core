package bdv.util;

import java.util.Random;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Generates random circles in 2D
 */
public class ExampleCircles2D {

    public static void main(final String[] args) {
        final int width = 512;
        final int height = 512;

        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(width, height);

        for (final UnsignedByteType pixel : img) {
            pixel.set(60);
        }

        final Random random = new Random();
        final RandomAccess<UnsignedByteType> ra = img.randomAccess();

        final int numberOfCircles = 30;

        for (int i = 0; i < numberOfCircles; i++) {
            final int cx = random.nextInt(width);
            final int cy = random.nextInt(height);
            final int radius = random.nextInt(40) + 15;

            final int grayValue = random.nextInt(135) + 120;

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
                        ra.get().set(grayValue);
                    }
                }
            }
        }

        BdvFunctions.show(img, "Random Gray Circles Test", Bdv.options().is2D());
    }
}
