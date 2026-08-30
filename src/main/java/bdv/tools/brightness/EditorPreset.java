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

/**
 * A named, reusable snapshot of the LUT editor's "look" settings: which
 * palette, range mode, background handling, and curve shape to use --
 * everything in {@link LutEditorDialog}'s "Mapping" panel except the actual
 * input value range (min/max), which is left alone since it is specific to
 * whatever source's data is currently being edited, not part of a reusable
 * preset.
 * <p>
 * A plain data class (Gson-serialized field-for-field by {@link EditorPresets});
 * field names are also the JSON keys, so renaming a field changes the file
 * format.
 */
public class EditorPreset
{
	private String name;

	private String paletteName;

	private boolean cyclic;

	private boolean treatMinAsBackground;

	private int backgroundColor;

	private double[] curveXs;

	private int[] curveYs;

	/**
	 * No-arg constructor for Gson deserialization; fields are otherwise
	 * immutable (see the other constructor).
	 */
	EditorPreset()
	{}

	public EditorPreset( final String name, final String paletteName, final boolean cyclic,
			final boolean treatMinAsBackground, final int backgroundColor,
			final double[] curveXs, final int[] curveYs )
	{
		this.name = name;
		this.paletteName = paletteName;
		this.cyclic = cyclic;
		this.treatMinAsBackground = treatMinAsBackground;
		this.backgroundColor = backgroundColor;
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

	public boolean isCyclic()
	{
		return cyclic;
	}

	public boolean isTreatMinAsBackground()
	{
		return treatMinAsBackground;
	}

	public int getBackgroundColor()
	{
		return backgroundColor;
	}

	public double[] getCurveXs()
	{
		return curveXs;
	}

	public int[] getCurveYs()
	{
		return curveYs;
	}
}
