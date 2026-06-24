package bdv.tools.brightness.lut;

import bdv.BigDataViewer;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import org.scijava.listeners.Listeners;

/**
 * Pretends to be a ConverterSetup that is, in fact, talking to some LUT.
 * Here, we don't do any LUT. Instead, we outsource to a PlaceHolderConverterSetup.
 */
public class DemoLutConnectorConverterSetup <T extends RealType<T>> implements ConverterSetup {
	public DemoLutConnectorConverterSetup(final DemoLutConnector<T> lut,
	                                      final SourceAndConverter<T> sac,
	                                      final int setupId) {

		//show memorize 'lut' and should change the @Override methods to deal with the 'lut',
		//and the line bellow should go away...
		cs = BigDataViewer.createConverterSetup(sac, setupId);
	}

	private final ConverterSetup cs;

	@Override
	public Listeners<SetupChangeListener> setupChangeListeners() {
		return cs.setupChangeListeners();
	}

	@Override
	public int getSetupId() {
		return cs.getSetupId();
	}

	@Override
	public void setDisplayRange(double min, double max) {
		cs.setDisplayRange(min, max);
	}

	@Override
	public void setColor(ARGBType color) {
		cs.setColor(color);
	}

	@Override
	public boolean supportsColor() {
		return cs.supportsColor();
	}

	@Override
	public double getDisplayRangeMin() {
		return cs.getDisplayRangeMin();
	}

	@Override
	public double getDisplayRangeMax() {
		return cs.getDisplayRangeMax();
	}

	@Override
	public ARGBType getColor() {
		return cs.getColor();
	}
}
