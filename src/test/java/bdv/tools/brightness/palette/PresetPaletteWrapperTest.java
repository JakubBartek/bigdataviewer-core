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
import bdv.tools.brightness.presetfunc.StepPresetFunc;
import net.imglib2.type.numeric.ARGBType;

/**
 * The wrapper is scheme-agnostic (a {@link DiscreteColorScheme} floors, a
 * {@link ContinuousColorScheme} interpolates); it owns boundary handling.
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

	@Test
	public void testContinuousSchemeInterpolates()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( continuousThreeStops(), new LinearPresetFunc( 0f, 2f, 2 ) );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2f ) );
		// blends between stops
		final int quarter = wrapper.getRGBForRaw( 0.5f );
		Assert.assertNotEquals( RED, quarter );
		Assert.assertNotEquals( GREEN, quarter );
	}

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

		for ( final double raw : new double[] { -1, 0, 0.5, 1, 1.5, 2, 3 } )
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

	/** Otherwise the last stop's band would be twice as wide. */
	@Test
	public void testCycleWrapsExactlyAtTheDomainMaximum()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3f ) );
		// Every other full period lands on the same seam.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 6f ) );
	}

	@Test
	public void testClampResolvesExactlyAtTheDomainMaximumToTheLastStop()
	{
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( discreteThreeStops(), new LinearPresetFunc( 0f, 3f, 3 ) );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 3f ) );
	}

	/** Translucent and not in the palette. */
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

	/** Regression: with one raw unit per color, no color may appear twice in a row. */
	@Test
	public void testCyclingAStepPaletteNeverRepeatsAColor()
	{
		for ( int stops = 2; stops <= 12; stops++ )
		{
			final int[] argb = new int[ stops ];
			for ( int i = 0; i < stops; i++ )
				argb[ i ] = ARGBType.rgba( i, 2 * i, 3 * i, 255 );
			final DiscreteColorScheme scheme = new DiscreteColorScheme( argb );

			for ( int min = -20; min <= 20; min += 5 )
			{
				final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme,
						new StepPresetFunc( min, stops, 1.0 ),
						BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

				for ( int raw = min - 200; raw <= min + 200; raw++ )
					Assert.assertEquals( "stops=" + stops + " min=" + min + " raw=" + raw,
							argb[ Math.floorMod( raw - min, stops ) ], wrapper.getRGBAForRaw( raw ) );
			}
		}
	}

	/**
	 * Sampled mid-band: out here {@code min + k * stepSize} is itself rounded,
	 * so which side of a boundary it lands on is undetermined. Boundaries are
	 * covered by {@link #testCyclingIsExactAtEveryBoundaryForALabelImage}.
	 */
	@Test
	public void testCyclingIsExactManyPeriodsOutForNonDyadicStepSizes()
	{
		final double[] awkwardStepSizes = { 0.3, 1.0 / 3.0, 0.7, 1.1, 0.123456789 };

		for ( int stops = 2; stops <= 7; stops++ )
		{
			final int[] argb = new int[ stops ];
			for ( int i = 0; i < stops; i++ )
				argb[ i ] = ARGBType.rgba( i, 2 * i, 3 * i, 255 );
			final DiscreteColorScheme scheme = new DiscreteColorScheme( argb );

			for ( final double stepSize : awkwardStepSizes )
				for ( final double min : new double[] { 0, 1.5, -30.25 } )
				{
					final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme,
							new StepPresetFunc( min, stops, stepSize ),
							BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

					for ( int k = -600; k <= 600; k++ )
						Assert.assertEquals( "stops=" + stops + " step=" + stepSize + " min=" + min + " band=" + k,
								argb[ Math.floorMod( k, stops ) ],
								wrapper.getRGBAForRaw( min + ( k + 0.5 ) * stepSize ) );
				}
		}
	}

	/** Including past 2^24, where {@code float} merges neighbouring ids. */
	@Test
	public void testCyclingIsExactAtEveryBoundaryForALabelImage()
	{
		for ( int stops = 2; stops <= 7; stops++ )
		{
			final int[] argb = new int[ stops ];
			for ( int i = 0; i < stops; i++ )
				argb[ i ] = ARGBType.rgba( i, 2 * i, 3 * i, 255 );

			for ( final long min : new long[] { 0, 1, -30, 1L << 24, 1L << 40 } )
			{
				final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( new DiscreteColorScheme( argb ),
						new StepPresetFunc( min, stops, 1.0 ),
						BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

				for ( long id = min - 500; id <= min + 500; id++ )
					Assert.assertEquals( "stops=" + stops + " min=" + min + " id=" + id,
							argb[ ( int ) Math.floorMod( id - min, stops ) ], wrapper.getRGBAForRaw( id ) );
			}
		}
	}

	/** In binary {@code 6.6 % 0.6} is {@code 0.5999999999999999}, yet 6.6 is 11 periods. */
	@Test
	public void testCyclingBreaksTiesTowardThePeriodBoundary()
	{
		final int[] argb = { ARGBType.rgba( 10, 20, 30, 255 ), ARGBType.rgba( 40, 50, 60, 255 ) };
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( new DiscreteColorScheme( argb ),
				new StepPresetFunc( 0, 2, 0.3 ), BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		Assert.assertEquals( 0.6, wrapper.getPresetFunc().getMax(), 0.0 );
		Assert.assertNotEquals( "the binary remainder alone does not land on 0",
				0.0, 6.6 % 0.6, 0.0 );

		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( 6.6 ) );
		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( 1.8 ) );
		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( -6.6 ) );
		// mid-band is untouched by the tie-break
		Assert.assertEquals( argb[ 1 ], wrapper.getRGBAForRaw( 6.6 + 0.45 ) );
	}

	@Test
	public void testClampingAStepPaletteHoldsTheLastColorPastTheDomain()
	{
		final int stops = 4;
		final int[] argb = new int[ stops ];
		for ( int i = 0; i < stops; i++ )
			argb[ i ] = ARGBType.rgba( i, 2 * i, 3 * i, 255 );

		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( new DiscreteColorScheme( argb ),
				new StepPresetFunc( 0, stops, 1.0 ), BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );

		for ( int raw = 0; raw < stops; raw++ )
			Assert.assertEquals( "raw=" + raw, argb[ raw ], wrapper.getRGBAForRaw( raw ) );
		for ( int raw = stops; raw <= stops + 500; raw++ )
			Assert.assertEquals( "raw=" + raw, argb[ stops - 1 ], wrapper.getRGBAForRaw( raw ) );
		for ( int raw = -1; raw >= -500; raw-- )
			Assert.assertEquals( "raw=" + raw, argb[ 0 ], wrapper.getRGBAForRaw( raw ) );
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
