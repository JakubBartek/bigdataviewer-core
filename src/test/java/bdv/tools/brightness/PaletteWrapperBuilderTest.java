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
import bdv.tools.brightness.palette.PaletteWrapper;
import net.imglib2.display.ColorTable8;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link PaletteWrapperBuilder}: that it picks the discrete vs
 * continuous color scheme from the mapping's discrete flag, always
 * carries the curve over, and maps range mode / background onto the new
 * boundary model. Colors asserted are exact palette stops or safely mid-band,
 * so the expected values are deterministic rather than empirically sampled.
 */
public class PaletteWrapperBuilderTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	/** A 3-stop RGB palette (red, green, blue), no alpha component -- read as opaque. */
	private static ColorTable8 threeStopPalette()
	{
		final byte[] r = { ( byte ) 255, 0, 0 };
		final byte[] g = { 0, ( byte ) 255, 0 };
		final byte[] b = { 0, 0, ( byte ) 255 };
		return new ColorTable8( r, g, b );
	}

	// -- color-scheme selection ----------------------------------------------

	@Test
	public void testInterpolateGivesAContinuousScheme()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( false );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 2 );
		Assert.assertTrue( wrapper.getColorScheme() instanceof ContinuousColorScheme );
	}

	@Test
	public void testTruncateGivesADiscreteScheme()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );
		Assert.assertTrue( wrapper.getColorScheme() instanceof DiscreteColorScheme );
	}

	// -- continuous mapping --------------------------------------------------

	/** Default (linear) curve, INTERPOLATE: the display range spreads across the gradient, min/mid/max landing on the three stops. */
	@Test
	public void testContinuousLinearSpreadsRangeAcrossStops()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( false );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 2 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2f ) );
	}

	// -- discrete mapping ----------------------------------------------------

	/** TRUNCATE with the default (linear) curve: each stop is a flat band, so mid-band raw values pick their stop and two values in the same band agree (no blending). */
	@Test
	public void testDiscreteAssignsAFlatBandPerStop()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );

		// Bands [0,1)->RED, [1,2)->GREEN, [2,3)->BLUE; sampled safely inside each.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5f ) );
		// Flat within a band: two different raws in [0,1) give the same color.
		Assert.assertEquals( wrapper.getRGBForRaw( 0.1f ), wrapper.getRGBForRaw( 0.9f ) );
	}

	/**
	 * The curve is always applied; on a discrete scheme its value is floored to
	 * a stop (the user's "floored to the color value"). A curve that saturates
	 * to the top by the midpoint therefore pushes the middle of the range onto
	 * the last stop, where a linear curve would land it on the middle one.
	 */
	@Test
	public void testDiscreteFloorsTheCurveToAStop()
	{
		final LutEditorMapping bent = new LutEditorMapping();
		bent.setDiscrete( true );
		bent.getCurve().addPoint( 0.5, 255 ); // curve reaches the top already at the midpoint
		final PaletteWrapper bentWrapper = PaletteWrapperBuilder.build( threeStopPalette(), bent, 0, 3 );

		final LutEditorMapping linear = new LutEditorMapping();
		linear.setDiscrete( true );
		final PaletteWrapper linearWrapper = PaletteWrapperBuilder.build( threeStopPalette(), linear, 0, 3 );

		// raw 1.5 is the midpoint: the bent curve floors to the last stop, the linear one to the middle stop.
		Assert.assertEquals( BLUE, bentWrapper.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( GREEN, linearWrapper.getRGBForRaw( 1.5f ) );
	}

	// -- boundary + background (both wrapper kinds) --------------------------

	/** FIT (the default) clamps out-of-range values to the nearest edge stop. */
	@Test
	public void testFitClampsOutOfRange()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		mapping.setCyclic( false );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) ); // below -> first stop
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 100f ) ); // above -> last stop
	}

	/** CYCLIC wraps values above the domain back around it instead of clamping to the last stop. */
	@Test
	public void testCyclicWrapsOutOfRange()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		mapping.setCyclic( true );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );

		// Domain [0,3], period 3: raw 3.5 wraps to 0.5 (first stop), 4.5 to 1.5 (middle stop) --
		// rather than clamping to the last stop as FIT would.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
	}

	/** Treat-min-as-background maps below-range values to a left SPECIAL color, whose alpha (here transparent) survives on the RGBA path. */
	@Test
	public void testBackgroundBelowRangeUsesTheSpecialColor()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( false );
		mapping.setTreatMinAsBackground( true );
		mapping.setBackgroundColor( 0x00000000 ); // transparent
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 10, 20 );

		Assert.assertEquals( 0, ARGBType.alpha( wrapper.getRGBAForRaw( 5f ) ) ); // below min -> transparent
		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBAForRaw( 15f ) ) ); // in range -> opaque stop
	}
}
