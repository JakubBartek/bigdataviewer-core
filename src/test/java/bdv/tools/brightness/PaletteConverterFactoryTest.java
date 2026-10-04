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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import bdv.tools.brightness.colorscheme.LegacyBdvColorPalette;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * How faithfully a {@link bdv.tools.brightness.colorscheme.LegacyBdvColorPalette}
 * reproduces the legacy converter, over every color, range and raw value, is
 * covered by {@code LegacyBdvColorPaletteTest}; here only that a conversion
 * actually installs it, so the converted source renders as it did before.
 */
public class PaletteConverterFactoryTest
{
	/**
	 * A source that exists only to answer {@code getType()} and
	 * {@code getName()} -- the only two things {@link PaletteConverterFactory}
	 * asks a source about. Nothing here is ever rendered. Also used by
	 * {@link LutEditorDialogTest}, whose dialog asks no more of a source.
	 */
	static class TypeOnlySource< T extends NumericType< T > > implements Source< T >
	{
		private final T type;

		TypeOnlySource( final T type )
		{
			this.type = type;
		}

		@Override
		public boolean isPresent( final int t )
		{
			return false;
		}

		@Override
		public RandomAccessibleInterval< T > getSource( final int t, final int level )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public RealRandomAccessible< T > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{}

		@Override
		public T getType()
		{
			return type;
		}

		@Override
		public String getName()
		{
			return "test";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		@Override
		public int getNumMipmapLevels()
		{
			return 1;
		}
	}

	private static < T extends NumericType< T > & NativeType< T > > SourceAndConverter< T > soc(
			final T type, final Converter< T, ARGBType > converter )
	{
		return new SourceAndConverter<>( new TypeOnlySource<>( type ), converter );
	}

	private static RealARGBColorConverter< DoubleType > legacy( final double min, final double max, final int argb )
	{
		final RealARGBColorConverter< DoubleType > converter = RealARGBColorConverter.create( new DoubleType(), min, max );
		converter.setColor( new ARGBType( argb ) );
		return converter;
	}

	private static SourceAndConverter< DoubleType > legacySoc( final double min, final double max, final int argb )
	{
		return soc( new DoubleType(), legacy( min, max, argb ) );
	}

	// -- canApproximate ------------------------------------------------------

	@Test
	public void testLegacyRealSourceCanBeApproximated()
	{
		assertTrue( PaletteConverterFactory.canApproximate( legacySoc( 0, 255, ARGBType.rgba( 255, 255, 255, 255 ) ) ) );
	}

	@Test
	public void testNullAndAlreadyConvertedSourcesAreNotApproximated()
	{
		assertFalse( PaletteConverterFactory.canApproximate( null ) );

		final SourceAndConverter< DoubleType > converted = legacySoc( 0, 255, ARGBType.rgba( 255, 255, 255, 255 ) );
		PaletteConverterFactory.approximateInPlace( converted );
		assertFalse( PaletteConverterFactory.canApproximate( converted ) );
		assertNull( PaletteConverterFactory.approximateInPlace( converted ) );
	}

	/** Without a color and a range to read off there is nothing to carry over. */
	@Test
	public void testNonColorConverterIsNotApproximated()
	{
		final Converter< DoubleType, ARGBType > plain = ( in, out ) -> out.set( 0 );
		assertFalse( PaletteConverterFactory.canApproximate( soc( new DoubleType(), plain ) ) );
	}

	/** {@link PaletteConverter} only accepts real-valued samples. */
	@Test
	public void testNonRealTypedSourceIsNotApproximated()
	{
		final Converter< ARGBType, ARGBType > argbConverter = new ScaledARGBColorConverter();
		assertFalse( PaletteConverterFactory.canApproximate( soc( new ARGBType(), argbConverter ) ) );
	}

