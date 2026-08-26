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
 * Test cases for {@link ContinuousColorScheme}.
 */
public class ContinuousColorSchemeTest
{
	private static final int RED = ARGBType.rgba( 255, 0, 0, 255 );

	private static final int GREEN = ARGBType.rgba( 0, 255, 0, 255 );

	/** Alpha deliberately not 255, so getRGB (forces opaque) and getRGBA (keeps it) can be told apart. */
	private static final int BLUE_HALF_ALPHA = ARGBType.rgba( 0, 0, 255, 128 );

	private static ContinuousColorScheme threeStops()
	{
		return new ContinuousColorScheme( new int[] { RED, GREEN, BLUE_HALF_ALPHA } );
	}

	@Test
	public void testDelkaIntervaluEqualsStopCountMinusOne()
	{
		Assert.assertEquals( 2, threeStops().getDelkaIntervalu() );
		Assert.assertEquals( 1, new ContinuousColorScheme( new int[] { RED, GREEN } ).getDelkaIntervalu() );
		Assert.assertEquals( 9, new ContinuousColorScheme( new int[ 10 ] ).getDelkaIntervalu() );
	}

	@Test
	public void testConstructorRejectsFewerThanTwoStops()
	{
		try
		{
			new ContinuousColorScheme( new int[] { RED } );
			Assert.fail( "expected IllegalArgumentException for a single color stop" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			new ContinuousColorScheme( new int[ 0 ] );
			Assert.fail( "expected IllegalArgumentException for zero color stops" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	/**
	 * Domain is the closed interval [0, N - 1]. Exactly the N = 3 examples
	 * from the requirements, plus the exact-integer stops in between.
	 */
	@Test
	public void testDomainBoundariesForThreeStops()
	{
		final ContinuousColorScheme scheme = threeStops();

		// -0.001 is outside [0, 2] -- clamps to the first stop, not an error.
		Assert.assertEquals( RED, scheme.getRGBA( -0.001f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1f ) );
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.0f ) );
		// 2.01 is outside [0, 2] -- clamps to the last stop.
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.01f ) );
	}

	@Test
	public void testInterpolatesLinearlyBetweenNeighboringStops()
	{
		final ContinuousColorScheme scheme = threeStops();

		// Halfway from red (255,0,0) to green (0,255,0): Math.round(127.5) == 128 for both channels.
		final int mid = scheme.getRGBA( 0.5f );
		Assert.assertEquals( 128, ARGBType.red( mid ) );
		Assert.assertEquals( 128, ARGBType.green( mid ) );
		Assert.assertEquals( 0, ARGBType.blue( mid ) );
		Assert.assertEquals( 255, ARGBType.alpha( mid ) );

		// 0.99 is 99% of the way from stop 0 (red) to stop 1 (green):
		// red = round(255 - 0.99*255) = round(2.55) = 3, green = round(0.99*255) = round(252.45) = 252.
		final int almostGreen = scheme.getRGBA( 0.99f );
		Assert.assertEquals( 3, ARGBType.red( almostGreen ) );
		Assert.assertEquals( 252, ARGBType.green( almostGreen ) );
	}

	@Test
	public void testInterpolatesAlphaChannelToo()
	{
		// Halfway from stop 1 (green, alpha 255) to stop 2 (blue, alpha 128):
		// round(255 + 0.5*(128-255)) = round(191.5) = 192.
		final int mid = threeStops().getRGBA( 1.5f );
		Assert.assertEquals( 192, ARGBType.alpha( mid ) );
	}

	@Test
	public void testGetRGBForcesFullOpacityRegardlessOfStopAlpha()
	{
		final int rgb = threeStops().getRGB( 2.0f );
		Assert.assertEquals( 255, ARGBType.alpha( rgb ) );
		Assert.assertEquals( ARGBType.blue( BLUE_HALF_ALPHA ), ARGBType.blue( rgb ) );
	}

	@Test
	public void testGetRGBAndGetRGBAAgreeOnFullyOpaqueStops()
	{
		final ContinuousColorScheme scheme = threeStops();
		Assert.assertEquals( scheme.getRGBA( 0f ), scheme.getRGB( 0f ) );
		Assert.assertEquals( scheme.getRGBA( 0.3f ), scheme.getRGB( 0.3f ) );
	}

	/**
	 * Reuses an actual bundled palette instead of a hand-built stop array,
	 * exercising the {@link ContinuousColorScheme#ContinuousColorScheme(ColorTable)}
	 * constructor. viridis is a real 256-stop continuous palette -- a natural
	 * fit for a continuous scheme.
	 */
	@Test
	public void testConstructFromExistingColorTablePalette()
	{
		final ColorTable viridis = LutPalettes.load( "viridis" );
		Assert.assertNotNull( viridis );

		final ContinuousColorScheme scheme = new ContinuousColorScheme( viridis );

		Assert.assertEquals( viridis.getLength() - 1, scheme.getDelkaIntervalu() );

		// Exact stops must reproduce the source table exactly, at both ends
		// and in the middle.
		for ( final int i : new int[] { 0, viridis.getLength() / 2, viridis.getLength() - 1 } )
		{
			final int expected = ARGBType.rgba(
					viridis.get( ColorTable.RED, i ), viridis.get( ColorTable.GREEN, i ),
					viridis.get( ColorTable.BLUE, i ), viridis.get( ColorTable.ALPHA, i ) );
			Assert.assertEquals( "stop " + i, expected, scheme.getRGBA( i ) );
		}
	}
}
