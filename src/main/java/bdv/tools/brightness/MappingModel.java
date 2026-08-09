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
 * value lands exactly on a palette color, wrapping once per full trip through
 * the palette (e.g. label/segmentation ids cycling through a small
 * categorical palette).</li>
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
	 * When {@code true}, raw source values at or below {@code min} (the
	 * left/start value of the range) are always mapped to
	 * {@link #backgroundColor} instead of the palette -- see
	 * {@link #isBackgroundValue} for the exact threshold, which differs
	 * slightly by {@link #rangeMode}. Useful for label/segmentation images
	 * where the min value (e.g. 0) marks background.
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
	 * palette. Never applies unless {@link #isTreatMinAsBackground()} is
	 * enabled; when it is, the exact threshold depends on {@link #rangeMode}:
	 * <ul>
	 * <li>{@link RangeMode#FIT}: any raw value at or below {@code min}. FIT
	 * has no notion of discrete steps, so this is simply "below the bottom
	 * of the range".</li>
	 * <li>{@link RangeMode#CYCLIC}: any raw value below {@code min + 1} --
	 * i.e. the "reserved" unit interval {@code [min, min + 1)} itself (the
	 * left/start value of the range), and everything below {@code min} too
	 * (out-of-range values on the low side are background, not cycled;
	 * out-of-range values on the high side still cycle normally, same as
	 * when this option is off).
	 * <p>
	 * The {@code [min, min + 1)} part is not just the exact point
	 * {@code value == min}: {@link #cyclicPosition} only defines integer
	 * inputs exactly on a palette color, so without covering the whole unit
	 * interval, any non-integer value strictly between {@code min} and
	 * {@code min + 1} (e.g. continuous image data, or a continuous preview)
	 * would fall through to {@link #mapToLutIndex} and incorrectly render as
	 * the palette's *last* color, since that gap is exactly what is skipped
	 * to make the cycle start at min + 1.</li>
	 * </ul>
	 */
	public boolean isBackgroundValue( final double value, final double min )
	{
		if ( !treatMinAsBackground )
			return false;
		return rangeMode == RangeMode.CYCLIC ? value < min + 1 : value <= min;
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
	 * Flip the current curve vertically (see {@link Curve#invert()}), e.g.
	 * turning the default increasing linear ramp into a decreasing one.
	 * Applies on top of whatever shape the curve currently has -- including
	 * further hand-dragged edits -- not just a freshly applied preset.
	 */
	public void invertCurve()
	{
		curve.invert();
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
	 * Like {@link #mapToLutIndex}, but meant for an actual color lookup
	 * rather than drawing the curve's own shape (see
	 * {@link MappingCurvePanel#drawCurve}, which needs the continuously
	 * swept version instead). In {@link RangeMode#CYCLIC} with anything
	 * other than {@link ValueMatching#INTERPOLATE}, evaluates the curve at
	 * the raw value's own label's exact position instead of continuously
	 * sweeping across that label's whole raw-value interval.
	 * <p>
	 * Without this, a curve that decreases across a label's interval (e.g.
	 * after {@link #invertCurve()}) makes {@link ColorTableLut}'s Truncate
	 * lookup pick up the <em>previous</em> label's color for almost the
	 * entire interval -- the interval's own intended color is only reached
	 * for an instant right at its start, since Truncate holds whichever
	 * control point was most recently passed in increasing-position order,
	 * and a decreasing sweep passes the interval's own position immediately
	 * and keeps going. The net effect: one color barely appears while its
	 * neighbor visually dominates two labels' worth of space instead of one.
	 * Snapping to the label's exact position sidesteps this entirely, since
	 * the curve is then evaluated once per label rather than swept.
	 */
	public int mapToLutIndexForColor( final double value, final double min, final double max, final double[] colorPositions )
	{
		if ( rangeMode != RangeMode.CYCLIC || valueMatching == ValueMatching.INTERPOLATE )
			return mapToLutIndex( value, min, max, colorPositions );

		final double t = steppedCyclicPosition( value, min, colorPositions, treatMinAsBackground );
		return Math.max( 0, Math.min( 255, curve.evaluate( t ) ) );
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
		final double m = cyclicOffset( value, min, n, treatMinAsBackground );
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
	 * Like {@link #cyclicPosition}, but snapped to the exact position of
	 * whichever label {@code value} falls into, instead of interpolating
	 * towards the next one. Used by {@link #mapToLutIndexForColor} so every
	 * raw value within one label's unit interval resolves to that label's
	 * own color, regardless of the curve's local direction there.
	 */
	static double steppedCyclicPosition( final double value, final double min, final double[] colorPositions, final boolean treatMinAsBackground )
	{
		final int n = colorPositions.length;
		if ( n <= 1 )
			return 0.0;
		final double m = cyclicOffset( value, min, n, treatMinAsBackground );
		final int k = Math.min( n - 1, ( int ) Math.floor( m ) );
		return colorPositions[ k ];
	}

	/**
	 * Raw value's offset into the current cycle, in {@code [0, n)}. When
	 * {@code treatMinAsBackground}, the cycle instead starts at min+1, so
	 * all n palette colors remain reachable without min itself ever
	 * competing for one of them.
	 */
	private static double cyclicOffset( final double value, final double min, final int n, final boolean treatMinAsBackground )
	{
		final double shifted = value - min;
		double m = treatMinAsBackground ? ( shifted - 1 ) % n : shifted % n;
		if ( m < 0 )
			m += n;
		return m;
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
