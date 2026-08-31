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
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.BoundaryCondition;
import bdv.tools.brightness.palette.PaletteWrapper;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link PaletteWrapperBuilder}: that the mapping's discrete
 * flag picks both the color scheme and which shape control feeds it (a curve
 * for continuous, a step size for discrete), and that the per-end boundary
 * conditions are carried across verbatim. Colors asserted are exact palette
 * stops or safely mid-band, so the expected values are deterministic rather
 * than empirically sampled.
 */
public class PaletteWrapperBuilderTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	/** A 3-stop opaque palette (red, green, blue). */
	private static Palette threeStopPalette()
	{
		return new Palette( new int[] { RED, GREEN, BLUE }, true );
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
	 * A discrete palette's shape is its step size, not the curve: one color per
	 * that many raw values, starting the palette over once it runs out. With 3
	 * stops over [0, 6] and one stop per raw unit, the palette is used twice.
	 */
	@Test
	public void testDiscreteStepSizeRepeatsThePaletteAcrossTheRange()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		mapping.setStepSize( 1.0 );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 6 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5f ) );
		// second pass through the palette
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 5.5f ) );
	}

	/**
	 * An unset step size ({@link LutEditorMapping#AUTO_STEP_SIZE}) resolves to
	 * one pass across the range, which is what a discrete palette does with no
	 * step size chosen: 3 stops over [0, 6] means 2 raw units each.
	 */
	@Test
	public void testDiscreteAutoStepSizeSpreadsThePaletteOnce()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		Assert.assertEquals( LutEditorMapping.AUTO_STEP_SIZE, mapping.getStepSize(), 0.0 );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 6 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 3f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 5f ) );
	}

	/** The curve shapes a continuous palette only -- a discrete one maps through its step size, so dragging the curve changes nothing. */
	@Test
	public void testDiscreteIgnoresTheCurve()
	{
		final LutEditorMapping bent = new LutEditorMapping();
		bent.setDiscrete( true );
		bent.setStepSize( 1.0 );
		bent.getCurve().addPoint( 0.5, 255 ); // would saturate the curve at the midpoint
		final PaletteWrapper bentWrapper = PaletteWrapperBuilder.build( threeStopPalette(), bent, 0, 6 );

		final LutEditorMapping plain = new LutEditorMapping();
		plain.setDiscrete( true );
		plain.setStepSize( 1.0 );
		final PaletteWrapper plainWrapper = PaletteWrapperBuilder.build( threeStopPalette(), plain, 0, 6 );

		for ( final float raw : new float[] { 0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f } )
			Assert.assertEquals( "raw " + raw, plainWrapper.getRGBForRaw( raw ), bentWrapper.getRGBForRaw( raw ) );
	}

	// -- boundary conditions (both wrapper kinds) ----------------------------

	/** CLAMP (the default at both ends) holds out-of-range values at the nearest edge stop. */
	@Test
	public void testClampHoldsOutOfRangeAtTheEdgeStops()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) ); // below -> first stop
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 100f ) ); // above -> last stop
	}

	/** CYCLE on one end wraps values past it back around the range, leaving the other end alone. */
	@Test
	public void testCycleWrapsOnlyTheEndItIsSetOn()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( true );
		mapping.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 3 );

		// Above: domain [0,3], period 3 -- raw 3.5 wraps to 0.5 (first stop), 4.5 to 1.5 (middle stop).
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
		// Below is still CLAMP, so it is unaffected.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) );
	}

	/** SPECIAL paints its own color rather than a palette one -- here transparent, whose alpha survives on the RGBA path. */
	@Test
	public void testSpecialUsesItsOwnColorAtEitherEnd()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setDiscrete( false );
		mapping.setLeftBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setLeftSpecialColor( 0x00000000 ); // transparent
		mapping.setRightBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setRightSpecialColor( 0xffaabbcc );
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( threeStopPalette(), mapping, 10, 20 );

		Assert.assertEquals( 0, ARGBType.alpha( wrapper.getRGBAForRaw( 5f ) ) ); // below min -> transparent
		Assert.assertEquals( 0xffaabbcc, wrapper.getRGBAForRaw( 25f ) ); // above max -> its own color
		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBAForRaw( 15f ) ) ); // in range -> opaque stop
	}

	/** Each end is independent: the two conditions are carried onto the wrapper as given, not collapsed into one range mode. */
	@Test
	public void testBoundaryConditionsAreCarriedPerEnd()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		final PresetPaletteWrapper wrapper = ( PresetPaletteWrapper ) PaletteWrapperBuilder.build( threeStopPalette(), mapping, 0, 2 );

		Assert.assertEquals( BoundaryCondition.SPECIAL, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getRightBoundaryCondition() );
	}
}
