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

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.palette.ContinuousPaletteWrapper;
import bdv.tools.brightness.palette.DiscretePaletteWrapper;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;

/**
 * Test cases for {@link PaletteConverter}: that it renders a real sample by
 * delegating to its {@link bdv.tools.brightness.palette.PaletteWrapper}, and
 * that changing the display range (min/max) re-ranges the wrapper's domain --
 * i.e. the render path and the brightness/contrast path are both wired to the
 * new architecture.
 */
public class PaletteConverterTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	private static int convert( final PaletteConverter< DoubleType > converter, final double rawValue )
	{
		final ARGBType out = new ARGBType();
		converter.convert( new DoubleType( rawValue ), out );
		return out.get();
	}

	// -- continuous ----------------------------------------------------------

	@Test
	public void testConvertsThroughAContinuousWrapper()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 2 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 100, 200 );

		Assert.assertEquals( RED, convert( converter, 100 ) );
		Assert.assertEquals( GREEN, convert( converter, 150 ) );
		Assert.assertEquals( BLUE, convert( converter, 200 ) );
	}

	/** The constructor applies the display range to the wrapper, overriding whatever range the preset function was built with. */
	@Test
	public void testConstructorAppliesDisplayRangeToWrapper()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
		// PresetFunc built over a throwaway [0,1] range: the converter's [100,200] must win.
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 2 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 100, 200 );

		Assert.assertEquals( 100f, wrapper.getPresetFunc().getMin(), 0f );
		Assert.assertEquals( 200f, wrapper.getPresetFunc().getMax(), 0f );
	}

	/** Moving the display range (as the brightness/contrast sliders do) re-ranges the mapping. */
	@Test
	public void testChangingDisplayRangeReRangesTheMapping()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 2 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 100, 200 );

		// Widen the window: what used to be the midpoint colour now sits at the new centre.
		converter.setMin( 0 );
		converter.setMax( 1000 );

		Assert.assertEquals( RED, convert( converter, 0 ) );
		Assert.assertEquals( GREEN, convert( converter, 500 ) );
		Assert.assertEquals( BLUE, convert( converter, 1000 ) );
	}

	/** A paired setMin/setMax that momentarily inverts the range must still settle correctly, not throw. */
	@Test
	public void testInvertedIntermediateRangeSettlesWithoutThrowing()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 2 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 100, 200 );

		// New range [500, 900]; setMin(500) transiently makes min(500) > max(200).
		converter.setMin( 500 );
		converter.setMax( 900 );

		Assert.assertEquals( RED, convert( converter, 500 ) );
		Assert.assertEquals( BLUE, convert( converter, 900 ) );
	}

	// -- discrete ------------------------------------------------------------

	@Test
	public void testConvertsThroughADiscreteWrapper()
	{
		final DiscreteColorScheme scheme = new DiscreteColorScheme( new int[] { RED, GREEN, BLUE } );
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( scheme, 0f, 1f );
		// 3 stops across [10, 40] -> stepSize 10.
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 10, 40 );

		Assert.assertEquals( RED, convert( converter, 10 ) );
		Assert.assertEquals( GREEN, convert( converter, 25 ) );
		Assert.assertEquals( BLUE, convert( converter, 39.9 ) );
	}

	/**
	 * The motivating case: a SPECIAL left boundary with a transparent color
	 * renders below-range values (e.g. a label image's background) fully
	 * transparent, which the old MappingModel did via treatMinAsBackground +
	 * backgroundColor(0x00000000). The alpha must survive convert().
	 */
	@Test
	public void testTransparentBackgroundBelowRangeSurvivesConversion()
	{
		final DiscreteColorScheme scheme = new DiscreteColorScheme( new int[] { RED, GREEN, BLUE } );
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( scheme, 0f, 1f,
				bdv.tools.brightness.palette.BoundaryCondition.SPECIAL,
				bdv.tools.brightness.palette.BoundaryCondition.CLAMP );
		// leftSpecialColor defaults to transparent; make the domain start at 10 so raw 5 is "background".
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 10, 40 );

		Assert.assertEquals( 0, ARGBType.alpha( convert( converter, 5 ) ) );  // below range -> transparent
		Assert.assertEquals( 255, ARGBType.alpha( convert( converter, 25 ) ) ); // in range -> opaque stop color
	}

	// -- ColorConverter contract ---------------------------------------------

	@Test
	public void testDoesNotSupportASingleColor()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 1 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 0, 1 );

		Assert.assertFalse( converter.supportsColor() );
	}

	@Test
	public void testExposesDisplayRangeAsMinMax()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 1 ) );
		final PaletteConverter< DoubleType > converter = new PaletteConverter<>( wrapper, 5, 42 );

		Assert.assertEquals( 5.0, converter.getMin(), 0.0 );
		Assert.assertEquals( 42.0, converter.getMax(), 0.0 );
	}
}
