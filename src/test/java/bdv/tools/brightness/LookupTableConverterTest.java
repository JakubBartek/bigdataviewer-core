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
