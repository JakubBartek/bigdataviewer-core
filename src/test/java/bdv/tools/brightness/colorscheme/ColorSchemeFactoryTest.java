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
package bdv.tools.brightness.colorscheme;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorConverter;
import net.imglib2.display.ColorTable8;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;

/** Loads the actual bundled color scheme resources. */
public class ColorSchemeFactoryTest
{
	@Test
	public void testDiscoverNamesFindsKnownSchemes()
	{
		final List< String > names = ColorSchemeFactory.discoverNames();

		Assert.assertTrue( names.contains( "Accent" ) );
		Assert.assertTrue( names.contains( "viridis" ) );
		Assert.assertTrue( names.contains( "tab10" ) );
	}

	@Test
	public void testDiscoverNamesIsSortedCaseInsensitively()
	{
		final List< String > names = ColorSchemeFactory.discoverNames();

		for ( int i = 1; i < names.size(); i++ )
			Assert.assertTrue( names.get( i - 1 ).compareToIgnoreCase( names.get( i ) ) <= 0 );
	}

	@Test
	public void testLoadFromJsonReturnsNullForUnknownName()
	{
		Assert.assertNull( ColorSchemeFactory.loadFromJson( "this-scheme-does-not-exist" ) );
	}

	/** A case-insensitive filesystem would find {@code gray.json}; {@code LutEditorDialog} relies on {@code null}. */
	@Test
	public void testLoadFromJsonDoesNotMatchGrayToBundledGray()
	{
		Assert.assertNotNull( ColorSchemeFactory.loadFromJson( "gray" ) );
		Assert.assertNull( ColorSchemeFactory.loadFromJson( "Gray" ) );
	}

	@Test
	public void testLoadFromJsonMatchesNamesCaseSensitively()
	{
		final List< String > names = ColorSchemeFactory.discoverNames();
		final Set< String > exact = new HashSet<>( names );
		for ( final String name : names )
		{
			final Set< String > variants = new HashSet<>();
			variants.add( name.toUpperCase( Locale.ROOT ) );
			variants.add( name.toLowerCase( Locale.ROOT ) );
			variants.add( swapCase( name.substring( 0, 1 ) ) + name.substring( 1 ) );
			for ( final String variant : variants )
				if ( !exact.contains( variant ) )
					Assert.assertNull( "loadFromJson( \"" + variant + "\" ) should not resolve to " + name, ColorSchemeFactory.loadFromJson( variant ) );
		}
	}

	private static String swapCase( final String s )
	{
		final StringBuilder sb = new StringBuilder( s.length() );
		for ( final char c : s.toCharArray() )
			sb.append( Character.isUpperCase( c ) ? Character.toLowerCase( c ) : Character.toUpperCase( c ) );
		return sb.toString();
	}

	/** Accent.json has 8 entries; count and order are preserved. */
	@Test
	public void testLoadFromJsonParsesFixesRGBA()
	{
		final IColorScheme scheme = ColorSchemeFactory.loadFromJson( "Accent" );

		Assert.assertNotNull( scheme );
		Assert.assertEquals( 8, scheme.getFixCount() );

		// index "0": [0.4980392156862745, 0.788235294117647, 0.4980392156862745, 1.0]
		final int argb = scheme.getFix( 0 );
		Assert.assertEquals( 127, ARGBType.red( argb ) );
		Assert.assertEquals( 201, ARGBType.green( argb ) );
		Assert.assertEquals( 127, ARGBType.blue( argb ) );
		Assert.assertEquals( 255, ARGBType.alpha( argb ) );
	}

	/** Value comparison only: a {@link IColorScheme} is immutable, so sharing an instance is fine. */
	@Test
	public void testLoadFromJsonIsRepeatable()
	{
		final IColorScheme first = ColorSchemeFactory.loadFromJson( "tab10" );
		final IColorScheme second = ColorSchemeFactory.loadFromJson( "tab10" );

		Assert.assertEquals( first, second );
		Assert.assertEquals( first.getFixCount(), second.getFixCount() );
	}

