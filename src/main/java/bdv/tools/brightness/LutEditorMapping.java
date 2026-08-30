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
import java.util.Arrays;
import java.util.List;

/**
 * The LUT editor's editable mapping state: a transfer {@link Curve} plus how a
 * raw source value should be turned into a color -- fit-vs-cyclic range
 * handling, whether the palette is discrete (categorical) or continuous, and
 * an optional dedicated background color for the minimum.
 * <p>
 * This is a pure configuration holder edited by {@link LutEditorDialog} and
 * {@link MappingCurvePanel}; it no longer performs the raw-value-to-color
 * mapping itself. That is done by the new color-mapping architecture: the
 * editor's state is translated into a {@code PaletteWrapper} by
 * {@link PaletteWrapperBuilder}, and the wrapper is what actually renders (see
 * {@code PaletteConverter}). The curve is initialized from a
 * {@link PresetShape} but can be further customized by adding/moving/removing
 * its control points.
 */
public class LutEditorMapping
{
	private final Curve curve = new Curve();

	/**
	 * Whether raw values outside {@code [min, max]} wrap around the range (the
	 * palette repeats) rather than clamping to the nearest end. Picked up by
	 * {@link PaletteWrapperBuilder} as {@code CYCLE} vs. {@code CLAMP}.
	 */
	private boolean cyclic = false;

	/**
	 * When {@code true}, raw source values below the range map to
	 * {@link #backgroundColor} instead of the palette. Useful for
	 * label/segmentation images where the min value (e.g. 0) marks background.
	 */
	private boolean treatMinAsBackground = false;

	/**
	 * The dedicated background color used when {@link #treatMinAsBackground}
	 * applies, packed as ARGB (see {@link net.imglib2.type.numeric.ARGBType}).
	 * Defaults to opaque black.
	 */
	private int backgroundColor = 0xff000000;

	/**
	 * Whether the palette is used as discrete, individually chosen colors
	 * (a categorical palette like tab10, where the mapped value snaps to a
	 * stop) rather than a smoothly interpolated gradient. Follows the chosen
	 * palette's own declared kind; picked up by {@link PaletteWrapperBuilder}
	 * to choose a discrete vs. continuous color scheme.
	 * <p>
	 * A non-linear curve shape (a warped preset, or hand-dragged points) is
	 * only meaningful for a continuous palette, where it reshapes a gradient;
	 * for a discrete one the color scheme already floors the curve's value to
	 * a stop, so a non-linear shape would just make the raw-to-stop mapping
	 * uneven for no visible benefit. So {@link #setDiscrete(boolean)} always
	 * resets the curve to {@link PresetShape#LINEAR} when turning this on.
	 */
	private boolean discrete = false;

	private PresetShape preset;

	private final List< Runnable > changeListeners = new ArrayList<>();

	public LutEditorMapping()
	{
		applyPreset( PresetShape.LINEAR );
	}

	public Curve getCurve()
	{
		return curve;
	}

	/** Whether out-of-range values wrap (cyclic) rather than clamp (fit); see the field javadoc. */
	public boolean isCyclic()
	{
		return cyclic;
	}

	public void setCyclic( final boolean cyclic )
	{
		this.cyclic = cyclic;
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

	/** Whether the palette is used as discrete (categorical) colors rather than a smooth gradient; see the field javadoc. */
	public boolean isDiscrete()
	{
		return discrete;
	}

	/**
	 * @param discrete see the field javadoc. Turning this on resets the curve
	 *                 to {@link PresetShape#LINEAR}, discarding whatever shape
	 *                 it had -- see the field javadoc for why.
	 */
	public void setDiscrete( final boolean discrete )
	{
		this.discrete = discrete;
		if ( discrete )
			applyPreset( PresetShape.LINEAR ); // also fires change listeners
		else
			fireChangeListeners();
	}

	public PresetShape getPreset()
	{
		return preset;
	}

	/**
	 * Apply a preset, replacing the current curve control points with the
	 * preset's shape. The control points can still be dragged afterwards.
	 */
	public void applyPreset( final PresetShape preset )
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
	 * Copy all mapping state (cyclic, background, discrete flag, preset and
	 * curve control points) from another model into this one.
	 */
	public void copyFrom( final LutEditorMapping other )
	{
		this.cyclic = other.cyclic;
		this.treatMinAsBackground = other.treatMinAsBackground;
		this.backgroundColor = other.backgroundColor;
		this.discrete = other.discrete;
		this.preset = other.preset;
		this.curve.setPoints( other.curve.xsArray(), other.curve.ysArray() );
		fireChangeListeners();
	}

	/**
	 * Whether {@code other} represents the same mapping as this one (cyclic,
	 * background handling, discrete flag, preset and curve shape) -- used to
	 * detect unapplied edits worth warning about before discarding them, rather
	 * than a general-purpose {@code equals}.
	 */
	public boolean hasSameState( final LutEditorMapping other )
	{
		return cyclic == other.cyclic
				&& treatMinAsBackground == other.treatMinAsBackground
				&& backgroundColor == other.backgroundColor
				&& discrete == other.discrete
				&& preset == other.preset
				&& Arrays.equals( curve.xsArray(), other.curve.xsArray() )
				&& Arrays.equals( curve.ysArray(), other.curve.ysArray() );
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
