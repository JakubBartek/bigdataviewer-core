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

	private static DiscreteColorScheme threeFixes()
	{
		return new DiscreteColorScheme( new int[] { RED, GREEN, BLUE_HALF_ALPHA } );
	}

	@Test
	public void testRangeEqualsFixCount()
	{
		Assert.assertEquals( 3, threeFixes().getRange() );
		Assert.assertEquals( 2, new DiscreteColorScheme( new int[] { RED, GREEN } ).getRange() );
		Assert.assertEquals( 10, new DiscreteColorScheme( new int[ 10 ] ).getRange() );
	}

	@Test
	public void testConstructorRejectsFewerThanTwoFixes()
	{
		try
		{
			new DiscreteColorScheme( new int[] { RED } );
			Assert.fail( "expected IllegalArgumentException for a single color fix" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			new DiscreteColorScheme( new int[ 0 ] );
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
		final DiscreteColorScheme scheme = threeFixes();

		// -0.001 is outside [0, 3) -- clamps to the first fix, not an error.
		Assert.assertEquals( RED, scheme.getRGBA( -0.001f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0f ) );
		Assert.assertEquals( RED, scheme.getRGBA( 0.99f ) );
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 2.99f ) );
		// 3.0 is outside [0, 3) -- clamps to the last fix.
		Assert.assertEquals( BLUE_HALF_ALPHA, scheme.getRGBA( 3.0f ) );
	}

	@Test
	public void testMiddleFixOwnsItsWholeUnitSlot()
	{
		final DiscreteColorScheme scheme = threeFixes();
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.0f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.5f ) );
		Assert.assertEquals( GREEN, scheme.getRGBA( 1.999f ) );
	}

	@Test
	public void testGetRGBAKeepsFixesOwnAlpha()
	{
		Assert.assertEquals( 128, ARGBType.alpha( threeFixes().getRGBA( 2.5f ) ) );
	}

	@Test
	public void testGetRGBForcesFullOpacityRegardlessOfFixAlpha()
	{
		final int rgb = threeFixes().getRGB( 2.5f );
		Assert.assertEquals( 255, ARGBType.alpha( rgb ) );
		// ...but the color channels underneath are unaffected.
		Assert.assertEquals( ARGBType.red( BLUE_HALF_ALPHA ), ARGBType.red( rgb ) );
		Assert.assertEquals( ARGBType.green( BLUE_HALF_ALPHA ), ARGBType.green( rgb ) );
		Assert.assertEquals( ARGBType.blue( BLUE_HALF_ALPHA ), ARGBType.blue( rgb ) );
	}

	@Test
	public void testGetRGBAndGetRGBAAgreeOnFullyOpaqueFixes()
	{
		final DiscreteColorScheme scheme = threeFixes();
		Assert.assertEquals( scheme.getRGBA( 0f ), scheme.getRGB( 0f ) );
		Assert.assertEquals( scheme.getRGBA( 1.2f ), scheme.getRGB( 1.2f ) );
	}

	@Test
	public void testReproducesEveryFixExactlyWithinItsSlot()
	{
		final IColorScheme tab10 = ColorSchemeFactory.loadFromJson( "tab10" );
		Assert.assertTrue( tab10 instanceof DiscreteColorScheme );

		Assert.assertEquals( tab10.getFixCount(), tab10.getRange() );
		for ( int i = 0; i < tab10.getFixCount(); i++ )
			Assert.assertEquals( "fix " + i, tab10.getFix( i ), tab10.getRGBA( i + 0.5 ) );
	}

	/** {@code ColorSchemeFactory#findName} relies on this. */
	@Test
	public void testEqualityIsByValue()
	{
		final DiscreteColorScheme a = new DiscreteColorScheme( new int[] { 1, 2, 3 } );

		Assert.assertEquals( a, new DiscreteColorScheme( new int[] { 1, 2, 3 } ) );
		Assert.assertEquals( a.hashCode(), new DiscreteColorScheme( new int[] { 1, 2, 3 } ).hashCode() );
		Assert.assertNotEquals( a, new DiscreteColorScheme( new int[] { 1, 2, 4 } ) );
	}
}
