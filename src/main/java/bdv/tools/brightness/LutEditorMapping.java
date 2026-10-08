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
 * The LUT editor's editable mapping state, turned into a {@code PaletteWrapper}
 * by {@link PaletteWrapperBuilder}. A continuous palette is shaped by a
 * {@link Curve}; a discrete one by a {@link #getStepSize() step size} in raw
 * units, since its scheme floors to a stop anyway.
 */
public class LutEditorMapping
{
	/**
	 * Step size sentinel resolved by {@link PaletteWrapperBuilder} via
	 * {@link StepPresetFunc#defaultStepSize}; the range and stop count needed
	 * for that are not part of this model.
	 */
	public static final double AUTO_STEP_SIZE = 0.0;

	public static final int DEFAULT_LEFT_SPECIAL_COLOR = 0xff000000;

	public static final int DEFAULT_RIGHT_SPECIAL_COLOR = 0xffffffff;

	private final Curve curve = new Curve();

	/** SPECIAL by default, so out-of-range values stand out from the palette's edge colors. */
	private BoundaryCondition leftBoundaryCondition = BoundaryCondition.SPECIAL;

	private BoundaryCondition rightBoundaryCondition = BoundaryCondition.SPECIAL;

	private int leftSpecialColor = DEFAULT_LEFT_SPECIAL_COLOR;

	private int rightSpecialColor = DEFAULT_RIGHT_SPECIAL_COLOR;

	/** Follows the chosen palette's declared kind. */
	private boolean discrete = false;

	/** Raw values per color stop when {@link #isDiscrete()}, or {@link #AUTO_STEP_SIZE}. */
	private double stepSize = AUTO_STEP_SIZE;

	/** The shape {@link #curve} was last seeded from. */
	private PresetShape preset;

	private final List< Runnable > changeListeners = new ArrayList<>();

	public LutEditorMapping()
	{
		applyPreset( PresetShape.LINEAR );
	}

	/** Only used for a continuous palette. */
	public Curve getCurve()
	{
		return curve;
	}

	// -- boundary conditions -------------------------------------------------

	public BoundaryCondition getLeftBoundaryCondition()
	{
		return leftBoundaryCondition;
	}

	public void setLeftBoundaryCondition( final BoundaryCondition leftBoundaryCondition )
	{
		this.leftBoundaryCondition = Objects.requireNonNull( leftBoundaryCondition, "leftBoundaryCondition" );
		fireChangeListeners();
	}

	public BoundaryCondition getRightBoundaryCondition()
	{
		return rightBoundaryCondition;
	}

	public void setRightBoundaryCondition( final BoundaryCondition rightBoundaryCondition )
	{
		this.rightBoundaryCondition = Objects.requireNonNull( rightBoundaryCondition, "rightBoundaryCondition" );
		fireChangeListeners();
	}

	public int getLeftSpecialColor()
	{
		return leftSpecialColor;
	}

	public void setLeftSpecialColor( final int leftSpecialColor )
	{
		this.leftSpecialColor = leftSpecialColor;
		fireChangeListeners();
	}

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

	public boolean isDiscrete()
	{
		return discrete;
	}

	/** Turning this on resets the unused curve to {@link PresetShape#LINEAR}. */
	public void setDiscrete( final boolean discrete )
	{
		this.discrete = discrete;
		if ( discrete )
			applyPreset( PresetShape.LINEAR ); // also fires change listeners
		else
			fireChangeListeners();
	}

	public double getStepSize()
	{
		return stepSize;
	}

	/** @param stepSize raw values per color stop; non-positive means {@link #AUTO_STEP_SIZE}. */
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

	/** Replaces the curve's control points with the preset's shape. */
	public void applyPreset( final PresetShape preset )
	{
		this.preset = preset;
		curve.setPoints( preset.xs(), preset.ys() );
		fireChangeListeners();
	}

	/** Flips the current curve vertically, including hand-dragged edits. */
	public void invertCurve()
	{
		curve.invert();
		fireChangeListeners();
	}

	// -- bulk state ----------------------------------------------------------

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

	/** Whether the editor still matches a saved configuration; not a general {@code equals}. */
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

	/** Call after editing the curve's points directly. */
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