	@Test
	public void testLoadFromJsonHandlesLargeContinuousScheme()
	{
		final IColorScheme scheme = ColorSchemeFactory.loadFromJson( "viridis" );

		Assert.assertNotNull( scheme );
		Assert.assertEquals( 256, scheme.getFixCount() );
	}

	@Test
	public void testLoadFromJsonReflectsColorInterpolationDeclaration()
	{
		Assert.assertTrue( ColorSchemeFactory.loadFromJson( "Accent" ) instanceof DiscreteColorScheme );
		Assert.assertTrue( ColorSchemeFactory.loadFromJson( "viridis" ) instanceof ContinuousColorScheme );
	}

    @Test
    public void testLoadPicksSchemeKindFromInterpolated() {
        final int[] fixes = {ARGBType.rgba(255, 0, 0, 255), ARGBType.rgba(0, 0, 255, 255)};

        Assert.assertEquals(new ContinuousColorScheme(fixes), ColorSchemeFactory.load(fixes, true));
        Assert.assertEquals(new DiscreteColorScheme(fixes), ColorSchemeFactory.load(fixes, false));
    }

    @Test
    public void testLoadFromLegacyConverterRampsToItsColor() {
        final int color = ARGBType.rgba(30, 200, 90, 128);
        final RealARGBColorConverter<DoubleType> legacy = RealARGBColorConverter.create(new DoubleType(), 0, 255);
        legacy.setColor(new ARGBType(color));

        Assert.assertEquals(new LegacyBdvColorScheme(color), ColorSchemeFactory.loadFromLegacyConverter(legacy));
    }

    @Test
    public void testLoadFromLegacyConverterWithoutColorRampsToWhite() {
        final ColorConverter colorless = new ColorConverter() {
            @Override
            public ARGBType getColor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setColor(final ARGBType c) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean supportsColor() {
                return false;
            }

            @Override
            public double getMin() {
                return 0;
            }

            @Override
            public double getMax() {
                return 255;
            }

            @Override
            public void setMin(final double min) {
            }

            @Override
            public void setMax(final double max) {
            }
        };

        Assert.assertEquals(new LegacyBdvColorScheme(ARGBType.rgba(255, 255, 255, 255)),
                ColorSchemeFactory.loadFromLegacyConverter(colorless));
    }

	/** By value, not identity. */
	@Test
	public void testFindNameRecoversLoadedSchemesName()
	{
		Assert.assertEquals( "tab10", ColorSchemeFactory.findName( ColorSchemeFactory.loadFromJson( "tab10" ) ) );
		Assert.assertEquals( "viridis", ColorSchemeFactory.findName( ColorSchemeFactory.loadFromJson( "viridis" ) ) );
	}

	@Test
	public void testFindNameReturnsNullForUnmatchedScheme()
	{
		Assert.assertNull( ColorSchemeFactory.findName( ContinuousColorScheme.DEFAULT ) );
	}

	/** The default {@link ColorTable8} ramp is exactly the bundled {@code gist_gray}. */
	@Test
	public void testFindNameMatchesSchemeAdaptedFromForeignColorTable()
	{
		Assert.assertEquals( "gist_gray", ColorSchemeFactory.findName( ContinuousColorScheme.of( new ColorTable8() ) ) );
	}

	/** The cache survives the first call. */
	@Test
	public void testFindNameCacheIsReusable()
	{
		Assert.assertEquals( "tab10", ColorSchemeFactory.findName( ColorSchemeFactory.loadFromJson( "tab10" ) ) );

		final IColorScheme first = ColorSchemeFactory.loadFromJson( "tab10" );
		Assert.assertEquals( "tab10", ColorSchemeFactory.findName( first ) );
		Assert.assertEquals( "viridis", ColorSchemeFactory.findName( ColorSchemeFactory.loadFromJson( "viridis" ) ) );
	}
}
