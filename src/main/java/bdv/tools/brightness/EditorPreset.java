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

import bdv.tools.brightness.palette.BoundaryCondition;

/**
 * A named, reusable snapshot of the LUT editor's "look" settings: which
 * palette, what happens at each end of the input range, and the shape in
 * between (a curve for a continuous palette, a step size for a discrete one)
 * -- everything in {@link LutEditorDialog}'s "Mapping" panel except the actual
 * input value range (min/max), which is left alone since it is specific to
 * whatever source's data is currently being edited, not part of a reusable
 * preset.
 * <p>
 * A plain data class (Gson-serialized field-for-field by {@link EditorPresets});
 * field names are also the JSON keys, so renaming a field changes the file
 * format.
 * <p>
 * <b>Legacy files.</b> Presets written before per-end boundary conditions
 * existed carry {@code cyclic}/{@code treatMinAsBackground}/{@code backgroundColor}
 * instead. Those keys are still read (see {@link #getLeftBoundaryCondition()}),
 * so an older user-saved setting keeps working, but they are never written
 * again: every field below is a boxed type precisely so Gson can tell "absent"
 * from "false"/"0" on the way in, and so the legacy keys can be left
 * {@code null} -- and thus omitted -- on the way out.
 */
public class EditorPreset
{
	private String name;

	private String paletteName;

	/** {@link BoundaryCondition#name()}; {@code null} in a legacy file, see the class javadoc. */
	private String leftBoundaryCondition;

	/** {@link BoundaryCondition#name()}; {@code null} in a legacy file, see the class javadoc. */
	private String rightBoundaryCondition;

	private Integer leftSpecialColor;

	private Integer rightSpecialColor;

	/** Raw values per color stop for a discrete palette; {@code null} means {@link LutEditorMapping#AUTO_STEP_SIZE}. */
	private Double stepSize;

	private double[] curveXs;

	private int[] curveYs;

	// -- legacy keys, read but never written; see the class javadoc -----------

	private Boolean cyclic;

	private Boolean treatMinAsBackground;

	private Integer backgroundColor;

	/**
	 * No-arg constructor for Gson deserialization; fields are otherwise
	 * immutable (see the other constructor).
	 */
	EditorPreset()
	{}

	public EditorPreset( final String name, final String paletteName,
			final BoundaryCondition leftBoundaryCondition, final BoundaryCondition rightBoundaryCondition,
			final int leftSpecialColor, final int rightSpecialColor, final double stepSize,
			final double[] curveXs, final int[] curveYs )
	{
		this.name = name;
		this.paletteName = paletteName;
		this.leftBoundaryCondition = leftBoundaryCondition.name();
		this.rightBoundaryCondition = rightBoundaryCondition.name();
		this.leftSpecialColor = leftSpecialColor;
		this.rightSpecialColor = rightSpecialColor;
		this.stepSize = stepSize;
		this.curveXs = curveXs;
		this.curveYs = curveYs;
	}

	public String getName()
	{
		return name;
	}

	public String getPaletteName()
	{
		return paletteName;
	}

	/**
	 * What happens below the input range. Falls back to the legacy keys when
	 * this preset predates them: a treat-min-as-background preset becomes
	 * {@link BoundaryCondition#SPECIAL} (which is what that setting always
	 * meant), otherwise a cyclic one becomes {@link BoundaryCondition#CYCLE}.
	 */
	public BoundaryCondition getLeftBoundaryCondition()
	{
		if ( leftBoundaryCondition != null )
			return parse( leftBoundaryCondition );
		if ( Boolean.TRUE.equals( treatMinAsBackground ) )
			return BoundaryCondition.SPECIAL;
		return legacyRangeMode();
	}

	/** As {@link #getLeftBoundaryCondition()}, above the range -- where the legacy format could only ever express clamp-or-cycle. */
	public BoundaryCondition getRightBoundaryCondition()
	{
		if ( rightBoundaryCondition != null )
			return parse( rightBoundaryCondition );
		return legacyRangeMode();
	}

	/** The below-range {@link BoundaryCondition#SPECIAL} color, packed as ARGB; the legacy {@code backgroundColor} if this preset predates it. */
	public int getLeftSpecialColor()
	{
		if ( leftSpecialColor != null )
			return leftSpecialColor;
		if ( backgroundColor != null )
			return backgroundColor;
		return LutEditorMapping.DEFAULT_SPECIAL_COLOR;
	}

	/** The above-range {@link BoundaryCondition#SPECIAL} color, packed as ARGB. The legacy format had no equivalent, so an older preset gets the default. */
	public int getRightSpecialColor()
	{
		return rightSpecialColor != null ? rightSpecialColor : LutEditorMapping.DEFAULT_SPECIAL_COLOR;
	}

	/** Raw values per color stop for a discrete palette, or {@link LutEditorMapping#AUTO_STEP_SIZE} if this preset does not pin one down. */
	public double getStepSize()
	{
		return stepSize != null ? stepSize : LutEditorMapping.AUTO_STEP_SIZE;
	}

	public double[] getCurveXs()
	{
		return curveXs;
	}

	public int[] getCurveYs()
	{
		return curveYs;
	}

	/** The legacy {@code cyclic} flag as a boundary condition, for both ends alike -- all that format could express. */
	private BoundaryCondition legacyRangeMode()
	{
		return Boolean.TRUE.equals( cyclic ) ? BoundaryCondition.CYCLE : BoundaryCondition.CLAMP;
	}

	/**
	 * A stored boundary-condition name, defaulting to {@link BoundaryCondition#CLAMP}
	 * if it names nothing we know: a preset file is user-editable, and one bad
	 * key is not worth failing the whole load over.
	 */
	private static BoundaryCondition parse( final String name )
	{
		try
		{
			return BoundaryCondition.valueOf( name );
		}
		catch ( final IllegalArgumentException e )
		{
			return BoundaryCondition.CLAMP;
		}
	}
}
