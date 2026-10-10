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
package bdv.tools.brightness.colorscheme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import bdv.tools.brightness.converter.PresetColorSchemeWrapper;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * The reference is imglib2's {@link RealARGBColorConverter} itself. Colors,
 * ranges and raw values are swept, since rounding disagreements only show up
 * for some ranges.
 */
public class LegacyBdvColorSchemeTest
{
	private static final int[] COLORS = colors();

	private static final double[][] RANGES = ranges();

	/** How many evenly spaced raw values each range is sampled at, both ends included. */
	private static final int SAMPLES_PER_RANGE = 400;

	// -- the scheme itself ---------------------------------------------------

	@Test
	public void testIsARampFromBlackToTheColor()
	{
		final int color = ARGBType.rgba( 30, 200, 90, 255 );
		final LegacyBdvColorScheme scheme = new LegacyBdvColorScheme( color );

		assertEquals( color, scheme.getColor() );
		assertEquals( 2, scheme.getFixCount() );
		assertEquals( ARGBType.rgba( 0, 0, 0, 255 ), scheme.getFix( 0 ) );
		assertEquals( color, scheme.getFix( 1 ) );
	}

	/** The old converter renders black at its color's alpha, not opaque black. */
	@Test
	public void testBlackFixCarriesTheColorsAlpha()
	{
		final LegacyBdvColorScheme scheme = new LegacyBdvColorScheme( ARGBType.rgba( 255, 0, 0, 77 ) );

		assertEquals( ARGBType.rgba( 0, 0, 0, 77 ), scheme.getFix( 0 ) );
	}

	@Test
	public void testEqualsAPlainSchemeWithTheSameFixes()
	{
		final int color = ARGBType.rgba( 255, 0, 255, 255 );
		final ContinuousColorScheme plain = new ContinuousColorScheme( new int[] { ARGBType.rgba( 0, 0, 0, 255 ), color } );

		assertEquals( plain, new LegacyBdvColorScheme( color ) );
		assertEquals( new LegacyBdvColorScheme( color ), plain );
		assertEquals( plain.hashCode(), new LegacyBdvColorScheme( color ).hashCode() );
		assertEquals( ContinuousColorScheme.DEFAULT, new LegacyBdvColorScheme( ARGBType.rgba( 255, 255, 255, 255 ) ) );
		assertNotEquals( new LegacyBdvColorScheme( color ), new LegacyBdvColorScheme( ARGBType.rgba( 255, 0, 254, 255 ) ) );
	}

	// -- against the old converter -------------------------------------------

	@Test
	public void testMatchesTheLegacyConverterExactlyBelowTheRange()
	{
		forEveryColorAndRange( ( color, min, max, legacy, wrapper ) ->
		{
			final double width = max - min;
			for ( final double raw : new double[] { min - width * 1e-9, min - 1, min - width, min - 1e6, -Double.MAX_VALUE } )
				assertSameColor( color, min, max, raw, legacyColor( legacy, raw ), wrapper.getRGBAForRaw( raw ) );
		} );
	}

	/** Rounding ties allow one unit per color channel; alpha must match exactly. */
	@Test
	public void testMatchesTheLegacyConverterWithinOneUnitInsideTheRange()
	{
		forEveryColorAndRange( ( color, min, max, legacy, wrapper ) ->
		{
			for ( int k = 0; k <= SAMPLES_PER_RANGE; k++ )
			{
				final double raw = k == SAMPLES_PER_RANGE ? max : min + ( max - min ) * k / SAMPLES_PER_RANGE;
				final int expected = legacyColor( legacy, raw );
				final int actual = wrapper.getRGBAForRaw( raw );
				final String where = describe( color, min, max, raw, expected, actual );
				assertTrue( where, Math.abs( ARGBType.red( expected ) - ARGBType.red( actual ) ) <= 1 );
				assertTrue( where, Math.abs( ARGBType.green( expected ) - ARGBType.green( actual ) ) <= 1 );
				assertTrue( where, Math.abs( ARGBType.blue( expected ) - ARGBType.blue( actual ) ) <= 1 );
				assertEquals( where, ARGBType.alpha( expected ), ARGBType.alpha( actual ) );
			}
		} );
	}

	/** Both ends of the ramp are the scheme's own fixes, so no rounding can intervene there. */
	@Test
	public void testMatchesTheLegacyConverterExactlyAtBothEndsOfTheRange()
	{
		forEveryColorAndRange( ( color, min, max, legacy, wrapper ) ->
		{
			assertSameColor( color, min, max, min, legacyColor( legacy, min ), wrapper.getRGBAForRaw( min ) );
			assertSameColor( color, min, max, max, legacyColor( legacy, max ), wrapper.getRGBAForRaw( max ) );
		} );
	}

	/** Only for colors whose channels are all 0 or 255. */
	@Test
	public void testClampsToTheColorAboveTheRangeAsTheLegacyConverterDoesForSaturatedColors()
	{
		forEveryColorAndRange( ( color, min, max, legacy, wrapper ) ->
		{
			final double width = max - min;
			for ( final double raw : new double[] { max + width * 1e-9, max + 1, max + width, max + 1e6, Double.MAX_VALUE } )
			{
				final int actual = wrapper.getRGBAForRaw( raw );
				assertSameColor( color, min, max, raw, color, actual );
				if ( isSaturated( color ) )
					assertSameColor( color, min, max, raw, legacyColor( legacy, raw ), actual );
			}
		} );
	}

