package bdv.tools.brightness.lut;

import bdv.BigDataViewer;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

public class DemoLutConnector <T extends RealType<T>> extends AbstractLutConnector<T> {

	public DemoLutConnector(RandomAccessibleInterval<T> img) {
		super(img.getType());

		//for the demo here, use the standard BDV convertor
		mySpecialConverter = BigDataViewer.createConverterToARGB(img.getType());

		//create own dialog, etc.
		//expose a trigger (method) to be hooked with some GUI button to open this dialog, etc.
	}

	private final Converter<T, ARGBType> mySpecialConverter;

	@Override
	public Converter<T, ARGBType> getConverter() {
		return mySpecialConverter;
	}
}
