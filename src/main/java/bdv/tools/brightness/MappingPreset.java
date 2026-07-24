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

import java.util.function.DoubleUnaryOperator;

/**
 * Predefined shapes for the mapping {@link Curve}. Each preset is sampled at
 * a fixed number of control points spanning the normalized input range
 * [0, 1]; the resulting points can be dragged further by the user afterwards.
 */
public enum MappingPreset
{
	LINEAR( "Linear", x -> x ),
	PERCENTILE_STRETCH( "Percentile Stretch", MappingPreset::percentileStretch ),
	LOG( "Log", MappingPreset::log ),
	EXP( "Exp", MappingPreset::exp ),
	SIGMOID( "Sigmoid", x -> sigmoid( x, 10.0 ) ),
	ALPHA_SIGMOID( "α-Sigmoid", MappingPreset::alphaSigmoid ),
	TAN( "Tan", MappingPreset::tan ),
	ATAN( "Atan", MappingPreset::atan );

	private static final int NUM_POINTS = 9;

	private final String label;

	private final DoubleUnaryOperator shape;

	MappingPreset( final String label, final DoubleUnaryOperator shape )
	{
		this.label = label;
		this.shape = shape;
	}

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * Normalized x positions (in [0, 1]) of the sampled control points.
	 */
	public double[] xs()
	{
		final double[] xs = new double[ NUM_POINTS ];
		for ( int i = 0; i < NUM_POINTS; i++ )
			xs[ i ] = i / ( double ) ( NUM_POINTS - 1 );
		return xs;
	}

	/**
	 * Output values (in [0, 255]) of the sampled control points, corresponding
	 * to the x positions returned by {@link #xs()}.
	 */
	public int[] ys()
	{
		final int[] ys = new int[ NUM_POINTS ];
		for ( int i = 0; i < NUM_POINTS; i++ )
		{
			final double x = i / ( double ) ( NUM_POINTS - 1 );
			final double v = Math.max( 0.0, Math.min( 1.0, shape.applyAsDouble( x ) ) );
			ys[ i ] = ( int ) Math.round( v * 255.0 );
		}
		// guarantee exact endpoints regardless of numerical rounding
		ys[ 0 ] = 0;
		ys[ NUM_POINTS - 1 ] = 255;
		return ys;
	}

	private static double percentileStretch( final double x )
	{
		final double lo = 0.02;
		final double hi = 0.98;
		if ( x <= lo )
			return 0;
		if ( x >= hi )
			return 1;
		return ( x - lo ) / ( hi - lo );
	}

	private static double log( final double x )
	{
		final double k = 20.0;
		return Math.log1p( k * x ) / Math.log1p( k );
	}

	private static double exp( final double x )
	{
		final double k = 4.0;
		return ( Math.exp( k * x ) - 1.0 ) / ( Math.exp( k ) - 1.0 );
	}

	private static double sigmoid( final double x, final double k )
	{
		return normalized( x, t -> 1.0 / ( 1.0 + Math.exp( -k * ( t - 0.5 ) ) ) );
	}

	private static double alphaSigmoid( final double x )
	{
		final double alpha = 0.5;
		final double xp = Math.pow( x, alpha );
		final double xq = Math.pow( 1.0 - x, alpha );
		if ( xp + xq == 0 )
			return 0;
		return xp / ( xp + xq );
	}

	private static double tan( final double x )
	{
		final double k = 1.4;
		return normalized( x, t -> Math.tan( k * ( t - 0.5 ) ) );
	}

	private static double atan( final double x )
	{
		final double k = 6.0;
		return normalized( x, t -> Math.atan( k * ( t - 0.5 ) ) );
	}

	/**
	 * Rescale {@code f} so that {@code f(0) -> 0} and {@code f(1) -> 1}.
	 */
	private static double normalized( final double x, final DoubleUnaryOperator f )
	{
		final double v = f.applyAsDouble( x );
		final double v0 = f.applyAsDouble( 0.0 );
		final double v1 = f.applyAsDouble( 1.0 );
		return ( v - v0 ) / ( v1 - v0 );
	}
}
