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

/**
 * Represents a piecewise-linear curve with control points.
 * Maps input values [0, 1] to output values [0, 255] via linear interpolation.
 * <p>
 * Control points are kept sorted by ascending x. Their number is small (a
 * preset's handful of points, plus whatever the user adds), so insert/remove
 * simply reallocate rather than maintaining spare capacity; the arrays are
 * primitive because {@link #evaluate} runs per rendered pixel.
 */
public class Curve
{
	private double[] xs = { 0.0, 1.0 };

	private int[] ys = { 0, 255 };

	/**
	 * Add a control point at the given x position.
	 * If a point at this x already exists, update its y value.
	 */
	public void addPoint( final double x, final int y )
	{
		int insertIdx = xs.length;
		for ( int i = 0; i < xs.length; i++ )
		{
			if ( Math.abs( xs[ i ] - x ) < 1e-6 )
			{
				ys[ i ] = clamp( y );
				return;
			}
			if ( xs[ i ] > x )
			{
				insertIdx = i;
				break;
			}
		}

		final double[] newXs = new double[ xs.length + 1 ];
		final int[] newYs = new int[ ys.length + 1 ];
		System.arraycopy( xs, 0, newXs, 0, insertIdx );
		System.arraycopy( ys, 0, newYs, 0, insertIdx );
		newXs[ insertIdx ] = x;
		newYs[ insertIdx ] = clamp( y );
		System.arraycopy( xs, insertIdx, newXs, insertIdx + 1, xs.length - insertIdx );
		System.arraycopy( ys, insertIdx, newYs, insertIdx + 1, ys.length - insertIdx );
		xs = newXs;
		ys = newYs;
	}

	/**
	 * Remove the point at the given index.
	 * Cannot remove the first or last point.
	 */
	public void removePoint( final int index )
	{
		if ( index <= 0 || index >= xs.length - 1 )
			return;

		final double[] newXs = new double[ xs.length - 1 ];
		final int[] newYs = new int[ ys.length - 1 ];
		System.arraycopy( xs, 0, newXs, 0, index );
		System.arraycopy( ys, 0, newYs, 0, index );
		System.arraycopy( xs, index + 1, newXs, index, xs.length - index - 1 );
		System.arraycopy( ys, index + 1, newYs, index, ys.length - index - 1 );
		xs = newXs;
		ys = newYs;
	}

	/**
	 * Find the nearest point to (x, y) within the given tolerance.
	 * x should be in [0, 1], y should be in [0, 1]
	 * Returns the index, or -1 if no point is near enough.
	 */
	public int findNearestPoint( final double x, final double y, final double tolerance )
	{
		int bestIdx = -1;
		double bestDist = tolerance;

		for ( int i = 0; i < xs.length; i++ )
		{
			final double dx = x - xs[ i ];
			final double dy = y - ys[ i ] / 255.0; // ys are [0, 255], y is [0, 1]
			final double dist = Math.sqrt( dx * dx + dy * dy );
			if ( dist < bestDist )
			{
				bestDist = dist;
				bestIdx = i;
			}
		}

		return bestIdx;
	}

	/**
	 * Get the x value of the control point at the given index.
	 */
	public double getX( final int index )
	{
		return xs[ index ];
	}

	/**
	 * Get the y value of the control point at the given index.
	 */
	public int getY( final int index )
	{
		return ys[ index ];
	}

	/**
	 * Get the number of control points.
	 */
	public int getPointCount()
	{
		return xs.length;
	}

	/**
	 * Set the output value of the control point at the given index. Its x
	 * position is left alone, so the points stay sorted.
	 */
	public void setPoint( final int index, final int y )
	{
		if ( index >= 0 && index < ys.length )
			ys[ index ] = clamp( y );
	}

	/**
	 * Evaluate the curve at a normalized input position [0, 1].
	 * Returns a value in [0, 255].
	 */
	public int evaluate( final double x )
	{
		if ( xs.length < 2 )
			return 0;

		// Points are sorted, so the last one at or before x is the start of
		// the segment containing x; past the final point, both ends collapse
		// onto it and the flat branch below returns its value.
		int lo = 0;
		while ( lo + 1 < xs.length && xs[ lo + 1 ] <= x )
			lo++;
		final int hi = Math.min( lo + 1, xs.length - 1 );

		if ( xs[ hi ] <= xs[ lo ] )
			return ys[ lo ];

		final double t = ( x - xs[ lo ] ) / ( xs[ hi ] - xs[ lo ] );
		return ( int ) Math.round( ys[ lo ] + t * ( ys[ hi ] - ys[ lo ] ) );
	}

	/**
	 * Replace all control points with copies of the given x/y arrays, which
	 * must be sorted by ascending x.
	 */
	public void setPoints( final double[] xs, final int[] ys )
	{
		this.xs = xs.clone();
		this.ys = new int[ ys.length ];
		for ( int i = 0; i < ys.length; i++ )
			this.ys[ i ] = clamp( ys[ i ] );
	}

	/**
	 * Get a copy of the control points' x values, suitable for passing to
	 * {@link #setPoints(double[], int[])} on another {@code Curve}.
	 */
	public double[] xsArray()
	{
		return xs.clone();
	}

	/**
	 * Get a copy of the control points' y values, suitable for passing to
	 * {@link #setPoints(double[], int[])} on another {@code Curve}.
	 */
	public int[] ysArray()
	{
		return ys.clone();
	}

	/**
	 * Flip the curve vertically: each control point's y becomes
	 * {@code 255 - y}, keeping its x unchanged. An increasing curve becomes
	 * decreasing and vice versa (e.g. the default linear ramp 0-&gt;255
	 * becomes 255-&gt;0).
	 */
	public void invert()
	{
		for ( int i = 0; i < ys.length; i++ )
			ys[ i ] = clamp( 255 - ys[ i ] );
	}

	private int clamp( final int v )
	{
		return Math.max( 0, Math.min( 255, v ) );
	}
}
