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
import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;
import bdv.tools.brightness.presetfunc.ExponentialPresetFunc;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.tools.brightness.presetfunc.LogarithmicPresetFunc;
import bdv.tools.brightness.presetfunc.PresetFunc;
import bdv.tools.brightness.presetfunc.SigmoidPresetFunc;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link ContinuousPaletteWrapper}.
 */
public class ContinuousPaletteWrapperTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	/** 3 stops, so the wrapped scheme's domain is the closed interval [0, 2]. */
	private static ContinuousColorScheme threeStops()
	{
		return new ContinuousColorScheme( new int[] { RED, GREEN, BLUE } );
	}

	/** raw in [100, 200] maps onto palette values [0, 2], matching {@link #threeStops()}'s domain exactly. */
	private static LinearPresetFunc linear()
	{
		return new LinearPresetFunc( 100f, 200f, 2f );
	}

	// -- construction --------------------------------------------------------

	@Test
	public void testConstructorRejectsMismatchedDelkaIntervalu()
	{
		// threeStops() has delkaIntervalu 2, this preset function has 3 -- they must agree.
		final PresetFunc mismatched = new LinearPresetFunc( 100f, 200f, 3f );
		try
		{
			new ContinuousPaletteWrapper( threeStops(), mismatched );
			Assert.fail( "expected IllegalArgumentException for mismatched getDelkaIntervalu()" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testConstructorRejectsNullArguments()
	{
		try
		{
			new ContinuousPaletteWrapper( null, linear() );
			Assert.fail( "expected NullPointerException for a null color scheme" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			new ContinuousPaletteWrapper( threeStops(), null );
			Assert.fail( "expected NullPointerException for a null preset function" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			new ContinuousPaletteWrapper( threeStops(), linear(), null, BoundaryCondition.CLAMP );
			Assert.fail( "expected NullPointerException for a null left boundary condition" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			new ContinuousPaletteWrapper( threeStops(), linear(), BoundaryCondition.CLAMP, null );
			Assert.fail( "expected NullPointerException for a null right boundary condition" );
		}
		catch ( final NullPointerException expected )
		{
		}
	}

	@Test
	public void testTwoArgConstructorDefaultsBothBoundaryConditionsToClamp()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getRightBoundaryCondition() );
	}

	@Test
	public void testGettersReturnConstructorArguments()
	{
		final ContinuousColorScheme scheme = threeStops();
		final PresetFunc presetFunc = linear();
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, presetFunc, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );

		Assert.assertSame( scheme, wrapper.getColorScheme() );
		Assert.assertSame( presetFunc, wrapper.getPresetFunc() );
	}

	// -- raw -> palette value mapping -----------------------------------------

	/** Covers "values inside the range", "exactly min" and "exactly max" landing precisely on stops. */
	@Test
	public void testRawToPaletteValueMappingAtStops()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 100f ) );   // min -> paletteValue 0
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 150f ) ); // midpoint -> paletteValue 1
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 200f ) );  // max -> paletteValue 2
	}

	/**
	 * A raw value inside the range that lands between two stops must produce
	 * exactly what the color scheme itself would produce for the
	 * corresponding palette value -- proof that the wrapper is composing
	 * {@link PresetFunc} and {@link ContinuousColorScheme} rather than doing
	 * any interpolation of its own (explicitly out of scope for this class).
	 */
	@Test
	public void testInteriorValueMatchesColorSchemeForTheSamePaletteValue()
	{
		final ContinuousColorScheme scheme = threeStops();
		final PresetFunc presetFunc = linear();
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, presetFunc );

		// raw=125 -> paletteValue=0.5, halfway between RED and GREEN.
		final float paletteValue = presetFunc.getPaletteValueForRaw( 125f );
		Assert.assertEquals( scheme.getRGB( paletteValue ), wrapper.getRGBForRaw( 125f ) );
	}

	/** Values below min and above max, under the default CLAMP boundary condition. */
	@Test
	public void testValuesBelowMinAndAboveMaxClampToTheNearestStop()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 99f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( -1000f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( Float.NEGATIVE_INFINITY ) );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 201f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 1000f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( Float.POSITIVE_INFINITY ) );
	}

	@Test
	public void testGetRGBForRawDelegatesToColorSchemesGetRGBNotGetRGBA()
	{
		// A stop with non-opaque alpha: getRGBForRaw must come back fully
		// opaque, matching ContinuousColorScheme#getRGB (not #getRGBA).
		final int translucentRed = ARGBType.rgba( 255, 0, 0, 100 );
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { translucentRed, GREEN } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 1f ) );

		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBForRaw( 0f ) ) );
	}

	/** The RGBA path carries the stop's own alpha through the same pipeline. */
	@Test
	public void testGetRGBAForRawPreservesTheStopsAlpha()
	{
		final int translucentRed = ARGBType.rgba( 255, 0, 0, 100 );
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { translucentRed, GREEN } );
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, new LinearPresetFunc( 0f, 1f, 1f ) );

		Assert.assertEquals( 100, ARGBType.alpha( wrapper.getRGBAForRaw( 0f ) ) );
		// Boundary handling applies to the RGBA path identically.
		Assert.assertEquals( 100, ARGBType.alpha( wrapper.getRGBAForRaw( -5f ) ) );
	}

	/** getPaletteValueForRaw is the same boundary-aware conversion getRGBForRaw uses, exposed on its own. */
	@Test
	public void testGetPaletteValueForRawMatchesWhatGetRGBForRawLooksUp()
	{
		final ContinuousColorScheme scheme = threeStops();
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, linear() );

		for ( final float raw : new float[] { 50f, 100f, 125f, 150f, 200f, 250f } )
			Assert.assertEquals( scheme.getRGB( wrapper.getPaletteValueForRaw( raw ) ), wrapper.getRGBForRaw( raw ) );
	}

	// -- independence from the particular PresetFunc -------------------------

	/**
	 * The wrapper must not care which transformation it was handed: swapping
	 * the preset function changes the resulting colors without any change to
	 * the wrapper, and the answer always matches
	 * {@code colorScheme.getRGB(presetFunc.getPaletteValueForRaw(raw))} --
	 * i.e. the wrapper only ever composes the two, never second-guesses which
	 * one it has.
	 */
	@Test
	public void testWorksWithEveryPresetFuncImplementationUniformly()
	{
		final CustomInterpPresetFunc custom = new CustomInterpPresetFunc( 100f, 200f, 2f );
		custom.setKnots( new double[] { 0.0, 0.5, 1.0 }, new double[] { 0.0, 0.9, 1.0 } );

		final PresetFunc[] presetFuncs = {
				new LinearPresetFunc( 100f, 200f, 2f ),
				new SigmoidPresetFunc( 100f, 200f, 2f ),
				new LogarithmicPresetFunc( 100f, 200f, 2f ),
				new ExponentialPresetFunc( 100f, 200f, 2f ),
				custom };

		for ( final PresetFunc presetFunc : presetFuncs )
		{
			final ContinuousColorScheme scheme = threeStops();
			final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( scheme, presetFunc );
			final String name = presetFunc.getClass().getSimpleName();

			for ( final float raw : new float[] { 100f, 120f, 150f, 180f, 200f } )
				Assert.assertEquals( name + " at raw=" + raw,
						scheme.getRGB( presetFunc.getPaletteValueForRaw( raw ) ), wrapper.getRGBForRaw( raw ) );

			// Whatever the shape, the domain edges still resolve through the
			// same code path, and out-of-range values still clamp.
			Assert.assertEquals( name, wrapper.getRGBForRaw( 100f ), wrapper.getRGBForRaw( -1000f ) );
			Assert.assertEquals( name, wrapper.getRGBForRaw( 200f ), wrapper.getRGBForRaw( 1000f ) );
		}
	}

	/** A non-linear preset function must actually change the result, or the test above would be vacuous. */
	@Test
	public void testDifferentPresetFuncsProduceDifferentColorsForTheSameRawValue()
	{
		final ContinuousPaletteWrapper linearWrapper = new ContinuousPaletteWrapper( threeStops(), new LinearPresetFunc( 100f, 200f, 2f ) );
		final ContinuousPaletteWrapper logWrapper = new ContinuousPaletteWrapper( threeStops(), new LogarithmicPresetFunc( 100f, 200f, 2f ) );

		Assert.assertNotEquals( linearWrapper.getRGBForRaw( 120f ), logWrapper.getRGBForRaw( 120f ) );
	}

	// -- CYCLE -----------------------------------------------------------

	/**
	 * CYCLE wraps a below-min raw value around to the far (right) side of the
	 * domain instead of collapsing onto the first stop -- the opposite of
	 * CLAMP's behavior for the same inputs.
	 */
	@Test
	public void testCycleWrapsBelowMinToTheOppositeEnd()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		// raw=50 is 50 below min; span is 100, so it wraps to raw=150 -> paletteValue 1 -> GREEN.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 50f ) );
		// raw=0 is exactly one full span below min -> wraps to raw=100 -> paletteValue 0 -> RED.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		// raw=-50, more than one full span below -> wraps to raw=150 again -> GREEN.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( -50f ) );
	}

	/** CYCLE wraps an above-max raw value back around to the start of the domain. */
	@Test
	public void testCycleWrapsAboveMaxToTheStart()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		// raw=250 is 50 above max -> wraps to raw=150 -> paletteValue 1 -> GREEN.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 250f ) );
		// raw=300, exactly one full span above max -> wraps to raw=100 -> RED.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 300f ) );
	}

	/** Left and right can independently be CYCLE vs. something else. */
	@Test
	public void testCycleCanBeSetOnOnlyOneSide()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.CYCLE, BoundaryCondition.CLAMP );

		// Left: cycles around to the last stop's neighborhood.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 50f ) );
		// Right: still plain clamp.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 1000f ) );
	}

	// -- SPECIAL -----------------------------------------------------------

	/**
	 * SPECIAL substitutes a fixed, user-chosen palette value (not a raw
	 * color) for an out-of-domain raw value -- it still goes through the
	 * color scheme's own lookup, same as every other case.
	 */
	@Test
	public void testSpecialUsesTheConfiguredPaletteValueOnTheLeft()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.SPECIAL, BoundaryCondition.CLAMP );
		wrapper.setLeftSpecialValue( 2f ); // stop 2 (blue), not the natural left edge (stop 0)

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 50f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -1000f ) );
		// Inside the domain is unaffected by the special value.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 100f ) );
	}

	@Test
	public void testSpecialUsesTheConfiguredPaletteValueOnTheRight()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.CLAMP, BoundaryCondition.SPECIAL );
		wrapper.setRightSpecialValue( 0f ); // stop 0 (red), not the natural right edge (stop 2)

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 250f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 1000f ) );
		// Inside the domain is unaffected by the special value.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 200f ) );
	}

	/**
	 * The default special value (never set) is 0 -- same as Java's default
	 * float field value -- so it must resolve to stop 0 without needing to be
	 * configured first.
	 */
	@Test
	public void testSpecialDefaultsToPaletteValueZero()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.SPECIAL, BoundaryCondition.SPECIAL );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 50f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 250f ) );
	}

	/**
	 * A left/right special value that is itself outside the color scheme's
	 * domain is not an error: it just clamps like any other out-of-domain
	 * palette value passed to the color scheme.
	 */
	@Test
	public void testOutOfDomainSpecialValueStillClampsInTheColorScheme()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.SPECIAL, BoundaryCondition.CLAMP );
		wrapper.setLeftSpecialValue( 999f );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 50f ) );
	}

	// -- palette values exactly at the boundary -----------------------------

	/**
	 * A raw value exactly at min or max is inside the domain (the closed
	 * interval requirements from {@link ContinuousColorScheme}), not
	 * boundary-condition territory -- shown here by CYCLE (which would
	 * produce a visibly different result) leaving them untouched.
	 */
	@Test
	public void testExactMinAndMaxAreNotBoundaryConditionTerritory()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear(),
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 100f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 200f ) );
	}

	// -- setters -----------------------------------------------------------

	@Test
	public void testSettersUpdateSubsequentLookups()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );

		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 150f ) );

		final ContinuousColorScheme reversed = new ContinuousColorScheme( new int[] { BLUE, GREEN, RED } );
		wrapper.setColorScheme( reversed );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 150f ) ); // middle stop either way

		final PresetFunc shifted = new LinearPresetFunc( 0f, 100f, 2f );
		wrapper.setPresetFunc( shifted );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 50f ) ); // new domain's midpoint

		wrapper.setLeftBoundaryCondition( BoundaryCondition.CYCLE );
		wrapper.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getRightBoundaryCondition() );
	}

	@Test
	public void testSetColorSchemeRejectsMismatchedDelkaIntervalu()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );
		final ContinuousColorScheme twoStops = new ContinuousColorScheme( new int[] { RED, GREEN } ); // delkaIntervalu 1, not 2
		try
		{
			wrapper.setColorScheme( twoStops );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		// Rejected setColorScheme must not have changed the previous value.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 150f ) );
	}

	@Test
	public void testSetPresetFuncRejectsMismatchedDelkaIntervalu()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );
		final PresetFunc mismatched = new LinearPresetFunc( 100f, 200f, 5f );
		try
		{
			wrapper.setPresetFunc( mismatched );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testSettersRejectNullArguments()
	{
		final ContinuousPaletteWrapper wrapper = new ContinuousPaletteWrapper( threeStops(), linear() );
		try
		{
			wrapper.setColorScheme( null );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			wrapper.setPresetFunc( null );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			wrapper.setLeftBoundaryCondition( null );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			wrapper.setRightBoundaryCondition( null );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
	}
}
