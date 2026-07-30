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
package bdv.tools.brightness;

import bdv.BigDataViewer;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorConverter;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class LookupTableConverterTest
{
	@Test
	public void realLutConverterUsesLookupTable()
	{
		final RealLUTConverter< UnsignedByteType > converter = new RealLUTConverter<>(
				0,
				1,
				new ColorTable8(
						new byte[] { 0, ( byte ) 255 },
						new byte[] { 0, 0 },
						new byte[] { 0, 0 },
						new byte[] { ( byte ) 255, ( byte ) 255 } ) );

		final ARGBType output = new ARGBType();

		converter.convert( new UnsignedByteType( 0 ), output );
		Assert.assertEquals( 0xff000000, output.get() );

		converter.convert( new UnsignedByteType( 1 ), output );
		Assert.assertEquals( 0xffff0000, output.get() );

		Assert.assertFalse( ( ( ColorConverter ) converter ).supportsColor() );
	}

	@Test
	public void realLutConverterUsesBackgroundColorAtMinInCyclicMode()
	{
		final RealLUTConverter< UnsignedByteType > converter = new RealLUTConverter<>(
				5,
				100,
				new ColorTable8(
						new byte[] { 0, ( byte ) 255 },
						new byte[] { 0, 0 },
						new byte[] { 0, 0 },
						new byte[] { ( byte ) 255, ( byte ) 255 } ) );

		final MappingModel mapping = new MappingModel();
		mapping.setRangeMode( RangeMode.CYCLIC );
		mapping.setTreatMinAsBackground( true );
		mapping.setBackgroundColor( 0xff112233 );
		converter.setMapping( mapping );

		final ARGBType output = new ARGBType();

		// Raw value == min (5, the "left value of the range") gets the
		// dedicated background color, not one of the palette's cycled colors.
		converter.convert( new UnsignedByteType( 5 ), output );
		Assert.assertEquals( 0xff112233, output.get() );

		// Any other value cycles through the palette as usual.
		converter.convert( new UnsignedByteType( 6 ), output );
		Assert.assertNotEquals( 0xff112233, output.get() );

		// Disabling the flag means min is no longer special-cased.
		mapping.setTreatMinAsBackground( false );
		converter.convert( new UnsignedByteType( 5 ), output );
		Assert.assertNotEquals( 0xff112233, output.get() );
	}

	/**
	 * Regression test: the cyclic mapping only ever defines integer inputs
	 * exactly on a palette color, skipping the gap between min and min+1 to
	 * make the cycle start at min+1. Any value strictly in between (e.g.
	 * continuous image data) must still count as background, or it falls
	 * through and renders as the palette's *last* color instead -- making it
	 * look like the last color appears right after the background, before
	 * the first color.
	 */
	@Test
	public void realLutConverterUsesBackgroundColorForWholeUnitIntervalNotJustExactMin()
	{
		final ColorTableLut palette = new ColorTableLut(
				new double[] { 0.0, 1.0 / 3, 2.0 / 3, 1.0 },
				new double[] { 0.0, 0.0, 0.0, 1.0 },
				new double[] { 0.0, 0.0, 0.0, 0.0 },
				new double[] { 1.0, 0.0, 0.0, 0.0 },
				new double[] { 1.0, 1.0, 1.0, 1.0 } );

		final RealLUTConverter< DoubleType > converter = new RealLUTConverter<>( 5, 1000, palette );
		final MappingModel mapping = new MappingModel();
		mapping.setRangeMode( RangeMode.CYCLIC );
		mapping.setTreatMinAsBackground( true );
		mapping.setBackgroundColor( 0xff112233 );
		converter.setMapping( mapping );

		final ARGBType output = new ARGBType();

		for ( final double value : new double[] { 5.0, 5.1, 5.5, 5.999 } )
		{
			converter.convert( new DoubleType( value ), output );
			Assert.assertEquals( "value=" + value, 0xff112233, output.get() );
		}

		// At min+1, the cycle resumes with the palette's first color (blue).
		converter.convert( new DoubleType( 6.0 ), output );
		Assert.assertEquals( 0xff0000ff, output.get() );
	}

	@Test
	public void createConverterToARGBUsesLutForIntegerTypes()
	{
		final Object converter = BigDataViewer.createConverterToARGB( new UnsignedByteType() );
		Assert.assertTrue( converter instanceof RealLUTConverter );
		Assert.assertTrue( converter instanceof ColorConverter );
		Assert.assertFalse( ( ( ColorConverter ) converter ).supportsColor() );
	}

	@Test
	public void createConverterToARGBUsesLutForDoubleTypesInZeroToOneRange()
	{
		final Object converter = BigDataViewer.createConverterToARGB( new DoubleType() );
		Assert.assertTrue( converter instanceof RealLUTConverter );
		Assert.assertTrue( converter instanceof ColorConverter );
		Assert.assertFalse( ( ( ColorConverter ) converter ).supportsColor() );

		final ARGBType output = new ARGBType();
		( ( RealLUTConverter< DoubleType > ) converter ).convert( new DoubleType( 0.0 ), output );
		Assert.assertEquals( 0xff000000, output.get() );
		( ( RealLUTConverter< DoubleType > ) converter ).convert( new DoubleType( 1.0 ), output );
		Assert.assertEquals( 0xffffffff, output.get() );
	}
}