	/** Pins the known divergence above {@code max}. */
	@Test
	public void testDivergesFromTheLegacyConverterAboveTheRangeForUnsaturatedColors()
	{
		final int color = ARGBType.rgba( 128, 64, 0, 255 );
		final RealARGBColorConverter< DoubleType > legacy = legacyConverter( 0, 100, color );
		final PresetColorSchemeWrapper wrapper = wrapper( 0, 100, color );

		assertEquals( color, wrapper.getRGBAForRaw( 200 ) );
		assertNotEquals( legacyColor( legacy, 200 ), wrapper.getRGBAForRaw( 200 ) );
	}

	// -- helpers -------------------------------------------------------------

	private interface RangeCheck
	{
		void check( int color, double min, double max, RealARGBColorConverter< DoubleType > legacy, PresetColorSchemeWrapper wrapper );
	}

	private static void forEveryColorAndRange( final RangeCheck check )
	{
		for ( final int color : COLORS )
			for ( final double[] range : RANGES )
				check.check( color, range[ 0 ], range[ 1 ], legacyConverter( range[ 0 ], range[ 1 ], color ), wrapper( range[ 0 ], range[ 1 ], color ) );
	}

	/** As {@code ColorSchemeConverterFactory} sets it up. */
	private static PresetColorSchemeWrapper wrapper( final double min, final double max, final int color )
	{
		final ContinuousColorScheme scheme = new LegacyBdvColorScheme( color );
		return new PresetColorSchemeWrapper( scheme, new LinearPresetFunc( min, max, scheme.getRange() ) );
	}

	private static RealARGBColorConverter< DoubleType > legacyConverter( final double min, final double max, final int color )
	{
		final RealARGBColorConverter< DoubleType > converter = RealARGBColorConverter.create( new DoubleType(), min, max );
		converter.setColor( new ARGBType( color ) );
		return converter;
	}

	private static int legacyColor( final RealARGBColorConverter< DoubleType > legacy, final double raw )
	{
		final ARGBType out = new ARGBType();
		legacy.convert( new DoubleType( raw ), out );
		return out.get();
	}

	private static boolean isSaturated( final int color )
	{
		for ( final int channel : new int[] { ARGBType.red( color ), ARGBType.green( color ), ARGBType.blue( color ) } )
			if ( channel != 0 && channel != 255 )
				return false;
		return true;
	}

	private static void assertSameColor( final int color, final double min, final double max, final double raw, final int expected, final int actual )
	{
		assertEquals( describe( color, min, max, raw, expected, actual ), String.format( "%08x", expected ), String.format( "%08x", actual ) );
	}

	private static String describe( final int color, final double min, final double max, final double raw, final int expected, final int actual )
	{
		return String.format( "color %08x, range [%s, %s], raw %s: legacy %08x, scheme %08x", color, min, max, raw, expected, actual );
	}

	/** Saturated, mixed, grey, translucent and seeded random colors. */
	private static int[] colors()
	{
		final List< Integer > colors = new ArrayList<>();
		for ( int bits = 1; bits < 8; bits++ )
			colors.add( ARGBType.rgba( ( bits & 1 ) * 255, ( ( bits >> 1 ) & 1 ) * 255, ( ( bits >> 2 ) & 1 ) * 255, 255 ) );
		colors.add( ARGBType.rgba( 255, 128, 0, 255 ) );
		colors.add( ARGBType.rgba( 128, 128, 128, 255 ) );
		colors.add( ARGBType.rgba( 1, 1, 1, 255 ) );
		colors.add( ARGBType.rgba( 255, 255, 255, 128 ) );
		colors.add( ARGBType.rgba( 0x99, 0x44, 0x99, 0 ) );
		final Random random = new Random( 1 );
		for ( int i = 0; i < 40; i++ )
			colors.add( ARGBType.rgba( random.nextInt( 256 ), random.nextInt( 256 ), random.nextInt( 256 ), i % 4 == 0 ? random.nextInt( 256 ) : 255 ) );
		return colors.stream().mapToInt( Integer::intValue ).toArray();
	}

	/** Bit depths, widths 1 to 40 (mostly non-dyadic), fractional widths, far origins. */
	private static double[][] ranges()
	{
		final List< double[] > ranges = new ArrayList<>();
		ranges.add( new double[] { 0, 255 } );
		ranges.add( new double[] { 0, 4095 } );
		ranges.add( new double[] { 0, 65535 } );
		ranges.add( new double[] { -100, 100 } );
		ranges.add( new double[] { 17.5, 923.25 } );
		ranges.add( new double[] { 0, 0.3 } );
		ranges.add( new double[] { 4610, 4613.7 } );
		ranges.add( new double[] { 1e9, 1e9 + 7 } );
		for ( int width = 1; width <= 40; width++ )
			ranges.add( new double[] { 0, width } );
		final Random random = new Random( 2 );
		for ( int i = 0; i < 20; i++ )
		{
			final double min = random.nextInt( 20000 ) - 10000 + random.nextDouble();
			ranges.add( new double[] { min, min + 0.5 + random.nextDouble() * random.nextInt( 5000 ) } );
		}
		return ranges.toArray( new double[ 0 ][] );
	}
}
