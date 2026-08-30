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
package bdv.tools.brightness.palette;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.tools.brightness.presetfunc.PresetFunc;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link PresetPaletteWrapper}: the single wrapper that pairs a
 * {@link PresetFunc} with a color scheme. The distinctive thing to prove is
 * that the wrapper itself is scheme-agnostic -- a {@link DiscreteColorScheme}
 * floors the preset function's value to a stop while a
 * {@link ContinuousColorScheme} interpolates it, with no other difference --
 * plus the boundary handling and construction invariants it owns.
 */
public class PresetPaletteWrapperTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	private static ContinuousColorScheme continuousThreeStops()
	{
		return new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } ); // paletteRangeLength 2
	}

	private static DiscreteColorScheme discreteThreeStops()
	{
		return new DiscreteColorScheme( new int[] { RED, GREEN, BLUE } ); // paletteRangeLength 3
	}

	// -- construction --------------------------------------------------------

	@Test
	public void testConstructorRejectsNullArguments()
	{
		final PresetFunc preset = new LinearPresetFunc( 0f, 1f, 2 );
		assertThrowsNpe( () -> new PresetPaletteWrapper( null, preset ) );
		assertThrowsNpe( () -> new PresetPaletteWrapper( continuousThreeStops(), null ) );
		assertThrowsNpe( () -> new PresetPaletteWrapper( continuousThreeStops(), preset, null, BoundaryCondition.CLAMP ) );
		assertThrowsNpe( () -> new PresetPaletteWrapper( continuousThreeStops(), preset, BoundaryCondition.CLAMP, null ) );
	}

	@Test
	public void testConstructorRejectsMismatchedPaletteRangeLength()
	{
		// Continuous scheme has paletteRangeLength 2; this preset function has 3.
		final PresetFunc mismatched = new LinearPresetFunc( 0f, 1f, 3 );
		try
		{
			new PresetPaletteWrapper( continuousThreeStops(), mismatched );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testTwoArgConstructorDefaultsBothBoundaryConditionsToClamp()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getRightBoundaryCondition() );
	}

	@Test
	public void testGettersReturnConstructorArguments()
	{
		final ContinuousColorScheme scheme = continuousThreeStops();
		final PresetFunc preset = new LinearPresetFunc( 0f, 2f, 2 );
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, preset );

		Assert.assertSame( scheme, wrapper.getColorScheme() );
		Assert.assertSame( preset, wrapper.getPresetFunc() );
	}

	// -- the scheme decides floor vs interpolate -----------------------------

	/** With a continuous scheme, the linear preset spreads the range across the gradient; the ends and midpoint land exactly on stops. */
	@Test
	public void testContinuousSchemeInterpolates()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2f ) );
		// Between stops it blends rather than snapping: halfway from RED to GREEN is neither.
		final int quarter = wrapper.getRGBForRaw( 0.5f );
		Assert.assertNotEquals( RED, quarter );
		Assert.assertNotEquals( GREEN, quarter );
	}

	/** With a discrete scheme and the very same kind of preset, the value is floored to a single stop: flat bands, no blend. */
	@Test
	public void testDiscreteSchemeFloors()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ) );

		// Bands [0,1)->RED, [1,2)->GREEN, [2,3)->BLUE, sampled mid-band.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5f ) );
		// Flat within a band.
		Assert.assertEquals( wrapper.getRGBForRaw( 0.1f ), wrapper.getRGBForRaw( 0.9f ) );
	}

	@Test
	public void testGetPaletteValueForRawMatchesWhatGetRGBForRawLooksUp()
	{
		final ContinuousColorScheme scheme = continuousThreeStops();
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, new LinearPresetFunc( 0f, 2f, 2 ) );

		for ( final float raw : new float[] { -1f, 0f, 0.5f, 1f, 1.5f, 2f, 3f } )
			Assert.assertEquals( scheme.getRGB( wrapper.getPaletteValueForRaw( raw ) ), wrapper.getRGBForRaw( raw ) );
	}

	// -- boundary conditions -------------------------------------------------

	@Test
	public void testClampResolvesOutOfRangeToTheEdgeStops()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ) );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -100f ) ); // below -> first stop
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 100f ) ); // above -> last stop
	}

	@Test
	public void testCycleWrapsValuesAboveTheDomain()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		// Period 3: raw 3.5 wraps to 0.5 -> RED, 4.5 to 1.5 -> GREEN.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
	}

	/**
	 * {@code max} is the same point as {@code min} on a cyclic domain (like
	 * 0{@code deg}/360{@code deg}), so it must wrap to the first stop, not
	 * resolve to the last one -- otherwise the last stop's band is twice as
	 * wide as every other stop's, and the first stop never appears exactly at
	 * the seam.
	 */
	@Test
	public void testCycleWrapsExactlyAtTheDomainMaximum()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3f ) );
		// Every other full period lands on the same seam.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 6f ) );
	}

	/** Unlike CYCLE, CLAMP's domain is closed: exactly at max still resolves to the last stop. */
	@Test
	public void testClampResolvesExactlyAtTheDomainMaximumToTheLastStop()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ) );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 3f ) );
	}

	/** A translucent grey, to prove the SPECIAL color is used verbatim, not looked up in the palette. */
	private static final int SPECIAL = ARGBType.rgba( 128, 128, 128, 64 );

	@Test
	public void testSpecialUsesTheConfiguredColor()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 10f, 20f, 2 ),
				BoundaryCondition.SPECIAL, BoundaryCondition.SPECIAL );
		wrapper.setLeftSpecialColor( SPECIAL );
		wrapper.setRightSpecialColor( SPECIAL );

		// getRGBForRaw forces opaque; getRGBAForRaw keeps the real alpha.
		Assert.assertEquals( SPECIAL | 0xff000000, wrapper.getRGBForRaw( 5f ) );
		Assert.assertEquals( SPECIAL, wrapper.getRGBAForRaw( 5f ) );
		Assert.assertEquals( SPECIAL, wrapper.getRGBAForRaw( 100f ) );
		// In range is unaffected.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 10f ) );
	}

	@Test
	public void testSpecialDefaultsToTransparent()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 10f, 20f, 2 ),
				BoundaryCondition.SPECIAL, BoundaryCondition.CLAMP );

		Assert.assertEquals( 0, ARGBType.alpha( wrapper.getRGBAForRaw( 5f ) ) );
	}

	// -- alpha ---------------------------------------------------------------

	@Test
	public void testGetRGBForcesOpaqueWhileGetRGBAKeepsStopAlpha()
	{
		final int translucentRed = ARGBType.rgba( 255, 0, 0, 100 );
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { translucentRed, GREEN } );
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 1 ) );

		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBForRaw( 0f ) ) );
		Assert.assertEquals( 100, ARGBType.alpha( wrapper.getRGBAForRaw( 0f ) ) );
	}

	// -- setters -------------------------------------------------------------

	@Test
	public void testSetRawDomainReRangesThePresetFunc()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );
		wrapper.setRawDomain( 300.0, 500.0 );

		Assert.assertEquals( 300f, wrapper.getPresetFunc().getMin(), 0f );
		Assert.assertEquals( 500f, wrapper.getPresetFunc().getMax(), 0f );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 300f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 400f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 500f ) );
	}

	@Test
	public void testSetRawDomainRejectsMaxNotGreaterThanMin()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );
		try
		{
			wrapper.setRawDomain( 5.0, 5.0 );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testSetColorSchemeAndSetPresetFuncRejectMismatchedRangeLength()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );
		try
		{
			wrapper.setColorScheme( discreteThreeStops() ); // paletteRangeLength 3, preset still 2
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			wrapper.setPresetFunc( new LinearPresetFunc( 0f, 2f, 5 ) ); // 5 != scheme's 2
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	private static void assertThrowsNpe( final Runnable r )
	{
		try
		{
			r.run();
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
	}
}
