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
package bdv.tools.brightness;

import bdv.tools.brightness.presetfunc.AlphaSigmoidPresetFunc;
import bdv.tools.brightness.presetfunc.AtanPresetFunc;
import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;
import bdv.tools.brightness.presetfunc.ExpPresetFunc;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.tools.brightness.presetfunc.LogPresetFunc;
import bdv.tools.brightness.presetfunc.PercentileStretchPresetFunc;
import bdv.tools.brightness.presetfunc.PresetFunc;
import bdv.tools.brightness.presetfunc.SigmoidPresetFunc;
import bdv.tools.brightness.presetfunc.TanPresetFunc;

/**
 * Predefined shapes for the mapping {@link Curve}, sampled from the
 * {@code bdv.tools.brightness.presetfunc} shape classes -- unlike the old
 * {@code MappingPreset} this replaced, the shape math lives exactly once
 * (in each {@link PresetFunc}), not duplicated here. Each preset is sampled
 * at a fixed number of control points spanning the normalized input range
 * [0, 1]; the resulting points can be dragged further by the user afterwards.
 */
public enum PresetShape
{
	LINEAR( "Linear", LinearPresetFunc::new ),
	PERCENTILE_STRETCH( "Percentile Stretch", PercentileStretchPresetFunc::new ),
	LOG( "Log", LogPresetFunc::new ),
	EXP( "Exp", ExpPresetFunc::new ),
	SIGMOID( "Sigmoid", SigmoidPresetFunc::new ),
	ALPHA_SIGMOID( "α-Sigmoid", AlphaSigmoidPresetFunc::new ),
	TAN( "Tan", TanPresetFunc::new ),
	ATAN( "Atan", AtanPresetFunc::new );

	/**
	 * Sampled at as many control points as {@code MappingPreset} used, so a
	 * preset looks and drags the same as before; {@link #LINEAR} is still the
	 * exception below (see {@link #xs()}).
	 */
	private static final int NUM_POINTS = 9;

	private final String label;

	private final PresetFuncFactory factory;

	PresetShape( final String label, final PresetFuncFactory factory )
	{
		this.label = label;
		this.factory = factory;
	}

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * Normalized x positions (in [0, 1]) of the sampled control points.
	 * <p>
	 * {@link #LINEAR} is the exception: a straight line is fully determined
	 * by its two endpoints, so it is represented with just those two,
	 * instead of {@link #NUM_POINTS} redundant ones -- sampling it at more
	 * points would show 9 collinear, indistinguishable draggable dots
	 * instead of a clean 2-point line.
	 */
	public double[] xs()
	{
		if ( this == LINEAR )
			return new double[] { 0.0, 1.0 };
		return sample().getKnotTs();
	}

	/**
	 * Output values (in [0, 255]) of the sampled control points, corresponding
	 * to the x positions returned by {@link #xs()}.
	 */
	public int[] ys()
	{
		if ( this == LINEAR )
			return new int[] { 0, 255 };
		final double[] values = sample().getKnotValues();
		final int[] ys = new int[ values.length ];
		for ( int i = 0; i < values.length; i++ )
			ys[ i ] = ( int ) Math.round( values[ i ] * 255.0 );
		// guarantee exact endpoints regardless of numerical rounding
		ys[ 0 ] = 0;
		ys[ values.length - 1 ] = 255;
		return ys;
	}

	/** This preset's shape, sampled into {@link #NUM_POINTS} knots over a normalized [0, 1] domain and palette range. */
	private CustomInterpPresetFunc sample()
	{
		final PresetFunc shape = factory.create( 0f, 1f, 1 );
		return CustomInterpPresetFunc.sampled( shape, NUM_POINTS );
	}

	@FunctionalInterface
	private interface PresetFuncFactory
	{
		PresetFunc create( float min, float max, int paletteRangeLength );
	}
}
