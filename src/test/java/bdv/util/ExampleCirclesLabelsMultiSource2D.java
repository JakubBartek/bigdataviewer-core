package bdv.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MappingModel;
import bdv.tools.brightness.RangeMode;
import bdv.tools.brightness.RealLUTConverter;
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
 * Same 18 labeled circles as {@link ExampleCirclesLabels2D} (grid layout,
 * values 1 to 18, background 0), but split across several sources instead of
 * one: a dedicated source containing only the background (no circles at
 * all), plus one source per group of up to 4 circles. Useful for exercising
 * the LUT editor across multiple, independently-configured sources.
 */
public class ExampleCirclesLabelsMultiSource2D {

    private static final int NUMBER_OF_CIRCLES = 18;
    private static final int COLUMNS = 6;
    private static final int ROWS = 3;
    private static final int CELL_SIZE = 100;
    private static final int RADIUS = 35;
    private static final int GROUP_SIZE = 4;

    private static final int WIDTH = COLUMNS * CELL_SIZE;
    private static final int HEIGHT = ROWS * CELL_SIZE;

    public static void main(final String[] args) {
        final List<ConverterSetup> converterSetups = new ArrayList<>();
        final List<SourceAndConverter<?>> sourcesAndConverters = new ArrayList<>();

        // A source of its own containing only the background (0), no circles.
        addSource(converterSetups, sourcesAndConverters, backgroundImage(), "Background (0)");

        // One source per group of up to 4 circles.
        for (int groupStart = 0; groupStart < NUMBER_OF_CIRCLES; groupStart += GROUP_SIZE) {
            final int groupEnd = Math.min(groupStart + GROUP_SIZE, NUMBER_OF_CIRCLES);
            final Img<UnsignedByteType> img = groupImage(groupStart, groupEnd);
            final String title = "Labels " + (groupStart + 1) + "-" + groupEnd;
            addSource(converterSetups, sourcesAndConverters, img, title);
        }

        BdvHandleFrame handle = new BdvHandleFrame(Bdv.options().is2D());
        handle.add(converterSetups, sourcesAndConverters, 1);
    }

    private static Img<UnsignedByteType> backgroundImage() {
        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(WIDTH, HEIGHT);
        for (final UnsignedByteType pixel : img) {
            pixel.set(0);
        }
        return img;
    }

    private static Img<UnsignedByteType> groupImage(final int groupStart, final int groupEnd) {
        final Img<UnsignedByteType> img = ArrayImgs.unsignedBytes(WIDTH, HEIGHT);
        for (final UnsignedByteType pixel : img) {
            pixel.set(0);
        }

        final RandomAccess<UnsignedByteType> ra = img.randomAccess();
        for (int i = groupStart; i < groupEnd; i++) {
            drawCircle(ra, i);
        }
        return img;
    }

    /**
     * Draws circle number {@code i} (0-based, value {@code i + 1}) at its
     * fixed grid position -- the same position it would have in
     * {@link ExampleCirclesLabels2D}, regardless of which group/source it
     * ends up in.
     */
    private static void drawCircle(final RandomAccess<UnsignedByteType> ra, final int i) {
        final int col = i % COLUMNS;
        final int row = i / COLUMNS;
        final int cx = col * CELL_SIZE + CELL_SIZE / 2;
        final int cy = row * CELL_SIZE + CELL_SIZE / 2;
        final int value = i + 1;

        final int minX = Math.max(0, cx - RADIUS);
        final int maxX = Math.min(WIDTH - 1, cx + RADIUS);
        final int minY = Math.max(0, cy - RADIUS);
        final int maxY = Math.min(HEIGHT - 1, cy + RADIUS);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                final int dx = x - cx;
                final int dy = y - cy;

                if ((dx * dx) + (dy * dy) <= (RADIUS * RADIUS)) {
                    ra.setPosition(x, 0);
                    ra.setPosition(y, 1);
                    ra.get().set(value);
                }
            }
        }
    }

    private static void addSource(final List<ConverterSetup> converterSetups,
                                   final List<SourceAndConverter<?>> sourcesAndConverters,
                                   final Img<UnsignedByteType> img,
                                   final String title) {
        final DemoLutConnector<UnsignedByteType> demoLUT = new DemoLutConnector<>(img);
        final Converter<UnsignedByteType, ARGBType> converter = demoLUT.getConverter();
        makeBackgroundTransparent(converter);

        final SourceAndConverter<UnsignedByteType> sac = createSAC(img, converter, title);
        final ConverterSetup cs = new DemoLutConnectorConverterSetup<>(demoLUT, sac, converterSetups.size());

        converterSetups.add(cs);
        sourcesAndConverters.add(sac);
    }

    /**
     * Makes raw value 0 (the background) render fully transparent instead of
     * opaque black. BDV's Fused display mode additively sums the R/G/B/A of
     * every visible source at each pixel (see AccumulateProjectorARGB); since
     * every source here has background covering nearly the whole canvas, an
     * opaque background on any one source would wash out the combined image.
     */
    @SuppressWarnings("unchecked")
    private static void makeBackgroundTransparent(final Converter<UnsignedByteType, ARGBType> converter) {
        final RealLUTConverter<UnsignedByteType> lutConverter = (RealLUTConverter<UnsignedByteType>) converter;
        final MappingModel mapping = new MappingModel();
        mapping.setRangeMode(RangeMode.CYCLIC);
        mapping.setTreatMinAsBackground(true);
        mapping.setBackgroundColor(0x00000000);
        lutConverter.setMapping(mapping);
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
