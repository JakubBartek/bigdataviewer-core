package bdv.util;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.lut.DemoLutConnector;
import bdv.tools.brightness.lut.DemoLutConnectorConverterSetup;
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

        // create a LUTeditor 'lutEditor' that can do this:
        // final Converter<UnsignedByteType, ARGBType> converter = lutEditor.getConverter();
        // (so, this an editor that, based on its current settings, modifies its underlying Converter,
        // and we borrow its Converter to the BDV)
        DemoLutConnector<UnsignedByteType> demoLUT = new DemoLutConnector<>(img);
        final Converter<UnsignedByteType, ARGBType> converter = demoLUT.getConverter();

        // create SAC around this converter
        // (so that the SAC display will always go through our (by us controlled) converter)
        final SourceAndConverter<UnsignedByteType> sac = createSAC(img, converter, "Random Gray Circles Test");
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
