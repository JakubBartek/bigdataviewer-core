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

import net.imglib2.display.ColorTable8;
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

	private static ContinuousColorScheme threeFixes()
	{
		return new ContinuousColorScheme( new int[] { RED, GREEN, BLUE_HALF_ALPHA } );
	}

	@Test
	public void testRangeEqualsFixCountMinusOne()
	{
		Assert.assertEquals( 2, threeFixes().getRange() );
		Assert.assertEquals( 1, new ContinuousColorScheme( new int[] { RED, GREEN } ).getRange() );
		Assert.assertEquals( 9, new ContinuousColorScheme( new int[ 10 ] ).getRange() );
	}

	@Test
	public void testConstructorRejectsFewerThanTwoFixes()
	{
		try
		{
			new ContinuousColorScheme( new int[] { RED } );
			Assert.fail( "expected IllegalArgumentException for a single color fix" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			new ContinuousColorScheme( new int[ 0 ] );
			Assert.fail( "expected IllegalArgumentException for zero color fixes" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	/** The N = 3 examples from the requirements. */
	@Test
	public void testDomainBoundariesForThreeFixes()
	{
		final ContinuousColorScheme scheme = threeFixes();

		// -0.001 is outside [0, 2] -- clamps to the first fix, not an error.
		Assert.assertEquals( RED, scheme.getRGBA( -0.001f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1f ) );
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.0f ) );
		// 2.01 is outside [0, 2] -- clamps to the last fix.
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.01f ) );
	}

	@Test
	public void testInterpolatesLinearlyBetweenNeighboringFixes()
	{
		final ContinuousColorScheme scheme = threeFixes();

		// Halfway from red (255,0,0) to green (0,255,0): Math.round(127.5) == 128 for both channels.
		final int mid = scheme.getRGBA( 0.5f );
		Assert.assertEquals( 128, ARGBType.red( mid ) );
		Assert.assertEquals( 128, ARGBType.green( mid ) );
		Assert.assertEquals( 0, ARGBType.blue( mid ) );
		Assert.assertEquals( 255, ARGBType.alpha( mid ) );

		// 0.99 is 99% of the way from fix 0 (red) to fix 1 (green):
		// red = round(255 - 0.99*255) = round(2.55) = 3, green = round(0.99*255) = round(252.45) = 252.
		final int almostGreen = scheme.getRGBA( 0.99f );
		Assert.assertEquals( 3, ARGBType.red( almostGreen ) );
		Assert.assertEquals( 252, ARGBType.green( almostGreen ) );
	}

	@Test
	public void testInterpolatesAlphaChannelToo()
	{
		// Halfway from fix 1 (green, alpha 255) to fix 2 (blue, alpha 128):
		// round(255 + 0.5*(128-255)) = round(191.5) = 192.
		final int mid = threeFixes().getRGBA( 1.5f );
		Assert.assertEquals( 192, ARGBType.alpha( mid ) );
	}

	@Test
	public void testGetRGBForcesFullOpacityRegardlessOfFixAlpha()
	{
		final int rgb = threeFixes().getRGB( 2.0f );
		Assert.assertEquals( 255, ARGBType.alpha( rgb ) );
		Assert.assertEquals( ARGBType.blue( BLUE_HALF_ALPHA ), ARGBType.blue( rgb ) );
	}

	@Test
	public void testGetRGBAndGetRGBAAgreeOnFullyOpaqueFixes()
	{
		final ContinuousColorScheme scheme = threeFixes();
		Assert.assertEquals( scheme.getRGBA( 0f ), scheme.getRGB( 0f ) );
		Assert.assertEquals( scheme.getRGBA( 0.3f ), scheme.getRGB( 0.3f ) );
	}

	@Test
	public void testReproducesEveryFixExactlyAtItsIndex()
	{
		final IColorScheme viridis = ColorSchemeFactory.load( "viridis" );
		Assert.assertTrue( viridis instanceof ContinuousColorScheme );

		for ( int i = 0; i < viridis.getFixCount(); i++ )
			Assert.assertEquals( "fix " + i, viridis.getFix( i ), viridis.getRGBA( i ) );
	}

	// -- fixes (shared with DiscreteColorScheme) ------------------------------

	@Test
	public void testKeepsFixesInOrder()
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( new int[] { RED, GREEN } );

		Assert.assertEquals( 2, scheme.getFixCount() );
		Assert.assertEquals( RED, scheme.getFix( 0 ) );
		Assert.assertEquals( GREEN, scheme.getFix( 1 ) );
		Assert.assertArrayEquals( new int[] { RED, GREEN }, scheme.getFixes() );
	}

	/** {@code ColorSchemeFactory#findName} shares cached instances. */
	@Test
	public void testIsImmutable()
	{
		final int[] source = { RED, GREEN };
		final ContinuousColorScheme scheme = new ContinuousColorScheme( source );

		source[ 0 ] = GREEN;
		Assert.assertEquals( RED, scheme.getFix( 0 ) );

		scheme.getFixes()[ 1 ] = RED;
		Assert.assertEquals( GREEN, scheme.getFix( 1 ) );
	}

	@Test
	public void testDefaultIsABlackToWhiteRamp()
	{
		Assert.assertArrayEquals( new int[] { 0xff000000, 0xffffffff }, ContinuousColorScheme.DEFAULT.getFixes() );
	}

	// -- equality ------------------------------------------------------------

	/** {@code ColorSchemeFactory#findName} relies on this. */
	@Test
	public void testEqualityIsByValue()
	{
		final ContinuousColorScheme a = new ContinuousColorScheme( new int[] { RED, GREEN } );
		final ContinuousColorScheme b = new ContinuousColorScheme( new int[] { RED, GREEN } );

		Assert.assertEquals( a, b );
		Assert.assertEquals( a.hashCode(), b.hashCode() );
		Assert.assertNotEquals( a, new ContinuousColorScheme( new int[] { GREEN, RED } ) );
		Assert.assertNotEquals( a, new ContinuousColorScheme( new int[] { RED, GREEN, RED } ) );
	}

	@Test
	public void testNeverEqualsADiscreteSchemeWithTheSameFixes()
	{
		final ContinuousColorScheme continuous = new ContinuousColorScheme( new int[] { RED, GREEN } );
		final DiscreteColorScheme discrete = new DiscreteColorScheme( new int[] { RED, GREEN } );

		Assert.assertNotEquals( continuous, discrete );
		Assert.assertNotEquals( discrete, continuous );
	}

	// -- adapting a foreign ColorTable ---------------------------------------

	/** {@link ColorTable8} has no ALPHA component. */
	@Test
	public void testOfColorTableWithoutAlphaComponentIsOpaque()
	{
		final ColorTable8 grayscale = new ColorTable8();
		Assert.assertEquals( 3, grayscale.getComponentCount() );

		final ContinuousColorScheme scheme = ContinuousColorScheme.of( grayscale );

		Assert.assertEquals( 256, scheme.getFixCount() );
		Assert.assertEquals( 0xff000000, scheme.getFix( 0 ) );
		Assert.assertEquals( 0xffffffff, scheme.getFix( 255 ) );
		Assert.assertEquals( 255, ARGBType.alpha( scheme.getFix( 128 ) ) );
	}

	@Test
	public void testOfColorTableKeepsEntryOrder()
	{
		final ColorTable8 table = new ColorTable8(
				new byte[] { ( byte ) 255, 0 },
				new byte[] { 0, ( byte ) 255 },
				new byte[] { 0, 0 } );

		Assert.assertArrayEquals( new int[] { RED, GREEN }, ContinuousColorScheme.of( table ).getFixes() );
	}
}
