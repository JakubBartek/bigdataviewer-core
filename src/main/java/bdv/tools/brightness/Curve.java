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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a piecewise-linear curve with control points.
 * Maps input values [0, 1] to output values [0, 255] via linear interpolation.
 */
public class Curve
{
	private final List< Double > xValues = new ArrayList<>();
	private final List< Integer > yValues = new ArrayList<>();

	public Curve()
	{
		xValues.add( 0.0 );
		yValues.add( 0 );
		xValues.add( 1.0 );
		yValues.add( 255 );
	}

	/**
	 * Add a control point at the given x position.
	 * If a point at this x already exists, update its y value.
	 */
	public void addPoint( final double x, final int y )
	{
		// Find insertion position
		int insertIdx = -1;
		for ( int i = 0; i < xValues.size(); i++ )
		{
			final double xi = xValues.get( i );
			if ( Math.abs( xi - x ) < 1e-6 )
			{
				// Update existing point
				yValues.set( i, clamp( y ) );
				return;
			}
			if ( xi > x )
			{
				insertIdx = i;
				break;
			}
		}
		if ( insertIdx < 0 )
			insertIdx = xValues.size();

		xValues.add( insertIdx, x );
		yValues.add( insertIdx, clamp( y ) );
	}

	/**
	 * Remove the point at the given index.
	 * Cannot remove the first or last point.
	 */
	public void removePoint( final int index )
	{
		if ( index <= 0 || index >= xValues.size() - 1 )
			return; // Cannot remove endpoints
		xValues.remove( index );
		yValues.remove( index );
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

		for ( int i = 0; i < xValues.size(); i++ )
		{
			final double xi = xValues.get( i );
			final double yi = yValues.get( i ) / 255.0; // Normalize y to [0, 1]
			final double dx = x - xi;
			final double dy = y - yi;
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
		return xValues.get( index );
	}

	/**
	 * Get the y value of the control point at the given index.
	 */
	public int getY( final int index )
	{
		return yValues.get( index );
	}

	/**
	 * Get the number of control points.
	 */
	public int getPointCount()
	{
		return xValues.size();
	}

	/**
	 * Update the control point at the given index.
	 */
	public void setPoint( final int index, final int y )
	{
		// For internal points, we can update x (but keep sorted)
		// For simplicity, just update y for existing points
		if ( index >= 0 && index < yValues.size() )
		{
			yValues.set( index, clamp( y ) );
		}
	}

	/**
	 * Evaluate the curve at a normalized input position [0, 1].
	 * Returns a value in [0, 255].
	 */
	public int evaluate( final double x )
	{
		if ( xValues.size() < 2 )
			return 0;

		// Find the two control points surrounding x
		int idx1 = 0;
		int idx2 = 1;
		for ( int i = 1; i < xValues.size(); i++ )
		{
			if ( xValues.get( i ) <= x )
			{
				idx1 = i;
				idx2 = i + 1;
				// Clamp idx2 to valid range
				if ( idx2 >= xValues.size() )
					idx2 = xValues.size() - 1;
			}
		}

		// Ensure valid indices
		if ( idx2 >= xValues.size() )
			idx2 = xValues.size() - 1;

		final double x1 = xValues.get( idx1 );
		final double x2 = xValues.get( idx2 );
		final int y1 = yValues.get( idx1 );
		final int y2 = yValues.get( idx2 );

		if ( x2 <= x1 )
			return y1;

		// Linear interpolation
		final double t = ( x - x1 ) / ( x2 - x1 );
		return ( int ) Math.round( y1 + t * ( y2 - y1 ) );
	}

	/**
	 * Replace all control points with the given x/y arrays. The arrays must
	 * be sorted by ascending x and are not copied defensively.
	 */
	public void setPoints( final double[] xs, final int[] ys )
	{
		xValues.clear();
		yValues.clear();
		for ( int i = 0; i < xs.length; i++ )
		{
			xValues.add( xs[ i ] );
			yValues.add( clamp( ys[ i ] ) );
		}
	}

	/**
	 * Get a copy of the control points' x values, suitable for passing to
	 * {@link #setPoints(double[], int[])} on another {@code Curve}.
	 */
	public double[] xsArray()
	{
		final double[] xs = new double[ xValues.size() ];
		for ( int i = 0; i < xs.length; i++ )
			xs[ i ] = xValues.get( i );
		return xs;
	}

	/**
	 * Get a copy of the control points' y values, suitable for passing to
	 * {@link #setPoints(double[], int[])} on another {@code Curve}.
	 */
	public int[] ysArray()
	{
		final int[] ys = new int[ yValues.size() ];
		for ( int i = 0; i < ys.length; i++ )
			ys[ i ] = yValues.get( i );
		return ys;
	}

	/**
	 * Evaluate the curve at a normalized input position [0, 1] using the
	 * given {@link ValueMatching} strategy. Returns a value in [0, 255].
	 */
	public int evaluate( final double x, final ValueMatching matching )
	{
		if ( matching == ValueMatching.INTERPOLATE )
			return evaluate( x );
		return yValues.get( truncateIndex( x ) );
	}

	/**
	 * Find the control point used for {@link ValueMatching#TRUNCATE}
	 * evaluation: the last control point at or before {@code x}.
	 */
	private int truncateIndex( final double x )
	{
		int idx = 0;
		for ( int i = 0; i < xValues.size(); i++ )
			if ( xValues.get( i ) <= x + 1e-9 )
				idx = i;
		return idx;
	}

	/**
	 * Reset the curve to a linear gradient (identity mapping).
	 */
	public void reset()
	{
		xValues.clear();
		yValues.clear();
		xValues.add( 0.0 );
		yValues.add( 0 );
		xValues.add( 1.0 );
		yValues.add( 255 );
	}

	private int clamp( final int v )
	{
		return Math.max( 0, Math.min( 255, v ) );
	}
}
