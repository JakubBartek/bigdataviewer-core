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
import bdv.tools.brightness.palette.PaletteWrapper;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;
import bdv.tools.brightness.presetfunc.PresetFunc;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.presetfunc.StepPresetFunc;

/**
 * Translates the LUT editor's {@link LutEditorMapping}-plus-{@link Palette}
 * state into a {@link PaletteWrapper} of the color-mapping architecture that
 * actually renders (see {@link PaletteConverter}). The single bridge between
 * the editor's model and the render model, kept out of both the Swing dialog
 * (so it can be unit-tested) and the {@code palette} package (so that package
 * stays free of any dependency on the editor's model).
 * <p>
 * The mapping's {@link LutEditorMapping#isDiscrete()} flag picks both halves of
 * the pipeline at once, which is the discrete-vs-continuous distinction at the
 * heart of the design:
 * <ul>
 * <li>continuous (a gradient palette, e.g. viridis): a
 * {@link ContinuousColorScheme} fed by the editor's {@link Curve} carried over
 * as a {@link CustomInterpPresetFunc}, so presets, hand-dragged points and
 * inversion are all preserved. The curve's value is interpolated between stops
 * into a smooth gradient.</li>
 * <li>discrete (a categorical palette, e.g. tab10, or a label image): a
 * {@link DiscreteColorScheme} fed by a {@link StepPresetFunc} built from the
 * mapping's {@link LutEditorMapping#getStepSize() step size}, so one color
 * covers that many raw values and the palette repeats across the range. The
 * curve is not consulted at all -- flooring to a stop is what makes a shape
 * pointless here.</li>
 * </ul>
 * The two {@link bdv.tools.brightness.palette.BoundaryCondition}s and their
 * colors are passed through verbatim: the editor edits exactly the render
 * model's own boundary vocabulary, so there is nothing to translate.
 */
public final class PaletteWrapperBuilder
{
	private PaletteWrapperBuilder()
	{
	}

	/**
	 * Build the {@link PaletteWrapper} equivalent of {@code palette} +
	 * {@code mapping} over the display range {@code [min, max]}; see the class
	 * javadoc.
	 * <p>
	 * Declared as the concrete {@link PresetPaletteWrapper} rather than the
	 * interface, because the editor's preview draws the shape of the mapping
	 * and not just its colors: it needs the {@code PresetFunc}'s own domain to
	 * know where the palette runs out and the boundary condition takes over
	 * (see {@code MappingCurvePanel}).
	 */
	public static PresetPaletteWrapper build( final Palette palette, final LutEditorMapping mapping, final double min, final double max )
	{
		final ColorScheme scheme = mapping.isDiscrete()
				? new DiscreteColorScheme( palette )
				: new ContinuousColorScheme( palette );

		final double lo = min;
		final double hi = max > min ? max : min + 1; // both preset functions require max > min
		final PresetFunc presetFunc = mapping.isDiscrete()
				? stepFunc( mapping, scheme, lo, hi )
				: curveFunc( mapping, scheme, lo, hi );

		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, presetFunc,
				mapping.getLeftBoundaryCondition(), mapping.getRightBoundaryCondition() );
		wrapper.setLeftSpecialColor( mapping.getLeftSpecialColor() );
		wrapper.setRightSpecialColor( mapping.getRightSpecialColor() );
		return wrapper;
	}

	/**
	 * The discrete palette's shape: one color per
	 * {@link LutEditorMapping#getStepSize()} raw values, resolving
	 * {@link LutEditorMapping#AUTO_STEP_SIZE} against the range and stop count
	 * the model deliberately does not know about.
	 * <p>
	 * Only {@code lo} reaches the function: a {@link StepPresetFunc} derives its
	 * own maximum from the step size and the stop count, so the display range's
	 * top is not part of what color a raw value gets (see that class's javadoc).
	 * {@code hi} is used solely to resolve an automatic step size into an
	 * explicit one -- the step size that would put the palette's far edge on
	 * {@code hi}. Past that edge the boundary condition takes over, repeating the
	 * palette (CYCLE) or holding the last color (CLAMP).
	 */
	private static PresetFunc stepFunc( final LutEditorMapping mapping, final ColorScheme scheme, final double lo, final double hi )
	{
		final int paletteRangeLength = scheme.getPaletteRangeLength();
		final double chosen = mapping.getStepSize();
		final double stepSize = chosen > 0.0 ? chosen : StepPresetFunc.defaultStepSize( lo, hi, paletteRangeLength );
		return new StepPresetFunc( lo, paletteRangeLength, stepSize );
	}

	/** The continuous palette's shape: the editor's curve, knot for knot. */
	private static PresetFunc curveFunc( final LutEditorMapping mapping, final ColorScheme scheme, final double lo, final double hi )
	{
		final CustomInterpPresetFunc curve = new CustomInterpPresetFunc( lo, hi, scheme.getPaletteRangeLength() );
		final Knots knots = sanitizedKnots( mapping );
		curve.setKnots( knots.ts, knots.values );
		return curve;
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
