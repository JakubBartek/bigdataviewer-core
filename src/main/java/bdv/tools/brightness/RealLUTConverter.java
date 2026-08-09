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
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package bdv.tools.brightness;

import net.imglib2.converter.Converter;
import net.imglib2.display.AbstractLinearRange;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.ColorTable;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * RealLUTConverter contains a {@link ColorTable} (typically a
 * {@link ColorTable8} or a {@link ColorTableLut}), through which samples are
 * filtered. Input values are interpreted as indices into the color table,
 * optionally reshaped first by a {@link MappingModel}.
 */
public class RealLUTConverter< R extends RealType< R > > extends
		AbstractLinearRange implements Converter< R, ARGBType >, ColorConverter
{

	private ColorTable lut = null;

	/** {@link ColorTableLut#colorPositions(ColorTable)} of {@link #lut}, cached since {@link #convert} runs per pixel. */
	private double[] lutColorPositions = null;

	private MappingModel mapping = null;

	public RealLUTConverter()
	{
		super();
	}

	public RealLUTConverter( final double min, final double max,
	                         final ColorTable lut )
	{
		super( min, max );
		setLUT( lut );
	}

	public ColorTable getLUT()
	{
		return lut;
	}

	public void setLUT( final ColorTable lut )
	{
		this.lut = lut == null ? ColorTableLut.DEFAULT : lut;
		this.lutColorPositions = ColorTableLut.colorPositions( this.lut );
	}

	public MappingModel getMapping()
	{
		return mapping;
	}

	public void setMapping( final MappingModel mapping )
	{
		this.mapping = mapping;
	}

	@Override
	public void convert( final R input, final ARGBType output )
	{
		final double a = input.getRealDouble();
		final int argb;
		if ( mapping != null && mapping.isBackgroundValue( a, min ) )
			argb = mapping.getBackgroundColor();
		else if ( mapping != null )
			argb = ColorTableLut.lookupARGB( lut, 0, 255, mapping.mapToLutIndexForColor( a, min, max, lutColorPositions ), mapping.getValueMatching() );
		else
			argb = lut.lookupARGB( min, max, a );
		output.set( argb );
	}

	@Override
	public ARGBType getColor()
	{
		return new ARGBType( ARGBType.rgba( 255, 255, 255, 255 ) );
	}

	@Override
	public void setColor( final ARGBType c )
	{
		// LUT converters do not expose a single editable color.
	}

	@Override
	public boolean supportsColor()
	{
		return false;
	}

}
