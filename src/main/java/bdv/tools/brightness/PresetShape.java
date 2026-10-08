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
import bdv.tools.brightness.presetfunc.PresetFunc;
import bdv.tools.brightness.presetfunc.SigmoidPresetFunc;
import bdv.tools.brightness.presetfunc.TanPresetFunc;

/**
 * Predefined shapes for the mapping {@link Curve}, sampled from the
 * corresponding {@link PresetFunc} into draggable control points.
 */
public enum PresetShape
{
	LINEAR( "Linear", LinearPresetFunc::new ),
	LOG( "Log", LogPresetFunc::new ),
	EXP( "Exp", ExpPresetFunc::new ),
	SIGMOID( "Sigmoid", SigmoidPresetFunc::new ),
	ALPHA_SIGMOID( "α-Sigmoid", AlphaSigmoidPresetFunc::new ),
	TAN( "Tan", TanPresetFunc::new ),
	ATAN( "Atan", AtanPresetFunc::new );

	/** Control points per non-linear preset; {@link #LINEAR} uses two. */
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

	/** Control point x positions in [0, 1]. */
	public double[] xs()
	{
		if ( this == LINEAR )
			return new double[] { 0.0, 1.0 };
		return sample().getKnotTs();
	}

	/** Control point values in [0, 255], matching {@link #xs()}. */
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

	private CustomInterpPresetFunc sample()
	{
		final PresetFunc shape = factory.create( 0.0, 1.0, 1 );
		return CustomInterpPresetFunc.sampled( shape, NUM_POINTS );
	}

	@FunctionalInterface
	private interface PresetFuncFactory
	{
		PresetFunc create( double min, double max, int paletteRangeLength );
	}
}
