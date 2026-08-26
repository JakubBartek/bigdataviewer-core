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
package bdv.tools.brightness.colorscheme;

import org.junit.Assert;
import org.junit.Test;

import bdv.tools.brightness.LutPalettes;
import net.imglib2.display.ColorTable;
import net.imglib2.type.numeric.ARGBType;

/**
 * Test cases for {@link DiscreteColorScheme}.
 */
public class DiscreteColorSchemeTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	/** Alpha deliberately not 255, so getRGB (forces opaque) and getRGBA (keeps it) can be told apart. */
	private static final int BLUE_HALF_ALPHA = ARGBType.rgba( 0, 0, 255, 128 );

	private static DiscreteColorScheme threeStops()
	{
		return new DiscreteColorScheme( new int[] { RED, GREEN, BLUE_HALF_ALPHA } );
	}

	@Test
	public void testPaletteRangeLengthEqualsStopCount()
	{
		Assert.assertEquals( 3, threeStops().getPaletteRangeLength() );
		Assert.assertEquals( 2, new DiscreteColorScheme( new int[] { RED, GREEN } ).getPaletteRangeLength() );
		Assert.assertEquals( 10, new DiscreteColorScheme( new int[ 10 ] ).getPaletteRangeLength() );
	}

	@Test
	public void testConstructorRejectsFewerThanTwoStops()
	{
		try
		{
			new DiscreteColorScheme( new int[] { RED } );
			Assert.fail( "expected IllegalArgumentException for a single color stop" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			new DiscreteColorScheme( new int[ 0 ] );
			Assert.fail( "expected IllegalArgumentException for zero color stops" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	/**
	 * Domain is the half-open interval [0, N); a value truncates (floors) to
	 * whichever stop's unit-wide slot it falls into. Exactly the N = 3
	 * examples from the requirements.
	 */
	@Test
	public void testDomainBoundariesForThreeStops()
	{
		final DiscreteColorScheme scheme = threeStops();

		// -0.001 is outside [0, 3) -- clamps to the first stop, not an error.
		Assert.assertEquals( RED, scheme.getRGBA( -0.001f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0.99f ) );
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.99f ) );
		// 3.0 is outside [0, 3) -- clamps to the last stop.
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 3.0f ) );
	}

	@Test
	public void testMiddleStopOwnsItsWholeUnitSlot()
	{
		final DiscreteColorScheme scheme = threeStops();
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.0f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.5f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.999f ) );
	}

	@Test
	public void testGetRGBAKeepsStopsOwnAlpha()
	{
		Assert.assertEquals( 128, ARGBType.alpha( threeStops().getRGBA( 2.5f ) ) );
	}

	@Test
	public void testGetRGBForcesFullOpacityRegardlessOfStopAlpha()
	{
		final int rgb = threeStops().getRGB( 2.5f );
		Assert.assertEquals( 255, ARGBType.alpha( rgb ) );
		// ...but the color channels underneath are unaffected.
		Assert.assertEquals( ARGBType.red( BLUE_HALF_ALPHA ), ARGBType.red( rgb ) );
		Assert.assertEquals( ARGBType.green( BLUE_HALF_ALPHA ), ARGBType.green( rgb ) );
		Assert.assertEquals( ARGBType.blue( BLUE_HALF_ALPHA ), ARGBType.blue( rgb ) );
	}

	@Test
	public void testGetRGBAndGetRGBAAgreeOnFullyOpaqueStops()
	{
		final DiscreteColorScheme scheme = threeStops();
		Assert.assertEquals( scheme.getRGBA( 0f ), scheme.getRGB( 0f ) );
		Assert.assertEquals( scheme.getRGBA( 1.2f ), scheme.getRGB( 1.2f ) );
	}

	/**
	 * Reuses an actual bundled palette (see {@code bdv.tools.brightness.LutPalettes})
	 * instead of a hand-built stop array, exercising the
	 * {@link DiscreteColorScheme#DiscreteColorScheme(ColorTable)} constructor.
	 * tab10 is a real 10-color qualitative palette -- a natural fit for a
	 * discrete scheme.
	 */
	@Test
	public void testConstructFromExistingColorTablePalette()
	{
		final ColorTable tab10 = LutPalettes.load( "tab10" );
		Assert.assertNotNull( tab10 );

		final DiscreteColorScheme scheme = new DiscreteColorScheme( tab10 );

		Assert.assertEquals( tab10.getLength(), scheme.getPaletteRangeLength() );
		for ( int i = 0; i < tab10.getLength(); i++ )
		{
			final int expected = ARGBType.rgba(
					tab10.get( ColorTable.RED, i ), tab10.get( ColorTable.GREEN, i ),
					tab10.get( ColorTable.BLUE, i ), tab10.get( ColorTable.ALPHA, i ) );
			// Anywhere within stop i's unit slot must reproduce that stop exactly.
			Assert.assertEquals( "stop " + i, expected, scheme.getRGBA( i + 0.5f ) );
		}
	}
}
