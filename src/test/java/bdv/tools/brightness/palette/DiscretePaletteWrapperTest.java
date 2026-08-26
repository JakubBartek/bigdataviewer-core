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

import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link DiscretePaletteWrapper}.
 */
public class DiscretePaletteWrapperTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	private static final int BLUE = ARGBType.rgba( 0, 0, 255, 255 );

	/** 3 stops, so the wrapped scheme's domain is [0, 3). */
	private static DiscreteColorScheme threeStops()
	{
		return new DiscreteColorScheme( new int[] { RED, GREEN, BLUE } );
	}

	// -- construction --------------------------------------------------------

	@Test
	public void testConstructorRejectsNonPositiveStepSize()
	{
		for ( final float badStep : new float[] { 0f, -1f, -0.0001f, Float.NaN } )
		{
			try
			{
				new DiscretePaletteWrapper( threeStops(), 0, badStep );
				Assert.fail( "expected IllegalArgumentException for stepSize=" + badStep );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}

	@Test
	public void testConstructorRejectsNullArguments()
	{
		try
		{
			new DiscretePaletteWrapper( null, 0, 1 );
			Assert.fail( "expected NullPointerException for a null color scheme" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			new DiscretePaletteWrapper( threeStops(), 0, 1, null, BoundaryCondition.CLAMP );
			Assert.fail( "expected NullPointerException for a null left boundary condition" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			new DiscretePaletteWrapper( threeStops(), 0, 1, BoundaryCondition.CLAMP, null );
			Assert.fail( "expected NullPointerException for a null right boundary condition" );
		}
		catch ( final NullPointerException expected )
		{
		}
	}

	@Test
	public void testThreeArgConstructorDefaultsBothBoundaryConditionsToClamp()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0, 1 );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CLAMP, wrapper.getRightBoundaryCondition() );
	}

	@Test
	public void testGettersReturnConstructorArguments()
	{
		final DiscreteColorScheme scheme = threeStops();
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( scheme, 10f, 5f, BoundaryCondition.CLAMP, BoundaryCondition.CLAMP );

		Assert.assertSame( scheme, wrapper.getColorScheme() );
		Assert.assertEquals( 10f, wrapper.getMin(), 0f );
		Assert.assertEquals( 5f, wrapper.getStepSize(), 0f );
	}

	// -- raw -> palette value mapping -----------------------------------------

	/**
	 * min=10, stepSize=5: paletteValue = (rawValue - 10) / 5. Covers "values
	 * inside the range", "exactly min", "values below min" and "values above
	 * the maximum" all through one non-trivial min/stepSize combination.
	 */
	@Test
	public void testRawToPaletteValueMapping()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 10f, 5f );

		// exactly min -> paletteValue 0 -> first stop
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 10f ) );
		// inside range: raw=12 -> paletteValue=0.4 -> still stop 0
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 12f ) );
		// inside range: raw=17 -> paletteValue=1.4 -> stop 1
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 17f ) );
		// inside range: raw=24 -> paletteValue=2.8 -> stop 2 (last valid slot)
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 24f ) );

		// below min: raw=9 -> paletteValue=-0.2 -> left boundary -> clamps to stop 0
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 9f ) );
		// further below min
		Assert.assertEquals( RED, wrapper.getRGBForRaw( -100f ) );

		// above the maximum: raw=25 -> paletteValue=3.0 -> right boundary -> clamps to stop 2
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 25f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 1000f ) );
	}

	/**
	 * Same min, two different step sizes: the raw value at which the mapping
	 * crosses from stop 0 into stop 1 must scale with stepSize, since a
	 * larger step means each stop covers a wider span of raw values.
	 */
	@Test
	public void testDifferentStepSizesScaleTheRawDomainPerStop()
	{
		final DiscretePaletteWrapper fineStep = new DiscretePaletteWrapper( threeStops(), 0f, 1f );
		final DiscretePaletteWrapper coarseStep = new DiscretePaletteWrapper( threeStops(), 0f, 10f );

		// raw=1.5: paletteValue=1.5 (fine) vs 0.15 (coarse) -> different stops.
		Assert.assertEquals( GREEN, fineStep.getRGBForRaw( 1.5f ) );
		Assert.assertEquals( RED, coarseStep.getRGBForRaw( 1.5f ) );

		// coarseStep needs a much larger raw value to reach the same stop.
		Assert.assertEquals( GREEN, coarseStep.getRGBForRaw( 15f ) );

		// Both must agree exactly at their own min (palette value 0 either way).
		Assert.assertEquals( RED, fineStep.getRGBForRaw( 0f ) );
		Assert.assertEquals( RED, coarseStep.getRGBForRaw( 0f ) );
	}

	/**
	 * min=100, stepSize=1: a raw value below min by any amount hits the left
	 * boundary condition and clamps to the first stop.
	 */
	@Test
	public void testLeftBoundaryBehavior()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 100f, 1f );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 99.999f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 99f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( Float.NEGATIVE_INFINITY ) );

		// Just past the boundary is no longer "left boundary" territory.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 100f ) );
	}

	/**
	 * min=0, stepSize=1, 3 stops: a raw value at or above the domain length
	 * (3) hits the right boundary condition and clamps to the last stop.
	 */
	@Test
	public void testRightBoundaryBehavior()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 3f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 3.001f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 1000f ) );
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( Float.POSITIVE_INFINITY ) );

		// Just before the boundary is still legitimately the last stop, not
		// boundary-clamped -- same visible result, different code path.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.999f ) );
	}

	/**
	 * min=0, stepSize=1 makes paletteValue == rawValue exactly, so this
	 * reproduces the domain examples from the color-scheme requirements
	 * (N = 3: [0, 3) valid) through the wrapper end to end.
	 */
	@Test
	public void testExactZeroToNDiscreteDomainThroughIdentityMapping()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -0.001f ) );  // invalid -> edge color
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );        // valid
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0.99f ) );     // valid
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.99f ) );    // valid
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 3.0f ) );     // invalid -> edge color
	}

	@Test
	public void testGetRGBForRawDelegatesToColorSchemesGetRGBNotGetRGBA()
	{
		// A stop with non-opaque alpha: getRGBForRaw must come back fully
		// opaque, matching DiscreteColorScheme#getRGB (not #getRGBA).
		final int translucentRed = ARGBType.rgba( 255, 0, 0, 100 );
		final DiscreteColorScheme scheme = new DiscreteColorScheme( new int[] { translucentRed, GREEN } );
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( scheme, 0f, 1f );

		Assert.assertEquals( 255, ARGBType.alpha( wrapper.getRGBForRaw( 0f ) ) );
	}

	// -- CYCLE -----------------------------------------------------------

	/**
	 * CYCLE wraps a left-boundary value around to the far (right) side of the
	 * domain instead of collapsing onto the first stop -- the opposite of
	 * CLAMP's behavior for the same inputs (see {@link #testLeftBoundaryBehavior()}).
	 */
	@Test
	public void testCycleWrapsLeftBoundaryToTheOppositeEnd()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		// paletteValue -0.5 must wrap to 2.5 (floorMod(-0.5, 3) == 2.5), stop 2.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -0.5f ) );
		// paletteValue -1.0 -> floorMod(-1, 3) == 2 exactly -> stop 2.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -1f ) );
		// paletteValue -3.5 (more than one full cycle below) -> floorMod(-3.5, 3) == 2.5 -> stop 2, same as -0.5.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -3.5f ) );
	}

	/**
	 * CYCLE wraps a right-boundary value back around to the start of the
	 * domain.
	 */
	@Test
	public void testCycleWrapsRightBoundaryToTheStart()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.CYCLE, BoundaryCondition.CYCLE );

		// paletteValue 3.0 -> floorMod(3, 3) == 0 -> stop 0.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3f ) );
		// paletteValue 3.5 -> floorMod(3.5, 3) == 0.5 -> still stop 0.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3.5f ) );
		// paletteValue 4.5 -> floorMod(4.5, 3) == 1.5 -> stop 1.
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 4.5f ) );
		// Many full cycles above: paletteValue 9.2 -> floorMod(9.2, 3) == 0.2 -> stop 0.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 9.2f ) );
	}

	/** Left and right can independently be CYCLE vs. something else. */
	@Test
	public void testCycleCanBeSetOnOnlyOneSide()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.CYCLE, BoundaryCondition.CLAMP );

		// Left: cycles around to the last stop.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -1f ) );
		// Right: still plain clamp.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 100f ) );
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
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.SPECIAL, BoundaryCondition.CLAMP );
		wrapper.setLeftSpecialValue( 1f ); // stop 1 (green), not the natural left edge (stop 0)

		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( -1f ) );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( -100f ) );
		// Inside the domain is unaffected by the special value.
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 0f ) );
	}

	@Test
	public void testSpecialUsesTheConfiguredPaletteValueOnTheRight()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.CLAMP, BoundaryCondition.SPECIAL );
		wrapper.setRightSpecialValue( 0f ); // stop 0 (red), not the natural right edge (stop 2)

		Assert.assertEquals( RED, wrapper.getRGBForRaw( 3f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 1000f ) );
		// Inside the domain is unaffected by the special value.
		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( 2.5f ) );
	}

	/**
	 * The default special value (never set) is 0 -- same as Java's default
	 * float field value -- so it must resolve to stop 0 without needing to
	 * be configured first.
	 */
	@Test
	public void testSpecialDefaultsToPaletteValueZero()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.SPECIAL, BoundaryCondition.SPECIAL );

		Assert.assertEquals( RED, wrapper.getRGBForRaw( -5f ) );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 5f ) );
	}

	/**
	 * A left/right special value that is itself outside the color scheme's
	 * domain is not an error: it just clamps like any other out-of-domain
	 * palette value passed to the color scheme.
	 */
	@Test
	public void testOutOfDomainSpecialValueStillClampsInTheColorScheme()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f,
				BoundaryCondition.SPECIAL, BoundaryCondition.CLAMP );
		wrapper.setLeftSpecialValue( 999f );

		Assert.assertEquals( BLUE, wrapper.getRGBForRaw( -1f ) );
	}

	// -- setters -----------------------------------------------------------

	@Test
	public void testSettersUpdateSubsequentLookups()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f );

		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 1f ) );

		wrapper.setMin( 10f );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 11f ) );

		wrapper.setStepSize( 2f );
		Assert.assertEquals( GREEN, wrapper.getRGBForRaw( 12f ) );

		final DiscreteColorScheme reversed = new DiscreteColorScheme( new int[] { BLUE, GREEN, RED } );
		wrapper.setColorScheme( reversed );
		Assert.assertEquals( RED, wrapper.getRGBForRaw( 14f ) ); // now stop 2 of the new scheme

		wrapper.setLeftBoundaryCondition( BoundaryCondition.CYCLE );
		wrapper.setRightBoundaryCondition( BoundaryCondition.CYCLE );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, wrapper.getRightBoundaryCondition() );
	}

	@Test
	public void testSetStepSizeRejectsNonPositiveValues()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f );
		for ( final float badStep : new float[] { 0f, -1f, Float.NaN } )
		{
			try
			{
				wrapper.setStepSize( badStep );
				Assert.fail( "expected IllegalArgumentException for stepSize=" + badStep );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
		// Rejected setStepSize calls must not have changed the previous value.
		Assert.assertEquals( 1f, wrapper.getStepSize(), 0f );
	}

	@Test
	public void testSettersRejectNullArguments()
	{
		final DiscretePaletteWrapper wrapper = new DiscretePaletteWrapper( threeStops(), 0f, 1f );
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
