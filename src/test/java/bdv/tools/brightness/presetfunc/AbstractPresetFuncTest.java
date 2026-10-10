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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** Behavior every {@link IPresetFunc} shares; per-class tests cover the shapes. */
public class AbstractPresetFuncTest
{
	/** Mirrors every concrete {@link IPresetFunc} constructor, so a test can build any of them from one fixture. */
	@FunctionalInterface
	private interface IPresetFuncFactory
	{
		IPresetFunc create( double min, double max, int schemeRange );
	}

	/** All but {@link StepPresetFunc}, whose step size survives a range change. */
	private static final List< IPresetFuncFactory > RANGE_STRETCHING_CONSTRUCTORS = Arrays.asList(
			LinearPresetFunc::new,
			LogPresetFunc::new,
			ExpPresetFunc::new,
			SigmoidPresetFunc::new,
			AlphaSigmoidPresetFunc::new,
			TanPresetFunc::new,
			AtanPresetFunc::new,
			CustomInterpPresetFunc::new );

	/** {@link StepPresetFunc} at its default step size is a plain single pass. */
	private static final List< IPresetFuncFactory > ALL_CONSTRUCTORS = concat( RANGE_STRETCHING_CONSTRUCTORS,
			( min, max, n ) -> new StepPresetFunc( min, n, StepPresetFunc.defaultStepSize( min, max, n ) ) );

	private static List< IPresetFuncFactory > concat( final List< IPresetFuncFactory > factories, final IPresetFuncFactory extra )
	{
		final List< IPresetFuncFactory > all = new ArrayList<>( factories );
		all.add( extra );
		return all;
	}

	/** The shared fixture: raw [100, 200] onto scheme values [0, 10]. */
	private static IPresetFunc build( final IPresetFuncFactory factory )
	{
		return factory.create( 100f, 200f, 10 );
	}

	@Test
	public void testGettersReturnConstructorArguments()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final IPresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 100f, f.getMin(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 200f, f.getMax(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10, f.getSchemeRange() );
		}
	}

	/** {@link CustomInterpPresetFunc} only guarantees this for its default knots. */
	@Test
	public void testEveryShapeReachesExactlyZeroAndSchemeRangeAtTheEnds()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final IPresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getSchemeValueForRaw( 100f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getSchemeValueForRaw( 200f ), 1e-4f );
		}
	}

	/** Values outside [min, max] are not an error: they clamp to the nearest end, same as {@code IColorScheme} clamps an out-of-domain scheme value. */
	@Test
	public void testOutOfRangeRawValuesClampToTheNearestEnd()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final IPresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getSchemeValueForRaw( 0f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getSchemeValueForRaw( -1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getSchemeValueForRaw( 1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getSchemeValueForRaw( Float.POSITIVE_INFINITY ), 1e-4f );
		}
	}

	/** Every implementation rejects an empty or inverted raw range, not just the one spot-checked below. */
	@Test
	public void testConstructorRejectsMaxNotGreaterThanMin()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			for ( final float[] badRange : new float[][] { { 5f, 5f }, { 5f, 4f }, { 5f, Float.NaN } } )
			{
				try
				{
					factory.create( badRange[ 0 ], badRange[ 1 ], 10 );
					Assert.fail( "expected IllegalArgumentException for min=" + badRange[ 0 ] + ", max=" + badRange[ 1 ] );
				}
				catch ( final IllegalArgumentException expected )
				{
				}
			}
		}
	}

	@Test
	public void testConstructorRejectsNonPositiveSchemeRange()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			for ( final int bad : new int[] { 0, -1 } )
			{
				try
				{
					factory.create( 0f, 1f, bad );
					Assert.fail( "expected IllegalArgumentException for schemeRange=" + bad );
				}
				catch ( final IllegalArgumentException expected )
				{
				}
			}
		}
	}

	/** Stretched, not distorted. */
	@Test
	public void testWithRangeStretchesTheSameShapeOntoTheNewEndpoints()
	{
		for ( final IPresetFuncFactory factory : RANGE_STRETCHING_CONSTRUCTORS )
		{
			final IPresetFunc original = build( factory ); // raw [100, 200] -> [0, 10]
			final IPresetFunc reranged = original.withRange( 300f, 500f );
			final String name = factory.getClass().getSimpleName();

			Assert.assertEquals( name, 300f, reranged.getMin(), 0f );
			Assert.assertEquals( name, 500f, reranged.getMax(), 0f );
			Assert.assertEquals( name, original.getSchemeRange(), reranged.getSchemeRange() );

			// The 25%/50%/75% points of each range must produce the same scheme value.
			Assert.assertEquals( name, original.getSchemeValueForRaw( 125f ), reranged.getSchemeValueForRaw( 350f ), 1e-4f );
			Assert.assertEquals( name, original.getSchemeValueForRaw( 150f ), reranged.getSchemeValueForRaw( 400f ), 1e-4f );
			Assert.assertEquals( name, original.getSchemeValueForRaw( 175f ), reranged.getSchemeValueForRaw( 450f ), 1e-4f );
		}
	}

	/** {@code withRange} keeps the shape independent of the endpoints, so re-ranging must not mutate the original. */
	@Test
	public void testWithRangeDoesNotMutateTheOriginal()
	{
		for ( final IPresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final IPresetFunc original = build( factory );
			original.withRange( 300f, 500f );
			Assert.assertEquals( factory.getClass().getSimpleName(), 100f, original.getMin(), 0f );
			Assert.assertEquals( factory.getClass().getSimpleName(), 200f, original.getMax(), 0f );
		}
	}
}