	/**
	 * A volatile counterpart that cannot be converted blocks the whole
	 * conversion: converting only the non-volatile half would leave the source
	 * rendering in one scheme while loading and another once loaded.
	 */
	@Test
	public void testUnconvertibleVolatileCounterpartBlocksConversion()
	{
		final SourceAndConverter< ARGBType > volatileSoc = soc( new ARGBType(), new ScaledARGBColorConverter() );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final SourceAndConverter< DoubleType > main = new SourceAndConverter(
				new TypeOnlySource<>( new DoubleType() ),
				legacy( 0, 255, ARGBType.rgba( 255, 255, 255, 255 ) ),
				volatileSoc );
		assertFalse( PaletteConverterFactory.canApproximate( main ) );
	}

	// -- approximateInPlace --------------------------------------------------

	@Test
	public void testConversionReplacesTheConverterInPlace()
	{
		final SourceAndConverter< DoubleType > source = legacySoc( 10, 210, ARGBType.rgba( 255, 255, 255, 255 ) );
		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( source );

		assertNotNull( converted );
		assertSame( converted, source.getConverter() );
	}

	@Test
	public void testConversionKeepsTheDisplayRange()
	{
		final SourceAndConverter< DoubleType > source = legacySoc( 17.5, 923.25, ARGBType.rgba( 0, 255, 0, 255 ) );
		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( source );

		assertEquals( 17.5, converted.getMin(), 0.0 );
		assertEquals( 923.25, converted.getMax(), 0.0 );
	}

	/**
	 * A collapsed range leaves the ramp nothing to stretch across, which the
	 * new representation rejects outright while the legacy converter tolerated
	 * it. It is widened by one raw unit rather than failing the conversion.
	 */
	@Test
	public void testCollapsedRangeIsWidenedRatherThanRejected()
	{
		final SourceAndConverter< DoubleType > source = legacySoc( 42, 42, ARGBType.rgba( 255, 255, 255, 255 ) );
		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( source );

		assertEquals( 42.0, converted.getMin(), 0.0 );
		assertEquals( 43.0, converted.getMax(), 0.0 );
	}

	/**
	 * Both halves of a source must render through one and the same wrapper, so
	 * that an edit made through the LUT editor reaches the volatile converter
	 * too rather than only taking hold once loading finishes.
	 */
	@Test
	public void testVolatileCounterpartSharesTheSameWrapper()
	{
		final SourceAndConverter< DoubleType > volatileSoc = legacySoc( 10, 210, ARGBType.rgba( 255, 0, 0, 255 ) );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final SourceAndConverter< DoubleType > main = new SourceAndConverter(
				new TypeOnlySource<>( new DoubleType() ),
				legacy( 10, 210, ARGBType.rgba( 255, 0, 0, 255 ) ),
				volatileSoc );

		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( main );

		assertNotNull( converted );
		final Converter< ?, ARGBType > volatileConverter = volatileSoc.getConverter();
		assertTrue( volatileConverter instanceof PaletteConverter );
		assertSame( converted.getWrapper(), ( ( PaletteConverter< ? > ) volatileConverter ).getWrapper() );
	}

	/**
	 * The legacy converter is its own reference here: it is asked for its
	 * colors before the conversion and the new converter has to reproduce
	 * them. The sample points avoid exact rounding ties, which the two
	 * paths are allowed to break differently.
	 */
	@Test
	public void testConvertedSourceRendersAsTheLegacyConverterDid()
	{
		for ( final int color : new int[] { ARGBType.rgba( 255, 255, 255, 255 ), ARGBType.rgba( 255, 0, 0, 255 ), ARGBType.rgba( 255, 128, 0, 200 ) } )
		{
			final SourceAndConverter< DoubleType > source = legacySoc( 10, 210, color );
			final double[] raws = { -50, 10, 47, 61, 113, 190, 210 };
			final int[] expected = renderLegacy( legacy( 10, 210, color ), raws );

			final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( source );

			assertColors( expected, render( converted, raws ) );
		}
	}

