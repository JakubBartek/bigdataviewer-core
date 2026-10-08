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
 * Translates the LUT editor's {@link LutEditorMapping} and {@link Palette}
 * into the {@link PaletteWrapper} that renders (see {@link PaletteConverter}).
 * {@link LutEditorMapping#isDiscrete()} picks both halves:
 * <ul>
 * <li>continuous: {@link ContinuousColorScheme} fed by the editor's
 * {@link Curve} as a {@link CustomInterpPresetFunc}.</li>
 * <li>discrete: {@link DiscreteColorScheme} fed by a {@link StepPresetFunc};
 * the curve is ignored.</li>
 * </ul>
 * Boundary conditions and their colors pass through unchanged.
 */
public final class PaletteWrapperBuilder
{
	private PaletteWrapperBuilder()
	{
	}

	/**
	 * Returns the concrete type because {@code MappingCurvePanel} needs the
	 * preset function's domain to draw where the boundary condition takes over.
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

	/** {@code hi} is only used to resolve {@link LutEditorMapping#AUTO_STEP_SIZE}. */
	private static PresetFunc stepFunc( final LutEditorMapping mapping, final ColorScheme scheme, final double lo, final double hi )
	{
		final int paletteRangeLength = scheme.getPaletteRangeLength();
		final double chosen = mapping.getStepSize();
		final double stepSize = chosen > 0.0 ? chosen : StepPresetFunc.defaultStepSize( lo, hi, paletteRangeLength );
		return new StepPresetFunc( lo, paletteRangeLength, stepSize );
	}

	private static PresetFunc curveFunc( final LutEditorMapping mapping, final ColorScheme scheme, final double lo, final double hi )
	{
		final CustomInterpPresetFunc curve = new CustomInterpPresetFunc( lo, hi, scheme.getPaletteRangeLength() );
		final Knots knots = sanitizedKnots( mapping );
		curve.setKnots( knots.ts, knots.values );
		return curve;
	}

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
	 * Clamps and de-duplicates the curve into valid knots, falling back to
	 * linear, so a degenerate curve never throws during a live edit.
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
				continue; // keep strictly ascending
			ts[ n ] = t;
			values[ n ] = Math.max( 0.0, Math.min( 1.0, ys[ i ] / 255.0 ) );
			n++;
		}
		if ( n < 2 )
			return new Knots( new double[] { 0.0, 1.0 }, new double[] { 0.0, 1.0 } );
		return new Knots( Arrays.copyOf( ts, n ), Arrays.copyOf( values, n ) );
	}
}
