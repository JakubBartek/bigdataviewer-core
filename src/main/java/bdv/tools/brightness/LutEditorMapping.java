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
import java.util.Objects;

import bdv.tools.brightness.palette.BoundaryCondition;
import bdv.tools.brightness.presetfunc.StepPresetFunc;

/**
 * The LUT editor's editable mapping state: how a raw source value should be
 * turned into a color -- what happens at each end of the input range, whether
 * the palette is discrete (categorical) or continuous, and the shape in
 * between.
 * <p>
 * That shape is specified two different ways depending on the palette kind,
 * because the two ask genuinely different questions:
 * <ul>
 * <li><b>continuous</b> (a gradient palette): a transfer {@link Curve},
 * seeded from a {@link PresetShape} and further draggable -- "what curve
 * reshapes this gradient".</li>
 * <li><b>discrete</b> (a categorical palette): a {@link #getStepSize() step
 * size} in raw units -- "how many raw values does one color cover". A curve
 * would be meaningless here, since the color scheme floors its value to a
 * stop anyway.</li>
 * </ul>
 * <p>
 * This is a pure configuration holder edited by {@link LutEditorDialog} and
 * {@link MappingCurvePanel}; it does not perform the raw-value-to-color
 * mapping itself. That is done by the color-mapping architecture: the editor's
 * state is translated into a {@code PaletteWrapper} by
 * {@link PaletteWrapperBuilder}, and the wrapper is what actually renders (see
 * {@code PaletteConverter}).
 */
public class LutEditorMapping
{
	/**
	 * {@link #getStepSize()} value meaning "no explicit choice": let
	 * {@link PaletteWrapperBuilder} use {@link StepPresetFunc#defaultStepSize},
	 * which spreads the palette exactly once across the input range. Kept as a
	 * sentinel rather than a resolved number because the range and the palette's
	 * stop count -- both needed to resolve it -- are deliberately not part of
	 * this model.
	 */
	public static final double AUTO_STEP_SIZE = 0.0;

	/** The default {@link BoundaryCondition#SPECIAL} color at either end: opaque black. */
	public static final int DEFAULT_SPECIAL_COLOR = 0xff000000;

	private final Curve curve = new Curve();

	/**
	 * What happens to raw values below the input range. Passed straight through
	 * to the rendered wrapper by {@link PaletteWrapperBuilder}:
	 * {@link BoundaryCondition#CLAMP} holds the first color,
	 * {@link BoundaryCondition#CYCLE} wraps them back around the range, and
	 * {@link BoundaryCondition#SPECIAL} paints {@link #getLeftSpecialColor()}
	 * instead of any palette color at all (how a label image's background value
	 * is given a dedicated color).
	 */
	private BoundaryCondition leftBoundaryCondition = BoundaryCondition.CLAMP;

	/** As {@link #leftBoundaryCondition}, for raw values above the input range. */
	private BoundaryCondition rightBoundaryCondition = BoundaryCondition.CLAMP;

	/** The color painted below the range when {@link #leftBoundaryCondition} is {@link BoundaryCondition#SPECIAL}, packed as ARGB. */
	private int leftSpecialColor = DEFAULT_SPECIAL_COLOR;

	/** The color painted above the range when {@link #rightBoundaryCondition} is {@link BoundaryCondition#SPECIAL}, packed as ARGB. */
	private int rightSpecialColor = DEFAULT_SPECIAL_COLOR;

	/**
	 * Whether the palette is used as discrete, individually chosen colors
	 * (a categorical palette like tab10, where the mapped value snaps to a
	 * stop) rather than a smoothly interpolated gradient. Follows the chosen
	 * palette's own declared kind; picked up by {@link PaletteWrapperBuilder}
	 * to choose a discrete vs. continuous color scheme -- and, with it, whether
	 * {@link #getStepSize()} or {@link #getCurve()} defines the shape (see the
	 * class javadoc).
	 */
	private boolean discrete = false;

	/**
	 * How many raw values one color stop covers, when {@link #isDiscrete()};
	 * {@link #AUTO_STEP_SIZE} to let the range and palette decide. Ignored
	 * entirely for a continuous palette, which uses {@link #getCurve()} instead.
	 */
	private double stepSize = AUTO_STEP_SIZE;

	/** The shape {@link #curve} was last seeded from; only meaningful for a continuous palette. */
	private PresetShape preset;

	private final List< Runnable > changeListeners = new ArrayList<>();

