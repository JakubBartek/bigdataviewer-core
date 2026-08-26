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
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for behavior every {@link PresetFunc} implementation shares
 * (constructor validation, {@code getMin}/{@code getMax}/{@code getDelkaIntervalu},
 * and the {@code shape(0) == 0}/{@code shape(1) == 1} endpoint guarantee),
 * checked generically across all eight implementations rather than repeating
 * it in each one's own test class -- their individual test classes instead
 * focus on what is actually distinctive about each: its shape in between.
 */
public class AbstractPresetFuncTest
{
	/** One constructor reference per implementation, so the endpoint/getter tests below run against all eight without repeating them by hand. */
	private static final List< Function< float[], PresetFunc > > ALL_CONSTRUCTORS = Arrays.asList(
			args -> new LinearPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new PercentileStretchPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new LogarithmicPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new ExponentialPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new SigmoidPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new AlphaSigmoidPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new TanPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new AtanPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ),
			args -> new CustomInterpPresetFunc( args[ 0 ], args[ 1 ], args[ 2 ] ) );

	@Test
	public void testGettersReturnConstructorArguments()
	{
		for ( final Function< float[], PresetFunc > ctor : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = ctor.apply( new float[] { 100f, 200f, 10f } );
			Assert.assertEquals( f.getClass().getSimpleName(), 100f, f.getMin(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 200f, f.getMax(), 0f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getDelkaIntervalu(), 0f );
		}
	}

	/**
	 * Every shape is normalized so its two ends land exactly on palette value
	 * {@code 0} and {@code getDelkaIntervalu()} -- this is what lets a
	 * {@code ContinuousPaletteWrapper} later rely on any {@link PresetFunc}
	 * feeding a continuous color scheme's full domain, regardless of which
	 * shape was chosen.
	 */
	@Test
	public void testEveryShapeReachesExactlyZeroAndDelkaIntervaluAtTheEnds()
	{
		for ( final Function< float[], PresetFunc > ctor : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = ctor.apply( new float[] { 100f, 200f, 10f } );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( 100f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( 200f ), 1e-4f );
		}
	}

	/** Values outside [min, max] are not an error: they clamp to the nearest end, same as {@code IColorScheme} clamps an out-of-domain palette value. */
	@Test
	public void testOutOfRangeRawValuesClampToTheNearestEnd()
	{
		for ( final Function< float[], PresetFunc > ctor : ALL_CONSTRUCTORS )
		{
			final PresetFunc f = ctor.apply( new float[] { 100f, 200f, 10f } );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( 0f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 0f, f.getPaletteValueForRaw( -1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( 1000f ), 1e-4f );
			Assert.assertEquals( f.getClass().getSimpleName(), 10f, f.getPaletteValueForRaw( Float.POSITIVE_INFINITY ), 1e-4f );
		}
	}

	@Test
	public void testConstructorRejectsMaxNotGreaterThanMin()
	{
		for ( final float[] badRange : new float[][] { { 5f, 5f, 10f }, { 5f, 4f, 10f } } )
		{
			try
			{
				new LinearPresetFunc( badRange[ 0 ], badRange[ 1 ], badRange[ 2 ] );
				Assert.fail( "expected IllegalArgumentException for min=" + badRange[ 0 ] + ", max=" + badRange[ 1 ] );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}

	@Test
	public void testConstructorRejectsNonPositiveDelkaIntervalu()
	{
		for ( final float bad : new float[] { 0f, -1f, Float.NaN } )
		{
			try
			{
				new LinearPresetFunc( 0f, 1f, bad );
				Assert.fail( "expected IllegalArgumentException for delkaIntervalu=" + bad );
			}
			catch ( final IllegalArgumentException expected )
			{
			}
		}
	}
}