	@Test
	public void testConvertedSourceRendersThroughALegacyPaletteOfItsColor()
	{
		final int color = ARGBType.rgba( 30, 200, 90, 255 );
		final SourceAndConverter< DoubleType > source = legacySoc( 0, 255, color );

		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( source );

		assertEquals( new LegacyBdvColorPalette( color ), PaletteConverterFactory.paletteFor( legacy( 0, 255, color ) ) );
		assertColors( new int[] { color }, render( converted, 255 ) );
	}

	// -- colorConvertersOf ---------------------------------------------------

	@Test
	public void testColorConvertersOfCollectsBothHalves()
	{
		final SourceAndConverter< DoubleType > volatileSoc = legacySoc( 0, 255, ARGBType.rgba( 255, 255, 255, 255 ) );
		@SuppressWarnings( { "unchecked", "rawtypes" } )
		final SourceAndConverter< DoubleType > main = new SourceAndConverter(
				new TypeOnlySource<>( new DoubleType() ),
				legacy( 0, 255, ARGBType.rgba( 255, 255, 255, 255 ) ),
				volatileSoc );

		final List< ColorConverter > converters = PaletteConverterFactory.colorConvertersOf( main );
		assertEquals( 2, converters.size() );
		assertSame( main.getConverter(), converters.get( 0 ) );
		assertSame( volatileSoc.getConverter(), converters.get( 1 ) );
	}

	@Test
	public void testColorConvertersOfSkipsWhatIsNotAColorConverter()
	{
		final Converter< DoubleType, ARGBType > plain = ( in, out ) -> out.set( 0 );
		assertTrue( PaletteConverterFactory.colorConvertersOf( soc( new DoubleType(), plain ) ).isEmpty() );
		assertTrue( PaletteConverterFactory.colorConvertersOf( null ).isEmpty() );
	}

	// -- helpers -------------------------------------------------------------

	private static int[] renderLegacy( final RealARGBColorConverter< DoubleType > converter, final double... raws )
	{
		final DoubleType in = new DoubleType();
		final ARGBType out = new ARGBType();
		final int[] argbs = new int[ raws.length ];
		for ( int i = 0; i < raws.length; i++ )
		{
			in.set( raws[ i ] );
			converter.convert( in, out );
			argbs[ i ] = out.get();
		}
		return argbs;
	}

	private static int[] render( final PaletteConverter< ? > converter, final double... raws )
	{
		final int[] argbs = new int[ raws.length ];
		for ( int i = 0; i < raws.length; i++ )
			argbs[ i ] = converter.getWrapper().getRGBAForRaw( raws[ i ] );
		return argbs;
	}

	/**
	 * Compare rendered colors as hex strings, so a failure prints the two
	 * ramps side by side instead of an index and two decimal ints.
	 */
	private static void assertColors( final int[] expected, final int[] actual )
	{
		assertEquals( hex( expected ), hex( actual ) );
	}

	private static String hex( final int[] argbs )
	{
		final StringBuilder sb = new StringBuilder();
		for ( final int argb : argbs )
			sb.append( String.format( "%08x ", argb ) );
		return sb.toString();
	}

	/** A minimal {@link ColorConverter} over a type that is not a {@code RealType}. */
	private static class ScaledARGBColorConverter implements Converter< ARGBType, ARGBType >, ColorConverter
	{
		private final ARGBType color = new ARGBType( ARGBType.rgba( 255, 255, 255, 255 ) );

		private double min = 0;

		private double max = 255;

		@Override
		public void convert( final ARGBType input, final ARGBType output )
		{
			output.set( input );
		}

		@Override
		public ARGBType getColor()
		{
			return color;
		}

		@Override
		public void setColor( final ARGBType c )
		{
			color.set( c );
		}

		@Override
		public boolean supportsColor()
		{
			return true;
		}

		@Override
		public double getMin()
		{
			return min;
		}

		@Override
		public double getMax()
		{
			return max;
		}

		@Override
		public void setMin( final double min )
		{
			this.min = min;
		}

		@Override
		public void setMax( final double max )
		{
			this.max = max;
		}
	}
}
