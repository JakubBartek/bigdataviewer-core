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
package bdv.tools.brightness.editor;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.converter.BoundaryCondition;
import bdv.tools.brightness.converter.IColorSchemeWrapper;
import bdv.tools.brightness.converter.PresetColorSchemeWrapper;
import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;
import bdv.tools.brightness.presetfunc.StepPresetFunc;
import net.imglib2.type.numeric.ARGBType;

/** Colors asserted are exact fixes or mid-band, so no sampling is needed. */
public class ColorSchemeWrapperFactoryTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	private static ContinuousColorScheme continuousThreeFixes()
	{
		return new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
	}

	private static DiscreteColorScheme discreteThreeFixes()
	{
		return new DiscreteColorScheme( new int[] { RED, GREEN, BLUE } );
	}

	// -- preset-function selection -------------------------------------------

	@Test
	public void testContinuousSchemeIsShapedByTheCurve()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		final ContinuousColorScheme scheme = continuousThreeFixes();
		final PresetColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( scheme, mapping, 0, 2 );
		Assert.assertSame( scheme, wrapper.getColorScheme() );
		Assert.assertTrue( wrapper.getPresetFunc() instanceof CustomInterpPresetFunc );
	}

	@Test
	public void testDiscreteSchemeIsShapedByAStepFunction()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		final DiscreteColorScheme scheme = discreteThreeFixes();
		final PresetColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( scheme, mapping, 0, 3 );
		Assert.assertSame( scheme, wrapper.getColorScheme() );
		Assert.assertTrue( wrapper.getPresetFunc() instanceof StepPresetFunc );
	}

	// -- continuous mapping --------------------------------------------------

	/** Default (linear) curve, INTERPOLATE: the display range spreads across the gradient, min/mid/max landing on the three fixes. */
	@Test
	public void testContinuousLinearSpreadsRangeAcrossFixes()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( continuousThreeFixes(), mapping, 0, 2 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2f ) );
	}

	// -- discrete mapping ----------------------------------------------------

	/** TRUNCATE with the default (linear) curve: each fix is a flat band, so mid-band raw values pick their fix and two values in the same band agree (no blending). */
	@Test
	public void testDiscreteAssignsAFlatBandPerFix()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, 3 );

		// Bands [0,1)->RED, [1,2)->GREEN, [2,3)->BLUE; sampled safely inside each.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5f ) );
		// Flat within a band: two different raws in [0,1) give the same color.
		Assert.assertEquals( wrapper.getRGBForRaw( 0.1f ), wrapper.getRGBForRaw( 0.9f ) );
	}

	/** The display range's top does not matter; past the scheme CLAMP holds the last color. */
	@Test
	public void testDiscreteStepSizeDecidesHowFarTheSchemeReaches()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setStepSize( 1.0 );
		mapping.setRightBoundaryCondition( BoundaryCondition.CLAMP );

		for ( final double rangeMax : new double[] { 3, 6, 100 } )
		{
			final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, rangeMax );
			final String where = "range max " + rangeMax;

			Assert.assertEquals( where, RED, wrapper.getRGBForRaw( 0.5 ) );
			Assert.assertEquals( where, GREEN, wrapper.getRGBForRaw( 1.5 ) );
			Assert.assertEquals( where, BLUE, wrapper.getRGBForRaw( 2.5 ) );
			// Past the last fix, CLAMP holds -- and does so identically no
			// matter how wide the display range was.
			Assert.assertEquals( where, BLUE, wrapper.getRGBForRaw( 3.5 ) );
			Assert.assertEquals( where, BLUE, wrapper.getRGBForRaw( 5.5 ) );
		}
	}

	/** Repeating the scheme is the {@link bdv.tools.brightness.converter.BoundaryCondition#CYCLE} boundary's job, and the only way to get it. */
	@Test
	public void testDiscreteSchemeRepeatsOnlyWhenTheBoundaryIsCyclic()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setStepSize( 1.0 );
		mapping.setRightBoundaryCondition( bdv.tools.brightness.converter.BoundaryCondition.CYCLE );
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, 6 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.5 ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1.5 ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5 ) );
		// second pass through the scheme
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5 ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5 ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 5.5 ) );
	}

	/** 3 fixes over [0, 6] means 2 raw units each. */
	@Test
	public void testDiscreteAutoStepSizeSpreadsTheSchemeOnce()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		Assert.assertEquals( LutEditorMapping.AUTO_STEP_SIZE, mapping.getStepSize(), 0.0 );
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, 6 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 3f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 5f ) );
	}

	/** The curve shapes a continuous scheme only -- a discrete one maps through its step size, so dragging the curve changes nothing. */
	@Test
	public void testDiscreteIgnoresTheCurve()
	{
		final LutEditorMapping bent = new LutEditorMapping();
		bent.setStepSize( 1.0 );
		bent.getCurve().addPoint( 0.5, 255 ); // would saturate the curve at the midpoint
		final IColorSchemeWrapper bentWrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), bent, 0, 6 );

		final LutEditorMapping plain = new LutEditorMapping();
		plain.setStepSize( 1.0 );
		final IColorSchemeWrapper plainWrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), plain, 0, 6 );

		for ( final double raw : new double[] { 0.5, 1.5, 2.5, 3.5, 4.5, 5.5 } )
			Assert.assertEquals( "raw " + raw, plainWrapper.getRGBForRaw( raw ), bentWrapper.getRGBForRaw( raw ) );
	}

	// -- boundary conditions (both wrapper kinds) ----------------------------

	/** CLAMP holds out-of-range values at the nearest edge fix. */
	@Test
	public void testClampHoldsOutOfRangeAtTheEdgeFixes()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( BoundaryCondition.CLAMP );
		mapping.setRightBoundaryCondition( BoundaryCondition.CLAMP );
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, 3 );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) ); // below -> first fix
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 100f ) ); // above -> last fix
	}

	/** CYCLE on one end wraps values past it back around the range, leaving the other end alone. */
	@Test
	public void testCycleWrapsOnlyTheEndItIsSetOn()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( BoundaryCondition.CLAMP );
		mapping.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( discreteThreeFixes(), mapping, 0, 3 );

		// Above: domain [0,3], period 3 -- raw 3.5 wraps to 0.5 (first fix), 4.5 to 1.5 (middle fix).
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
		// Below is CLAMP, so it is unaffected.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) );
	}

	/** SPECIAL paints its own color rather than a scheme one -- here transparent, whose alpha survives on the RGBA path. */
	@Test
	public void testSpecialUsesItsOwnColorAtEitherEnd()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setLeftSpecialColor( 0x00000000 ); // transparent
		mapping.setRightBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setRightSpecialColor( 0xffaabbcc );
		final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build( continuousThreeFixes(), mapping, 10, 20 );

		Assert.assertEquals( 0, ARGBType.alpha( wrapper.getRGBAForRaw( 5f ) ) ); // below min -> transparent
		Assert.assertEquals( 0xffaabbcc, wrapper.getRGBAForRaw( 25f ) ); // above max -> its own color
		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBAForRaw( 15f ) ) ); // in range -> opaque fix
	}

	/** Each end is independent: the two conditions are carried onto the wrapper as given, not collapsed into one range mode. */
	@Test
	public void testBoundaryConditionsAreCarriedPerEnd()
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( BoundaryCondition.SPECIAL );
		mapping.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		final PresetColorSchemeWrapper wrapper = ( PresetColorSchemeWrapper ) ColorSchemeWrapperFactory.build( continuousThreeFixes(), mapping, 0, 2 );

		Assert.assertEquals( BoundaryCondition.SPECIAL, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getRightBoundaryCondition() );
	}
}
