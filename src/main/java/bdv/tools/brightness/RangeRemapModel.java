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
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model for range remapping: defines disjoint intervals over [0, 255] that
 * can be shuffled to remap how input intensities map to LUT positions.
 * <p>
 * Each interval [a, b] in the ordered list maps to a contiguous segment of
 * the LUT. The intervals are laid out sequentially in the LUT space according
 * to their order in the list.
 * <p>
 * Example: intervals [[50,100], [0,49], [150,255], [101,149]] means:
 * - input intensities in [50,100] map to LUT positions [0,50]
 * - input intensities in [0,49] map to LUT positions [51,100]
 * - etc.
 */
public class RangeRemapModel
{
	public boolean splitOutOfRange = false;

	/**
	 * An interval [start, end] inclusive, both in [0, 255].
	 * When start <= end, the interval maps forward.
	 * When start > end, the interval is inverted (maps in reverse).
	 */
	public static class Interval
	{
		public final int start;
		public final int end;

		public Interval( final int start, final int end )
		{
			this.start = start;
			this.end = end;
		}

		public int low()
		{
			return Math.min( start, end );
		}

		public int high()
		{
			return Math.max( start, end );
		}

		public boolean isInverted()
		{
			return start > end;
		}

		public int size()
		{
			return high() - low() + 1;
		}

		public Interval inverted()
		{
			return new Interval( end, start );
		}

		@Override
		public String toString()
		{
			return "[" + start + ", " + end + "]";
		}
	}

	private final List< Interval > intervals = new ArrayList<>();
	private final List< Runnable > changeListeners = new ArrayList<>();

	// Precomputed lookup: for each input intensity [0..255], the mapped LUT index
	private final int[] remapTable = new int[ 256 ];

	public RangeRemapModel()
	{
		reset();
	}

	/**
	 * Reset to a single interval [0, 255] (identity mapping).
	 */
	public void reset()
	{
		intervals.clear();
		intervals.add( new Interval( 0, 255 ) );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Get the current list of intervals (unmodifiable).
	 */
	public List< Interval > getIntervals()
	{
		return Collections.unmodifiableList( intervals );
	}

	/**
	 * Check whether a list of intervals is valid: disjoint and covering [0, 255] exactly.
	 */
	public static boolean isValid( final List< Interval > ivs )
	{
		if ( ivs == null || ivs.isEmpty() )
			return false;
		final boolean[] covered = new boolean[ 256 ];
		for ( final Interval iv : ivs )
		{
			for ( int i = iv.low(); i <= iv.high(); i++ )
			{
				if ( i < 0 || i > 255 || covered[ i ] )
					return false;
				covered[ i ] = true;
			}
		}
		for ( int i = 0; i <= 255; i++ )
			if ( !covered[ i ] )
				return false;
		return true;
	}

	/**
	 * Set the intervals. They must be disjoint and their union must cover [0, 255].
	 * If the new intervals are invalid, the call is ignored.
	 */
	public void setIntervals( final List< Interval > newIntervals )
	{
		if ( !isValid( newIntervals ) )
			return;
		intervals.clear();
		intervals.addAll( newIntervals );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Split the interval at the given index at the specified position.
	 * The split point becomes the end of the first sub-interval.
	 */
	public void splitInterval( final int index, final int splitPoint )
	{
		splitOutOfRange = false;
		if ( index < 0 || index >= intervals.size() )
		{
			splitOutOfRange = true;
			fireChangeListeners();
			return;
		}
		final Interval iv = intervals.get( index );
		if ( splitPoint <= iv.low() || splitPoint >= iv.high() )
		{
			splitOutOfRange = true;
			fireChangeListeners();
			return;
		}
		splitOutOfRange = false;
		intervals.remove( index );
		if ( iv.isInverted() )
		{
			intervals.add( index, new Interval( iv.start, splitPoint + 1 ) );
			intervals.add( index + 1, new Interval( splitPoint, iv.end ) );
		} else
		{
			intervals.add( index, new Interval( iv.start, splitPoint ) );
			intervals.add( index + 1, new Interval( splitPoint + 1, iv.end ) );
		}
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Swap two intervals in the ordering.
	 */
	public void swapIntervals( final int idx1, final int idx2 )
	{
		if ( idx1 < 0 || idx1 >= intervals.size() || idx2 < 0 || idx2 >= intervals.size() )
			return;
		Collections.swap( intervals, idx1, idx2 );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Move an interval from one position to another.
	 */
	public void moveInterval( final int fromIndex, final int toIndex )
	{
		if ( fromIndex < 0 || fromIndex >= intervals.size() || toIndex < 0 || toIndex >= intervals.size() )
			return;
		final Interval iv = intervals.remove( fromIndex );
		intervals.add( toIndex, iv );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Merge an interval at index with the next interval (index+1).
	 * The merged interval covers the union of both ranges.
	 */
	public void mergeWithNext( final int index )
	{
		if ( index < 0 || index >= intervals.size() - 1 )
			return;
		final Interval a = intervals.get( index );
		final Interval b = intervals.get( index + 1 );
		intervals.remove( index + 1 );
		intervals.remove( index );
		final int lo = Math.min( a.low(), b.low() );
		final int hi = Math.max( a.high(), b.high() );
		intervals.add( index, new Interval( lo, hi ) );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Invert the interval at the given index (swap start and end).
	 */
	public void invertInterval( final int index )
	{
		if ( index < 0 || index >= intervals.size() )
			return;
		intervals.set( index, intervals.get( index ).inverted() );
		rebuildRemapTable();
		fireChangeListeners();
	}

	/**
	 * Remap an input intensity [0, 255] to a LUT index [0, 255].
	 */
	public int remap( final int inputIntensity )
	{
		final int clamped = Math.max( 0, Math.min( 255, inputIntensity ) );
		return remapTable[ clamped ];
	}

	/**
	 * Remap a normalized input [0, 1] to a normalized LUT position [0, 1].
	 */
	public double remapNormalized( final double normalizedInput )
	{
		final int idx = ( int ) Math.round( normalizedInput * 255.0 );
		return remap( idx ) / 255.0;
	}

	private void rebuildRemapTable()
	{
		// For each interval in order, assign sequential LUT positions
		int lutPos = 0;
		for ( final Interval iv : intervals )
		{
			if ( iv.isInverted() )
			{
				// Inverted: iterate from start down to end
				for ( int i = iv.start; i >= iv.end; i-- )
				{
					if ( i >= 0 && i <= 255 && lutPos <= 255 )
					{
						remapTable[ i ] = lutPos;
						lutPos++;
					}
				}
			} else
			{
				for ( int i = iv.start; i <= iv.end; i++ )
				{
					if ( i >= 0 && i <= 255 && lutPos <= 255 )
					{
						remapTable[ i ] = lutPos;
						lutPos++;
					}
				}
			}
		}
	}

	public void addChangeListener( final Runnable listener )
	{
		changeListeners.add( listener );
	}

	public void removeChangeListener( final Runnable listener )
	{
		changeListeners.remove( listener );
	}

	private void fireChangeListeners()
	{
		for ( final Runnable listener : changeListeners )
			listener.run();
	}
}
