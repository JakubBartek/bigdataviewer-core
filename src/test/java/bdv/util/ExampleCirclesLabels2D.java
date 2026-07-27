package bdv.util;

import java.util.Arrays;
import java.util.List;

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
 * Generates 18 labeled circles in 2D, arranged in a grid, each carrying a
 * distinct value from 1 to 18. The background is 0. Useful for exercising
 * the LUT editor's Cyclic range mode and "Treat min as Bg" option against
 * label/segmentation-like data.
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

        // create a LUTeditor 'lutEditor' that can do this:
        // final Converter<UnsignedByteType, ARGBType> converter = lutEditor.getConverter();
        // (so, this an editor that, based on its current settings, modifies its underlying Converter,
        // and we borrow its Converter to the BDV)
        DemoLutConnector<UnsignedByteType> demoLUT = new DemoLutConnector<>(img);
        final Converter<UnsignedByteType, ARGBType> converter = demoLUT.getConverter();

        // create SAC around this converter
        // (so that the SAC display will always go through our (by us controlled) converter)
        final SourceAndConverter<UnsignedByteType> sac = createSAC(img, converter, "18 Labeled Circles (1-18, bg=0)");

        // provide our adapter that implements ConverterSetup and is constructed around our LUT editor;
        // this one here fakes the job and uses BDV's default ConverterSetup, and only because of this
        // the params 'sac, '0' are required; normally only 'demoLUT' should be needed
        final ConverterSetup cs = new DemoLutConnectorConverterSetup<>(demoLUT, sac, 0);

        // this only shows an empty BDV frame and then it adds to it our SAC and our ConverterSetup
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
