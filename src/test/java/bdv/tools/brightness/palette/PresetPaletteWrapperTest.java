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

	/**
	 * The property behind the bug this composition was actually reported for:
	 * with a categorical palette, one raw unit per color and
	 * {@link BoundaryCondition#CYCLE} on both ends, walking the raw axis must
	 * step through the colors one at a time forever, never showing the same
	 * color twice in a row.
	 * <p>
	 * Repeating the palette is this boundary condition's job and nothing else's
	 * -- a {@link StepPresetFunc}'s domain is exactly one pass wide -- so the
	 * property holds over the whole raw axis with no exception anywhere,
	 * including at {@code getMax()} and far below {@code getMin()}. It is swept
	 * over many periods either side of the domain rather than checked at a
	 * chosen point, because the wrong answers this replaced appeared and
	 * disappeared with the display range in a way nobody can eyeball.
	 */
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
	 * The same cyclic property with a step size that is not a dyadic fraction,
	 * checked hundreds of periods away from the domain. Deriving the domain from
	 * the step size made the cycle period as narrow as the palette itself rather
	 * than as wide as the display range, so a raw value out at the edge of the
	 * data now wraps hundreds of times instead of once, and every one of those
	 * wraps has to put it back in the right place.
	 * <p>
	 * Sampled at the middle of each color's band rather than on its edges. That
	 * is not a softer question, it is the only well-posed one out here: a raw
	 * value naming the {@code k}th boundary can only be written as
	 * {@code min + k * stepSize}, which is itself rounded, and for a non-dyadic
	 * step size that drifts off the true boundary by up to {@code 1.6e-13} of a
	 * band by {@code k = 2000}. Which side of the edge such a value falls on is
	 * genuinely undetermined, and no tolerance can recover it. Boundaries are
	 * pinned where they are exactly representable instead, by
	 * {@link #testCyclingIsExactAtEveryBoundaryForALabelImage}.
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

	/**
	 * The case that must be exact right at the boundaries, because it is the one
	 * where the boundaries are exactly representable and the one this is for: a
	 * label image, one color per integer id, cycling through the palette. Every
	 * id lands on its own color however far from {@code min} it is -- including
	 * past 2^24, where a {@code float} could no longer tell neighbouring ids
	 * apart at all.
	 */
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

	/**
	 * Wrapping breaks a tie toward the period boundary, so a raw value that is a
	 * whole number of periods away in the step size's own (decimal) terms starts
	 * the palette over instead of finishing the previous pass.
	 * <p>
	 * With a step size of 0.3 and 2 stops the period is 0.6, and 6.6 is 11 of
	 * them -- but in binary {@code 6.6 % 0.6} is {@code 0.5999999999999999},
	 * an eighth of a ULP short, which without the tie-break lands at the far end
	 * of the previous pass: the last color where the first belongs, on a
	 * difference far below anything the raw value can express. Neither reading
	 * is forced by the arithmetic; this pins the one that matches what the step
	 * size means to whoever typed it.
	 */
	@Test
	public void testCyclingBreaksTiesTowardThePeriodBoundary()
	{
		final int[] argb = { ARGBType.rgba( 10, 20, 30, 255 ), ARGBType.rgba( 40, 50, 60, 255 ) };
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( new DiscreteColorScheme( argb ),
				new StepPresetFunc( 0, 2, 0.3 ), BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		Assert.assertEquals( 0.6, wrapper.getPresetFunc().getMax(), 0.0 );
		Assert.assertNotEquals( "the binary remainder alone does not land on 0",
				0.0, 6.6 % 0.6, 0.0 );

		// 6.6 is 11 whole periods along, so it starts a fresh pass.
		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( 6.6 ) );
		// ...and so do the other whole-period multiples either side of it.
		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( 1.8 ) );
		Assert.assertEquals( argb[ 0 ], wrapper.getRGBAForRaw( -6.6 ) );
		// A value in the middle of the second band is untouched by the tie-break.
		// Sampled mid-band, not on the edge: out here a value naming a boundary
		// is only accurate to about 1e-15 of a band, so which side it falls on
		// is not determined -- see testCyclingIsExactManyPeriodsOutForNonDyadicStepSizes.
		Assert.assertEquals( argb[ 1 ], wrapper.getRGBAForRaw( 6.6 + 0.45 ) );
	}

	/**
	 * The counterpart under {@link BoundaryCondition#CLAMP}: the palette is
	 * traversed once and then held on its last color. This is the behavior that
	 * makes the display range's maximum cosmetic for a discrete mapping -- what
	 * happens past the last stop is the boundary condition's decision, not a
	 * function of how wide the range happens to be.
	 */
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
