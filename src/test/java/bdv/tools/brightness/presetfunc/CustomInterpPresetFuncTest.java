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
package bdv.tools.brightness.presetfunc;

import org.junit.Assert;
import org.junit.Test;

/** Shared behavior is covered by {@link AbstractPresetFuncTest}; this covers knots. */
public class CustomInterpPresetFuncTest
{
	/** min=100, max=200, schemeRange=10, so raw 125/150/175 are t=0.25/0.5/0.75. */
	private static CustomInterpPresetFunc scaled()
	{
		return new CustomInterpPresetFunc( 100f, 200f, 10 );
	}

	@Test
	public void testDefaultKnotsAreLinear()
	{
		final CustomInterpPresetFunc f = scaled();
		Assert.assertEquals( 2.5f, f.getSchemeValueForRaw( 125f ), 1e-4f );
		Assert.assertEquals( 5.0f, f.getSchemeValueForRaw( 150f ), 1e-4f );
		Assert.assertEquals( 7.5f, f.getSchemeValueForRaw( 175f ), 1e-4f );
	}

	@Test
	public void testInteriorKnotBendsTheShape()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 0.5, 1.0 }, new double[] { 0.0, 0.8, 1.0 } );

		Assert.assertEquals( 4.0f, f.getSchemeValueForRaw( 125f ), 1e-4f ); // t=0.25 -> 0.4 * 10
		Assert.assertEquals( 8.0f, f.getSchemeValueForRaw( 150f ), 1e-4f ); // t=0.5 -> exactly the knot
		Assert.assertEquals( 9.0f, f.getSchemeValueForRaw( 175f ), 1e-4f ); // t=0.75 -> 0.9 * 10
	}

	/** Between two knots with equal values, the interpolated result must stay exactly flat. */
	@Test
	public void testPlateauBetweenEqualValuedKnotsStaysFlat()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 0.3, 0.7, 1.0 }, new double[] { 0.0, 0.5, 0.5, 1.0 } );

		// Anywhere strictly between the two 0.5-valued knots (t=0.3 and t=0.7) is exactly 5.0.
		Assert.assertEquals( 5.0f, f.getSchemeValueForRaw( 140f ), 1e-4f ); // t=0.4
		Assert.assertEquals( 5.0f, f.getSchemeValueForRaw( 150f ), 1e-4f ); // t=0.5
		Assert.assertEquals( 5.0f, f.getSchemeValueForRaw( 160f ), 1e-4f ); // t=0.6
	}

	/** Not rescaled to pin the endpoints; the outermost knot extends flat. */
	@Test
	public void testKnotsNotSpanningTheFullDomainAreNotStretchedToTheEdges()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.2, 0.8 }, new double[] { 0.3, 0.7 } );

		Assert.assertEquals( 3f, f.getSchemeValueForRaw( 100f ), 1e-4f );
		Assert.assertEquals( 7f, f.getSchemeValueForRaw( 200f ), 1e-4f );
	}

	/** Rescaling would divide by a negative span and flip it. */
	@Test
	public void testInvertedKnotsStayInverted()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 1.0 }, new double[] { 1.0, 0.0 } );

		Assert.assertEquals( 10f, f.getSchemeValueForRaw( 100f ), 1e-4f );
		Assert.assertEquals( 7.5f, f.getSchemeValueForRaw( 125f ), 1e-4f );
		Assert.assertEquals( 5f, f.getSchemeValueForRaw( 150f ), 1e-4f );
		Assert.assertEquals( 2.5f, f.getSchemeValueForRaw( 175f ), 1e-4f );
		Assert.assertEquals( 0f, f.getSchemeValueForRaw( 200f ), 1e-4f );
	}

	/** Rescaling would divide by zero. */
	@Test
	public void testFlatKnotsStayFlat()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 1.0 }, new double[] { 0.5, 0.5 } );

		for ( final double raw : new double[] { 100, 125, 150, 175, 200 } )
		{
			final double schemeValue = f.getSchemeValueForRaw( raw );
			Assert.assertFalse( "NaN at raw=" + raw, Double.isNaN( schemeValue ) );
			Assert.assertEquals( 5.0, schemeValue, 1e-4 );
		}
	}

	@Test
	public void testSetKnotsRejectsValuesOutsideTheUnitRange()
	{
		final CustomInterpPresetFunc f = scaled();
		for ( final double bad : new double[] { -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY } )
		{
			try
			{
				f.setKnots( new double[] { 0.0, 1.0 }, new double[] { 0.0, bad } );
				Assert.fail( "expected IllegalArgumentException for knot value " + bad );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
			try
			{
				f.setKnots( new double[] { 0.0, bad }, new double[] { 0.0, 1.0 } );
				Assert.fail( "expected IllegalArgumentException for knot t " + bad );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}

	/** Beyond the outermost knots, the shape stays flat rather than extrapolating a slope. */
	@Test
	public void testFlatBeyondTheOutermostKnots()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.2, 0.5, 0.8 }, new double[] { 0.3, 0.6, 0.9 } );

		// t=0 to t=0.2 is flat at the first knot's (rescaled) value, same as t=0.2 itself.
		Assert.assertEquals( f.getSchemeValueForRaw( 120f ), f.getSchemeValueForRaw( 110f ), 1e-4f );
		Assert.assertEquals( f.getSchemeValueForRaw( 120f ), f.getSchemeValueForRaw( 101f ), 1e-4f );
		// t=0.8 to t=1 is flat at the last knot's (rescaled) value.
		Assert.assertEquals( f.getSchemeValueForRaw( 180f ), f.getSchemeValueForRaw( 190f ), 1e-4f );
		Assert.assertEquals( f.getSchemeValueForRaw( 180f ), f.getSchemeValueForRaw( 199f ), 1e-4f );
	}

	@Test
	public void testGetKnotsReturnsWhatWasSet()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 0.4, 1.0 }, new double[] { 0.0, 0.9, 1.0 } );

		Assert.assertEquals( 3, f.getKnotCount() );
		Assert.assertArrayEquals( new double[] { 0.0, 0.4, 1.0 }, f.getKnotTs(), 1e-9 );
		Assert.assertArrayEquals( new double[] { 0.0, 0.9, 1.0 }, f.getKnotValues(), 1e-9 );
	}

	/** withRange must carry the custom knots over -- they are domain fractions, so a proportional raw value maps identically before and after re-ranging. */
	@Test
	public void testWithRangeCarriesKnotsOver()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 0.5, 1.0 }, new double[] { 0.0, 0.8, 1.0 } );

		final CustomInterpPresetFunc reranged = f.withRange( 300f, 500f );

		Assert.assertArrayEquals( f.getKnotTs(), reranged.getKnotTs(), 1e-9 );
		Assert.assertArrayEquals( f.getKnotValues(), reranged.getKnotValues(), 1e-9 );
		// t=0.5 of the new range (raw 400) hits the interior knot, same 8.0 as raw 150 did.
		Assert.assertEquals( 8.0f, reranged.getSchemeValueForRaw( 400f ), 1e-4f );
	}

	/** Mutating an array returned by the getters must not affect the function's actual state. */
	@Test
	public void testKnotGettersReturnDefensiveCopies()
	{
		final CustomInterpPresetFunc f = scaled();
		f.setKnots( new double[] { 0.0, 0.5, 1.0 }, new double[] { 0.0, 0.8, 1.0 } );

		f.getKnotTs()[ 1 ] = 999.0;
		f.getKnotValues()[ 1 ] = -999.0;

		Assert.assertEquals( 8.0f, f.getSchemeValueForRaw( 150f ), 1e-4f );
	}

	@Test
	public void testSetKnotsRejectsNullArguments()
	{
		final CustomInterpPresetFunc f = scaled();
		try
		{
			f.setKnots( null, new double[] { 0.0, 1.0 } );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
		try
		{
			f.setKnots( new double[] { 0.0, 1.0 }, null );
			Assert.fail( "expected NullPointerException" );
		}
		catch ( final NullPointerException expected )
		{
		}
	}

	@Test
	public void testSetKnotsRejectsMismatchedArrayLengths()
	{
		final CustomInterpPresetFunc f = scaled();
		try
		{
			f.setKnots( new double[] { 0.0, 0.5, 1.0 }, new double[] { 0.0, 1.0 } );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testSetKnotsRejectsFewerThanTwoKnots()
	{
		final CustomInterpPresetFunc f = scaled();
		try
		{
			f.setKnots( new double[] { 0.5 }, new double[] { 0.5 } );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			f.setKnots( new double[ 0 ], new double[ 0 ] );
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}

	@Test
	public void testSetKnotsRejectsNonAscendingTs()
	{
		final CustomInterpPresetFunc f = scaled();
		try
		{
			f.setKnots( new double[] { 0.0, 0.5, 0.5 }, new double[] { 0.0, 0.5, 1.0 } ); // equal, not strictly ascending
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
		try
		{
			f.setKnots( new double[] { 0.0, 0.6, 0.4 }, new double[] { 0.0, 0.5, 1.0 } ); // descending
			Assert.fail( "expected IllegalArgumentException" );
		}
		catch ( final IllegalArgumentException expected )
		{
		}
	}
}
