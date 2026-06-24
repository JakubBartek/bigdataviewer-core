package bdv.tools.brightness.lut;

import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * This connects any future LutEditor or LutChooser with a source image of pixel type 'T'.
 * The future editors/choosers are expected to extend this class.
 *
 * @param <T> Imglib2 pixel type for real numbers (note: that includes integers too)
 */
public abstract class AbstractLutConnector <T extends RealType<T>> {
	public AbstractLutConnector(final T type) {
		this.type = type;
	}

	private final T type;

	//package protected
	T getType() {
		return type;
	}

	/**
	 * Provides access to a converter that delivers the appropriate color
	 * from the underlying LUT, based on the settings of that LUT.
	 *
	 * @return converter that samples from LUT
	 */
	abstract Converter<T, ARGBType> getConverter();
}