	public LutEditorMapping()
	{
		applyPreset( PresetShape.LINEAR );
	}

	/** The transfer curve, defining the shape for a <em>continuous</em> palette only; see the class javadoc. */
	public Curve getCurve()
	{
		return curve;
	}

	// -- boundary conditions -------------------------------------------------

	/** What happens to raw values below the input range; see the field javadoc. */
	public BoundaryCondition getLeftBoundaryCondition()
	{
		return leftBoundaryCondition;
	}

	public void setLeftBoundaryCondition( final BoundaryCondition leftBoundaryCondition )
	{
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		fireChangeListeners();
	}

	/** What happens to raw values above the input range; see the field javadoc. */
	public BoundaryCondition getRightBoundaryCondition()
	{
		return rightBoundaryCondition;
	}

	public void setRightBoundaryCondition( final BoundaryCondition rightBoundaryCondition )
	{
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
		fireChangeListeners();
	}

	/** The below-range {@link BoundaryCondition#SPECIAL} color, packed as ARGB. */
	public int getLeftSpecialColor()
	{
		return leftSpecialColor;
	}

	public void setLeftSpecialColor( final int leftSpecialColor )
	{
		this.leftSpecialColor = leftSpecialColor;
		fireChangeListeners();
	}

	/** The above-range {@link BoundaryCondition#SPECIAL} color, packed as ARGB. */
	public int getRightSpecialColor()
	{
		return rightSpecialColor;
	}

	public void setRightSpecialColor( final int rightSpecialColor )
	{
		this.rightSpecialColor = rightSpecialColor;
		fireChangeListeners();
	}

	// -- discrete vs continuous ----------------------------------------------

	/** Whether the palette is used as discrete (categorical) colors rather than a smooth gradient; see the field javadoc. */
	public boolean isDiscrete()
	{
		return discrete;
	}

	/**
	 * @param discrete see the field javadoc. Turning this on resets the curve to
	 *                 {@link PresetShape#LINEAR}: the curve is not what defines a
	 *                 discrete palette's mapping ({@link #getStepSize()} is), so
	 *                 leaving a warped shape behind would only mislead if the
	 *                 palette later went back to continuous.
	 */
	public void setDiscrete( final boolean discrete )
	{
		this.discrete = discrete;
		if ( discrete )
			applyPreset( PresetShape.LINEAR ); // also fires change listeners
		else
			fireChangeListeners();
	}

	/**
	 * How many raw values one color stop covers for a discrete palette, or
	 * {@link #AUTO_STEP_SIZE} if none was chosen; see the field javadoc.
	 */
	public double getStepSize()
	{
		return stepSize;
	}

	/**
	 * @param stepSize raw values per color stop; {@link #AUTO_STEP_SIZE} (or any
	 *                 non-positive value) to go back to letting the range and
	 *                 palette decide.
	 */
	public void setStepSize( final double stepSize )
	{
		this.stepSize = stepSize > 0.0 ? stepSize : AUTO_STEP_SIZE;
		fireChangeListeners();
	}

	// -- curve ---------------------------------------------------------------

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

	// -- bulk state ----------------------------------------------------------

	/**
	 * Copy all mapping state (boundary conditions and their colors, discrete
	 * flag, step size, preset and curve control points) from another model into
	 * this one.
	 */
	public void copyFrom( final LutEditorMapping other )
	{
		this.leftBoundaryCondition = other.leftBoundaryCondition;
		this.rightBoundaryCondition = other.rightBoundaryCondition;
		this.leftSpecialColor = other.leftSpecialColor;
		this.rightSpecialColor = other.rightSpecialColor;
		this.discrete = other.discrete;
		this.stepSize = other.stepSize;
		this.preset = other.preset;
		this.curve.setPoints( other.curve.xsArray(), other.curve.ysArray() );
		fireChangeListeners();
	}

	/**
	 * Whether {@code other} represents the same mapping as this one -- used to
	 * detect unapplied edits worth warning about before discarding them, rather
	 * than a general-purpose {@code equals}.
	 */
	public boolean hasSameState( final LutEditorMapping other )
	{
		return leftBoundaryCondition == other.leftBoundaryCondition
				&& rightBoundaryCondition == other.rightBoundaryCondition
				&& leftSpecialColor == other.leftSpecialColor
				&& rightSpecialColor == other.rightSpecialColor
				&& discrete == other.discrete
				&& Double.compare( stepSize, other.stepSize ) == 0
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
