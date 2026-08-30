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

import java.util.Arrays;

import bdv.tools.brightness.colorscheme.ColorScheme;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.palette.BoundaryCondition;
import bdv.tools.brightness.palette.PaletteWrapper;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;
import net.imglib2.display.ColorTable;

/**
 * Translates the LUT editor's {@link LutEditorMapping}-plus-{@link ColorTable}
 * state into a {@link PaletteWrapper} of the color-mapping architecture that
 * actually renders (see {@link PaletteConverter}). The single bridge between
 * the editor's model and the render model, kept out of both the Swing dialog
 * (so it can be unit-tested) and the {@code palette} package (so that package
 * stays free of any dependency on the editor's model).
 * <p>
 * The mapping {@link Curve} always carries over as a {@link CustomInterpPresetFunc}
 * (so presets, hand-dragged points and inversion are all preserved), and the
 * mapping's {@link LutEditorMapping#isDiscrete()} flag only picks the color scheme
 * it feeds -- which is the discrete-vs-continuous distinction at the heart of
 * the new design:
 * <ul>
 * <li>continuous (a gradient palette, e.g. viridis) uses a
 * {@link ContinuousColorScheme}: the curve's value is interpolated between
 * stops into a smooth gradient.</li>
 * <li>discrete (a categorical palette, e.g. tab10, or a label image) uses a
 * {@link DiscreteColorScheme}: the very same curve value is floored to a single
 * stop, so each raw value shows one distinct color.</li>
 * </ul>
 * Either way, a cyclic range becomes {@link BoundaryCondition#CYCLE}
 * (otherwise {@link BoundaryCondition#CLAMP}), and a treat-min-as-background
 * color becomes a left {@link BoundaryCondition#SPECIAL} with that color.
 * Features the new model has no equivalent for (the cyclic period) are not
 * represented, so the result is close but not identical to the old converter's.
 */
public final class PaletteWrapperBuilder
{
	private PaletteWrapperBuilder()
	{
	}

	/** Build the {@link PaletteWrapper} equivalent of {@code palette} + {@code mapping} over the display range {@code [min, max]}; see the class javadoc. */
	public static PaletteWrapper build( final ColorTable palette, final LutEditorMapping mapping, final double min, final double max )
	{
		// The only discrete-vs-continuous difference is the color scheme: a
		// discrete scheme floors the curve's value to a stop, a continuous one
		// interpolates it. The rest of the pipeline is identical.
		final ColorScheme scheme = mapping.isDiscrete()
				? new DiscreteColorScheme( palette )
				: new ContinuousColorScheme( palette );

		final double hi = max > min ? max : min + 1; // CustomInterpPresetFunc requires max > min
		final CustomInterpPresetFunc curve = new CustomInterpPresetFunc( ( float ) min, ( float ) hi, scheme.getPaletteRangeLength() );
		final Knots knots = sanitizedKnots( mapping );
		curve.setKnots( knots.ts, knots.values );

		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, curve );
		final BoundaryCondition boundary = mapping.isCyclic() ? BoundaryCondition.CYCLE : BoundaryCondition.CLAMP;
		wrapper.setRightBoundaryCondition( boundary );
		if ( mapping.isTreatMinAsBackground() )
		{
			// Below-range values render as the background color; the new model's
			// closest equivalent to treat-min-as-background.
			wrapper.setLeftBoundaryCondition( BoundaryCondition.SPECIAL );
			wrapper.setLeftSpecialColor( mapping.getBackgroundColor() );
		}
		else
		{
			wrapper.setLeftBoundaryCondition( boundary );
		}
		return wrapper;
	}

	/** The knot arrays {@link CustomInterpPresetFunc#setKnots} needs; see {@link #sanitizedKnots}. */
	private static final class Knots
	{
		final double[] ts;
		final double[] values;

		Knots( final double[] ts, final double[] values )
		{
			this.ts = ts;
			this.values = values;
		}
	}

	/**
	 * The mapping curve as {@link CustomInterpPresetFunc}-ready knots: x
	 * positions clamped to {@code [0,1]} and forced strictly ascending (dropping
	 * any that would not advance), outputs scaled from {@code [0,255]} to
	 * {@code [0,1]}. Falls back to a linear pair if fewer than two usable knots
	 * survive, so a degenerate curve can never make the wrapper throw during a
	 * live edit.
	 */
	private static Knots sanitizedKnots( final LutEditorMapping mapping )
	{
		final double[] xs = mapping.getCurve().xsArray();
		final int[] ys = mapping.getCurve().ysArray();
		final double[] ts = new double[ xs.length ];
		final double[] values = new double[ xs.length ];
		int n = 0;
		for ( int i = 0; i < xs.length; i++ )
		{
			final double t = Math.max( 0.0, Math.min( 1.0, xs[ i ] ) );
			if ( n > 0 && !( t > ts[ n - 1 ] ) )
				continue; // keep strictly ascending; a non-advancing point is dropped
			ts[ n ] = t;
			values[ n ] = Math.max( 0.0, Math.min( 1.0, ys[ i ] / 255.0 ) );
			n++;
		}
		if ( n < 2 )
			return new Knots( new double[] { 0.0, 1.0 }, new double[] { 0.0, 1.0 } );
		return new Knots( Arrays.copyOf( ts, n ), Arrays.copyOf( values, n ) );
	}
}
