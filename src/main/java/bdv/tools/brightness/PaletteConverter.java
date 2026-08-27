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

package bdv.tools.brightness;

import java.util.Objects;

import bdv.tools.brightness.palette.PaletteWrapper;
import net.imglib2.converter.Converter;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * Renders a real-valued source through the color-mapping architecture in
 * {@code bdv.tools.brightness.palette}: each sample is handed to a
 * {@link PaletteWrapper}, which turns it into a color
 * ({@code rawValue -> boundary handling -> paletteValue -> RGB}). The new-model
 * counterpart of {@link RealLUTConverter}, which does the same job through the
 * older {@code MappingModel}/{@code ColorTable} pair.
 * <p>
 * The converter's {@linkplain #getMin() min}/{@linkplain #getMax() max} are the
 * display range -- the raw window the palette is stretched across, driven by the
 * brightness/contrast controls via {@code ConverterSetup#setDisplayRange}. Every
 * change to it is forwarded to {@link PaletteWrapper#setRawDomain(double, double)},
 * so the wrapper's own domain always tracks the display range; the wrapper stays
 * the single source of truth for how a raw value becomes a color.
 *
 * @param <R> source pixel type.
 */
public class PaletteConverter< R extends RealType< R > > extends AbstractLinearRange
		implements Converter< R, ARGBType >, ColorConverter
{
	private PaletteWrapper wrapper;

	/**
	 * @param wrapper the palette wrapper each sample is mapped through.
	 * @param min     display-range minimum (maps to the start of the palette).
	 * @param max     display-range maximum (maps to the end of the palette).
	 */
	public PaletteConverter( final PaletteWrapper wrapper, final double min, final double max )
	{
		super( min, max );
		this.wrapper = Objects.requireNonNull( wrapper, "wrapper" );
		wrapper.setRawDomain( min, max );
	}

	public PaletteWrapper getWrapper()
	{
		return wrapper;
	}

	/** Swap in a different wrapper (e.g. a different palette or discrete/continuous kind), re-applying the current display range to it. */
	public void setWrapper( final PaletteWrapper wrapper )
	{
		this.wrapper = Objects.requireNonNull( wrapper, "wrapper" );
		syncDomain();
	}

	@Override
	public void convert( final R input, final ARGBType output )
	{
		// getRGBAForRaw, not getRGBForRaw, so a color stop's own alpha and a
		// transparent SPECIAL-boundary (background) color both survive to the
		// display -- matching how the old ColorTable path carried alpha.
		output.set( wrapper.getRGBAForRaw( ( float ) input.getRealDouble() ) );
	}

	@Override
	public void setMin( final double min )
	{
		super.setMin( min );
		syncDomain();
	}

	@Override
	public void setMax( final double max )
	{
		super.setMax( max );
		syncDomain();
	}

	/**
	 * Keep the wrapper's raw domain equal to the display range. Skipped while
	 * the range is momentarily inverted (as it can be between a paired
	 * {@code setMin}/{@code setMax}); the following call settles it.
	 */
	private void syncDomain()
	{
		if ( wrapper != null && max > min )
			wrapper.setRawDomain( min, max );
	}

	@Override
	public ARGBType getColor()
	{
		return new ARGBType( ARGBType.rgba( 255, 255, 255, 255 ) );
	}

	@Override
	public void setColor( final ARGBType c )
	{
		// A palette converter has no single editable color; its colors come
		// from the wrapper's color scheme.
	}

	@Override
	public boolean supportsColor()
	{
		return false;
	}
}
