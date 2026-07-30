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
 * Model for mapping raw source values to a LUT index [0, 255].
 * <p>
 * A source value is first normalized to a position in [0, 1], the way
 * depending on the {@link RangeMode}:
 * <ul>
 * <li>{@link RangeMode#FIT}: the value is clamped against the given
 * [min, max] range.</li>
 * <li>{@link RangeMode#CYCLIC}: the value cycles through the palette's
 * actual number of colors, anchored at min (a value of exactly min always
 * gets the palette's first color) and otherwise ignoring min/max. An integer
 * value lands exactly on a palette color, wrapping every {@code lutColorCount}
 * values (e.g. label/segmentation ids cycling through a small categorical
 * palette).</li>
 * </ul>
 * That position is then evaluated smoothly against a {@link Curve} to produce
 * a LUT index in [0, 255]. The current {@link ValueMatching} strategy is
 * <em>not</em> applied here -- it instead governs how that LUT index selects
 * a final, discrete palette color (see
 * {@link ColorTableLut#lookupARGB(net.imglib2.display.ColorTable, double, double, double, ValueMatching)}).
 * Quantizing at the curve stage too (in addition to the palette stage) would
 * make some palette colors unreachable, since the curve's own control points
 * are an unrelated resolution to the palette's actual color count.
 * <p>
 * The curve is initialized from a {@link MappingPreset} but can be further
 * customized by adding/moving/removing its control points.
 */
public class MappingModel
{
	private final Curve curve = new Curve();

	private RangeMode rangeMode = RangeMode.FIT;

	/**
	 * When {@code true} (and {@link #rangeMode} is {@link RangeMode#CYCLIC}),
	 * a raw source value of exactly {@code min} (the left/start value of the
	 * range) is always mapped to {@link #backgroundColor}, bypassing the
	 * cyclic wrap entirely -- it is a dedicated color, not one of the
	 * palette's cycled colors, and is used only for that one value. Useful
	 * for label/segmentation images where the min value marks background.
	 */
	private boolean treatMinAsBackground = false;

	/**
	 * The dedicated background color used when {@link #treatMinAsBackground}
	 * applies, packed as ARGB (see {@link net.imglib2.type.numeric.ARGBType}).
	 * Defaults to opaque black.
	 */
	private int backgroundColor = 0xff000000;

	private ValueMatching valueMatching = ValueMatching.INTERPOLATE;

	private MappingPreset preset;

	private final List< Runnable > changeListeners = new ArrayList<>();

	public MappingModel()
	{
		applyPreset( MappingPreset.LINEAR );
	}

	public Curve getCurve()
	{
		return curve;
	}

	public RangeMode getRangeMode()
	{
		return rangeMode;
	}

	public void setRangeMode( final RangeMode rangeMode )
	{
		this.rangeMode = rangeMode;
		fireChangeListeners();
	}

	public boolean isTreatMinAsBackground()
	{
		return treatMinAsBackground;
	}

	public void setTreatMinAsBackground( final boolean treatMinAsBackground )
	{
		this.treatMinAsBackground = treatMinAsBackground;
		fireChangeListeners();
	}

	/**
	 * The dedicated background color, packed as ARGB.
	 */
	public int getBackgroundColor()
	{
		return backgroundColor;
	}

	public void setBackgroundColor( final int backgroundColor )
	{
		this.backgroundColor = backgroundColor;
		fireChangeListeners();
	}

	/**
	 * Whether {@code value} should be rendered as the dedicated
	 * {@link #getBackgroundColor()} instead of being looked up in the
	 * palette. Only applies in {@link RangeMode#CYCLIC}, when
	 * {@link #isTreatMinAsBackground()} is enabled, for any raw value below
	 * {@code min + 1} -- i.e. the "reserved" unit interval {@code [min, min +
	 * 1)} itself (the left/start value of the range), and everything below
	 * {@code min} too (out-of-range values on the low side are background,
	 * not cycled; out-of-range values on the high side still cycle normally,
	 * same as when this option is off).
	 * <p>
	 * The {@code [min, min + 1)} part is not just the exact point
	 * {@code value == min}: {@link #cyclicPosition} only defines integer
	 * inputs exactly on a palette color, so without covering the whole unit
	 * interval, any non-integer value strictly between {@code min} and
	 * {@code min + 1} (e.g. continuous image data, or a continuous preview)
	 * would fall through to {@link #mapToLutIndex} and incorrectly render as
	 * the palette's *last* color, since that gap is exactly what is skipped
	 * to make the cycle start at min + 1.
	 */
	public boolean isBackgroundValue( final double value, final double min )
	{
		return rangeMode == RangeMode.CYCLIC && treatMinAsBackground && value < min + 1;
	}

	public ValueMatching getValueMatching()
	{
		return valueMatching;
	}

	public void setValueMatching( final ValueMatching valueMatching )
	{
		this.valueMatching = valueMatching;
		fireChangeListeners();
	}

	public MappingPreset getPreset()
	{
		return preset;
	}

	/**
	 * Apply a preset, replacing the current curve control points with the
	 * preset's shape. The control points can still be dragged afterwards.
	 */
	public void applyPreset( final MappingPreset preset )
	{
		this.preset = preset;
		curve.setPoints( preset.xs(), preset.ys() );
		fireChangeListeners();
	}

	/**
	 * Copy the range mode, value matching, preset and curve control points
	 * from another model into this one.
	 */
	public void copyFrom( final MappingModel other )
	{
		this.rangeMode = other.rangeMode;
		this.treatMinAsBackground = other.treatMinAsBackground;
		this.backgroundColor = other.backgroundColor;
		this.valueMatching = other.valueMatching;
		this.preset = other.preset;
		this.curve.setPoints( other.curve.xsArray(), other.curve.ysArray() );
		fireChangeListeners();
	}

	/**
	 * Map a raw source value to a LUT index in [0, 255], assuming the target
	 * LUT's {@code lutColorCount} colors are evenly spaced. Does not account
	 * for {@link #isBackgroundValue}; callers should check that first and use
	 * {@link #getBackgroundColor()} directly instead of calling this method.
	 * <p>
	 * Real palettes are not always evenly spaced (e.g. a LUT resource file can
	 * declare arbitrary control point positions); when the actual positions
	 * are known, prefer {@link #mapToLutIndex(double, double, double, double[])}
	 * (see {@link ColorTableLut#colorPositions(net.imglib2.display.ColorTable)}).
	 *
	 * @param value
	 * 		the raw source value.
	 * @param min
	 * 		source value mapped to the start of the range; only used in
	 * 		{@link RangeMode#FIT}.
	 * @param max
	 * 		source value mapped to the end of the range; only used in
	 * 		{@link RangeMode#FIT}.
	 * @param lutColorCount
	 * 		the number of distinct colors in the target LUT (see
	 * 		{@link net.imglib2.display.ColorTable#getLength()}); only used in
	 * 		{@link RangeMode#CYCLIC}, as the wrap period.
	 */
	public int mapToLutIndex( final double value, final double min, final double max, final int lutColorCount )
	{
		return mapToLutIndex( value, min, max, uniformColorPositions( lutColorCount ) );
	}

	/**
	 * Map a raw source value to a LUT index in [0, 255]. Does not account for
	 * {@link #isBackgroundValue}; callers should check that first and use
	 * {@link #getBackgroundColor()} directly instead of calling this method.
	 *
	 * @param value
	 * 		the raw source value.
	 * @param min
	 * 		source value mapped to the start of the range; only used in
	 * 		{@link RangeMode#FIT}.
	 * @param max
	 * 		source value mapped to the end of the range; only used in
	 * 		{@link RangeMode#FIT}.
	 * @param colorPositions
	 * 		the target LUT's colors' normalized positions, in order (see
	 * 		{@link ColorTableLut#colorPositions(net.imglib2.display.ColorTable)});
	 * 		only used in {@link RangeMode#CYCLIC}, whose wrap period is the
	 * 		array's length.
	 */
	public int mapToLutIndex( final double value, final double min, final double max, final double[] colorPositions )
	{
		final double t = rangeMode == RangeMode.CYCLIC
				? cyclicPosition( value, min, colorPositions, treatMinAsBackground )
				: fitPosition( value, min, max );
		// Always smooth here -- see the class javadoc for why ValueMatching
		// is applied only once, at the palette-color stage, instead of here too.
		final int y = curve.evaluate( t );
		return Math.max( 0, Math.min( 255, y ) );
	}

	/**
	 * Normalized curve position for a raw value in {@link RangeMode#FIT}:
	 * clamped against [min, max]. Package-visible so the UI can reuse the
	 * exact same formula used by {@link #mapToLutIndex} (e.g. to draw the
	 * curve consistently with what is actually applied).
	 */
	static double fitPosition( final double value, final double min, final double max )
	{
		final double span = max - min;
		return span > 0 ? Math.max( 0.0, Math.min( 1.0, ( value - min ) / span ) ) : 0.0;
	}

	/**
	 * Same as {@link #cyclicPosition(double, double, double[], boolean)},
	 * assuming the palette's {@code lutColorCount} colors are evenly spaced.
	 */
	static double cyclicPosition( final double value, final double min, final int lutColorCount, final boolean treatMinAsBackground )
	{
		return cyclicPosition( value, min, uniformColorPositions( lutColorCount ), treatMinAsBackground );
	}

	/**
	 * Normalized curve position for a raw value in {@link RangeMode#CYCLIC}:
	 * the value cycles through the palette's colors (one per entry of
	 * {@code colorPositions}), anchored at {@code min} (a raw value of
	 * exactly {@code min} always lands on the palette's first color -- e.g.
	 * setting min to 5 means value 5 gets whatever color is first in the
	 * palette) and otherwise ignoring min/max, landing exactly on
	 * {@code colorPositions[k]} for integer inputs {@code k} steps past min.
	 * Non-integer inputs interpolate linearly between the two neighboring
	 * colors' actual positions, so unevenly-spaced palettes (not all LUTs
	 * have evenly-spaced colors) are still followed correctly rather than
	 * assuming a uniform {@code k / (n - 1)} step. Package-visible so the UI
	 * can reuse the exact same formula used by {@link #mapToLutIndex}.
	 */
	static double cyclicPosition( final double value, final double min, final double[] colorPositions, final boolean treatMinAsBackground )
	{
		final int n = colorPositions.length;
		if ( n <= 1 )
			return 0.0;
		final double shifted = value - min;
		// When min is reserved for the background color (handled separately,
		// see isBackgroundValue), the cycle instead starts at min+1, so all n
		// palette colors remain reachable without min itself ever competing
		// for one of them.
		double m = treatMinAsBackground ? ( shifted - 1 ) % n : shifted % n;
		if ( m < 0 )
			m += n;
		// The last unit interval before wrapping back to color 0 (m in
		// [n - 1, n)) holds flat at the last color, rather than blending
		// into the next cycle's first color.
		if ( m >= n - 1 )
			return colorPositions[ n - 1 ];
		final int k = ( int ) Math.floor( m );
		final double frac = m - k;
		return colorPositions[ k ] + frac * ( colorPositions[ k + 1 ] - colorPositions[ k ] );
	}

	/**
	 * The inverse of {@link #cyclicPosition(double, double, double[], boolean)}:
	 * given a normalized curve position {@code t}, the corresponding "raw
	 * value minus min" offset within a single cycle. Used to draw/hit-test a
	 * curve control point at its correct raw input value in
	 * {@link RangeMode#CYCLIC} (repeated every {@code colorPositions.length}
	 * raw units), without assuming evenly-spaced colors.
	 */
	static double inverseCyclicPosition( final double t, final double[] colorPositions, final boolean treatMinAsBackground )
	{
		final int n = colorPositions.length;
		if ( n <= 1 )
			return treatMinAsBackground ? 1 : 0;

		final double raw;
		if ( t >= colorPositions[ n - 1 ] )
		{
			raw = n - 1;
		}
		else
		{
			int k = 0;
			while ( k < n - 2 && colorPositions[ k + 1 ] < t )
				k++;
			final double span = colorPositions[ k + 1 ] - colorPositions[ k ];
			final double frac = span > 0 ? ( t - colorPositions[ k ] ) / span : 0.0;
			raw = k + frac;
		}
		return treatMinAsBackground ? raw + 1 : raw;
	}

	/**
	 * Positions {@code 0, 1/(n-1), 2/(n-1), ..., 1} for {@code n} evenly
	 * spaced colors ({@code n = max(1, lutColorCount)}), matching how
	 * {@link net.imglib2.display.ColorTable8} and similar fixed-resolution
	 * color tables are laid out.
	 */
	private static double[] uniformColorPositions( final int lutColorCount )
	{
		final int n = Math.max( 1, lutColorCount );
		final double[] positions = new double[ n ];
		for ( int i = 0; i < n; i++ )
			positions[ i ] = n > 1 ? i / ( double ) ( n - 1 ) : 0.0;
		return positions;
	}

	/**
	 * Notify listeners that the curve's control points were edited directly
	 * (e.g. by dragging in a UI), without going through {@link #applyPreset}.
	 */
	public void notifyCurveEdited()
	{
		fireChangeListeners();
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
