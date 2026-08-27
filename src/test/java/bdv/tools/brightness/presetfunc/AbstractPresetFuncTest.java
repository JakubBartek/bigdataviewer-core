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

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for behavior every {@link PresetFunc} implementation shares
 * (constructor validation, {@code getMin}/{@code getMax}/{@code getPaletteRangeLength},
 * and the endpoint guarantee), checked generically across all of them rather
 * than repeating it in each one's own test class -- their individual test
 * classes instead focus on what is actually distinctive about each: its shape
 * in between.
 */
public class AbstractPresetFuncTest
{
	/** Mirrors every concrete {@link PresetFunc} constructor, so a test can build any of them from one fixture. */
	@FunctionalInterface
	private interface PresetFuncFactory
	{
		PresetFunc create( float min, float max, int paletteRangeLength );
	}

	/** One constructor reference per implementation, so the tests below run against all of them without repeating themselves by hand. */
	private static final List< PresetFuncFactory > ALL_CONSTRUCTORS = Arrays.asList(
			LinearPresetFunc::new,
			PercentileStretchPresetFunc::new,
			LogPresetFunc::new,
			ExpPresetFunc::new,
			SigmoidPresetFunc::new,
			AlphaSigmoidPresetFunc::new,
			TanPresetFunc::new,
			AtanPresetFunc::new,
			CustomInterpPresetFunc::new );

	/** The shared fixture: raw [100, 200] onto palette values [0, 10]. */
	private static PresetFunc build( final PresetFuncFactory factory )
	{
		return factory.create( 100f, 200f, 10 );
	}

	@Test
	public void testGettersReturnConstructorArguments()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 100f, f.getMin(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 200f, f.getMax(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10, f.getPaletteRangeLength() );
		}
	}

	/**
	 * Each shape's two ends land exactly on palette value {@code 0} and
	 * {@code getPaletteRangeLength()} -- this is what lets a
	 * {@code ContinuousPaletteWrapper} rely on a {@link PresetFunc} feeding a
	 * continuous color scheme's full domain, regardless of which shape was
	 * chosen. {@link CustomInterpPresetFunc} only guarantees this for its
	 * default knots (which is what {@link #build} produces); see its own test
	 * class for why a user-defined curve is deliberately exempt.
	 */
	@Test
	public void testEveryShapeReachesExactlyZeroAndPaletteRangeLengthAtTheEnds()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( 100f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( 200f ), 1e-4f );
		}
	}

	/** Values outside [min, max] are not an error: they clamp to the nearest end, same as {@code ColorScheme} clamps an out-of-domain palette value. */
	@Test
	public void testOutOfRangeRawValuesClampToTheNearestEnd()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = build( factory );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( 0f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( -1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( 1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( Float.POSITIVE_INFINITY ), 1e-4f );
		}
	}

	/** Every implementation rejects an empty or inverted raw range, not just the one spot-checked below. */
	@Test
	public void testConstructorRejectsMaxNotGreaterThanMin()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
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
	public void testConstructorRejectsNonPositivePaletteRangeLength()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			for ( final int bad : new int[] { 0, -1 } )
			{
				try
				{
					factory.create( 0f, 1f, bad );
					Assert.fail( "expected IllegalArgumentException for paletteRangeLength=" + bad );
				}
				catch ( final IllegalArgumentException expected )
				{
				}
			}
		}
	}

	/**
	 * {@link PresetFunc#withRange(float, float)} keeps the shape and palette
	 * range but moves the endpoints: at the new min/max the palette value is
	 * the same 0/rangeLength as at the old ones, and a proportionally-placed
	 * raw value maps to the same palette value as before -- i.e. the shape was
	 * stretched, not distorted. Checked across all implementations.
	 */
	@Test
	public void testWithRangeStretchesTheSameShapeOntoTheNewEndpoints()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final PresetFunc original = build( factory ); // raw [100, 200] -> [0, 10]
			final PresetFunc reranged = original.withRange( 300f, 500f );
			final String name = factory.getClass().getSimpleName();

			Assert.assertEquals( name, 300f, reranged.getMin(), 0f );
			Assert.assertEquals( name, 500f, reranged.getMax(), 0f );
			Assert.assertEquals( name, original.getPaletteRangeLength(), reranged.getPaletteRangeLength() );

			// The 25%/50%/75% points of each range must produce the same palette value.
			Assert.assertEquals( name, original.getPaletteValueForRaw( 125f ), reranged.getPaletteValueForRaw( 350f ), 1e-4f );
			Assert.assertEquals( name, original.getPaletteValueForRaw( 150f ), reranged.getPaletteValueForRaw( 400f ), 1e-4f );
			Assert.assertEquals( name, original.getPaletteValueForRaw( 175f ), reranged.getPaletteValueForRaw( 450f ), 1e-4f );
		}
	}

	/** {@code withRange} keeps the shape independent of the endpoints, so re-ranging must not mutate the original. */
	@Test
	public void testWithRangeDoesNotMutateTheOriginal()
	{
		for ( final PresetFuncFactory factory : ALL_CONSTRUCTORS )
		{
			final PresetFunc original = build( factory );
			original.withRange( 300f, 500f );
			Assert.assertEquals( factory.getClass().getSimpleName(), 100f, original.getMin(), 0f );
			Assert.assertEquals( factory.getClass().getSimpleName(), 200f, original.getMax(), 0f );
		}
	}
}
