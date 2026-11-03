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
import bdv.tools.brightness.converter.ColorSchemeConverter;
import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 * Tests that {@link BigDataViewer#createConverterToARGB} hands real-valued
 * sources a {@link ColorSchemeConverter} of the new color-mapping
 * architecture.
 */
public class CreateConverterToARGBTest
{
	@Test
	public void usesColorSchemeConverterForIntegerTypes()
	{
		final Object converter = BigDataViewer.createConverterToARGB( new UnsignedByteType() );
		Assert.assertTrue( converter instanceof ColorSchemeConverter );
		Assert.assertTrue( converter instanceof ColorConverter );
		Assert.assertTrue(((ColorConverter) converter).supportsColor());
	}

    /**
     * Regression: a color set through the setup, as {@code BdvStackSource.setColor} does, was ignored.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultConverterTakesAColorFromItsSetup()
    {
        final ColorSchemeConverter<UnsignedByteType> converter =
                (ColorSchemeConverter<UnsignedByteType>) BigDataViewer.createConverterToARGB(new UnsignedByteType());
        final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup(0, converter);
        final int green = ARGBType.rgba(0, 255, 0, 255);

        setup.setColor(new ARGBType(green));

        Assert.assertEquals(green, setup.getColor().get());
        final ARGBType output = new ARGBType();
        converter.convert(new UnsignedByteType(255), output);
        Assert.assertEquals(green, output.get());
        converter.convert(new UnsignedByteType(0), output);
        Assert.assertEquals(ARGBType.rgba(0, 0, 0, 255), output.get());
    }

	@Test
	public void usesColorSchemeConverterForDoubleTypesInZeroToOneRange()
	{
		final Object converter = BigDataViewer.createConverterToARGB( new DoubleType() );
		Assert.assertTrue( converter instanceof ColorSchemeConverter );
		Assert.assertTrue( converter instanceof ColorConverter );
		Assert.assertTrue(((ColorConverter) converter).supportsColor());

		// Default grayscale ramp over [0, 1]: black at the bottom, white at the top.
		final ARGBType output = new ARGBType();
		( ( ColorSchemeConverter< DoubleType > ) converter ).convert( new DoubleType( 0.0 ), output );
		Assert.assertEquals( 0xff000000, output.get() );
		( ( ColorSchemeConverter< DoubleType > ) converter ).convert( new DoubleType( 1.0 ), output );
		Assert.assertEquals( 0xffffffff, output.get() );
	}
}
