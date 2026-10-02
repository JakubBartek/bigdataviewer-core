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

import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * {@link LegacyBdvColorPalette} is only worth having if it renders what the
 * old converter rendered, so the reference throughout is imglib2's own
 * {@link RealARGBColorConverter}, asked directly, never a hand-derived color.
 * <p>
 * The comparisons sweep colors (including translucent and mixed-hue ones),
 * display ranges (including non-dyadic widths and origins far from zero) and
 * raw values below, inside and above each range: a rounding disagreement
 * between the two paths shows up only for some ranges and not others.
 */
public class LegacyBdvColorPaletteTest
{
	private static final int[] COLORS = colors();

	private static final double[][] RANGES = ranges();

	/** How many evenly spaced raw values each range is sampled at, both ends included. */
	private static final int SAMPLES_PER_RANGE = 400;

	// -- the palette itself --------------------------------------------------

	@Test
	public void testIsARampFromBlackToTheColor()
	{
		final int color = ARGBType.rgba( 30, 200, 90, 255 );
		final LegacyBdvColorPalette palette = new LegacyBdvColorPalette( color );

		assertEquals( color, palette.getColor() );
		assertEquals( 2, palette.getLength() );
		assertEquals( ARGBType.rgba( 0, 0, 0, 255 ), palette.getStop( 0 ) );
		assertEquals( color, palette.getStop( 1 ) );
		assertTrue( palette.isInterpolated() );
	}

	/** The old converter renders black at its color's alpha, not opaque black. */
	@Test
	public void testBlackStopCarriesTheColorsAlpha()
	{
		final LegacyBdvColorPalette palette = new LegacyBdvColorPalette( ARGBType.rgba( 255, 0, 0, 77 ) );

		assertEquals( ARGBType.rgba( 0, 0, 0, 77 ), palette.getStop( 0 ) );
	}

	/**
	 * A palette is a value: knowing where its colors came from must not make
	 * it unequal to a plain palette with the same colors.
	 */
	@Test
	public void testEqualsAPlainPaletteWithTheSameStops()
	{
		final int color = ARGBType.rgba( 255, 0, 255, 255 );
		final Palette plain = new Palette( new int[] { ARGBType.rgba( 0, 0, 0, 255 ), color }, true );

		assertEquals( plain, new LegacyBdvColorPalette( color ) );
		assertEquals( new LegacyBdvColorPalette( color ), plain );
		assertEquals( plain.hashCode(), new LegacyBdvColorPalette( color ).hashCode() );
		assertEquals( Palette.DEFAULT, new LegacyBdvColorPalette( ARGBType.rgba( 255, 255, 255, 255 ) ) );
		assertNotEquals( new LegacyBdvColorPalette( color ), new LegacyBdvColorPalette( ARGBType.rgba( 255, 0, 254, 255 ) ) );
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

	/**
	 * Inside the range the two paths compute the same blend in a different
	 * order (see {@link LegacyBdvColorPalette}), so an exact {@code .5} can
	 * round to neighbouring values: each color channel may differ by at most
	 * one unit, and alpha, which is constant along the ramp, not at all.
	 */
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

	/** Both ends of the ramp are the palette's own stops, so no rounding can intervene there. */
	@Test
	public void testMatchesTheLegacyConverterExactlyAtBothEndsOfTheRange()
	{
		forEveryColorAndRange( ( color, min, max, legacy, wrapper ) ->
		{
			assertSameColor( color, min, max, min, legacyColor( legacy, min ), wrapper.getRGBAForRaw( min ) );
			assertSameColor( color, min, max, max, legacyColor( legacy, max ), wrapper.getRGBAForRaw( max ) );
		} );
	}

	/**
	 * Above the range the palette clamps to the color. The old converter
	 * agrees exactly when every channel of the color is 0 or 255, since
	 * clipping each channel at 255 then lands on the color itself.
	 */
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

	/**
	 * The known divergence, pinned down so it stays a decision rather than a
	 * surprise: past {@code max} the old converter keeps brightening a color
	 * with a channel strictly between 0 and 255, and the palette does not.
	 */
	@Test
	public void testDivergesFromTheLegacyConverterAboveTheRangeForUnsaturatedColors()
	{
		final int color = ARGBType.rgba( 128, 64, 0, 255 );
		final RealARGBColorConverter< DoubleType > legacy = legacyConverter( 0, 100, color );
		final PresetPaletteWrapper wrapper = wrapper( 0, 100, color );

		assertEquals( color, wrapper.getRGBAForRaw( 200 ) );
		assertNotEquals( legacyColor( legacy, 200 ), wrapper.getRGBAForRaw( 200 ) );
	}

	// -- helpers -------------------------------------------------------------

	private interface RangeCheck
	{
		void check( int color, double min, double max, RealARGBColorConverter< DoubleType > legacy, PresetPaletteWrapper wrapper );
	}

	private static void forEveryColorAndRange( final RangeCheck check )
	{
		for ( final int color : COLORS )
			for ( final double[] range : RANGES )
				check.check( color, range[ 0 ], range[ 1 ], legacyConverter( range[ 0 ], range[ 1 ], color ), wrapper( range[ 0 ], range[ 1 ], color ) );
	}

	/**
	 * The palette read the way {@code PaletteConverterFactory} reads it: a
	 * continuous scheme, a linear transfer function over the display range,
	 * both boundaries clamped.
	 */
	private static PresetPaletteWrapper wrapper( final double min, final double max, final int color )
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new LegacyBdvColorPalette( color ) );
		return new PresetPaletteWrapper( scheme, new LinearPresetFunc( min, max, scheme.getPaletteRangeLength() ) );
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
		return String.format( "color %08x, range [%s, %s], raw %s: legacy %08x, palette %08x", color, min, max, raw, expected, actual );
	}

	/**
	 * Every saturated color (each channel 0 or 255), some mixed hues and
	 * greys, colors with a non-opaque alpha, and a seeded random spread.
	 */
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

	/**
	 * Common bit depths, every integer width from 1 to 40 (most are not a
	 * power of two, so {@code 1 / width} is inexact), fractional widths, and
	 * origins far from zero.
	 */
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
