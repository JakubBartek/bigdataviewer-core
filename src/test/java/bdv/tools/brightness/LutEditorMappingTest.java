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

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link LutEditorMapping}, the LUT editor's editable mapping state.
 * It is now a pure configuration holder (the raw-value-to-color mapping itself
 * lives in the new color-mapping architecture, reached via
 * {@link PaletteWrapperBuilder}); these cover the state it owns -- curve,
 * presets, range mode, discrete flag and background -- and its copy/equality
 * helpers.
 */
public class LutEditorMappingTest
{
	@Test
	public void testDefaultIsLinear()
	{
		final LutEditorMapping model = new LutEditorMapping();
		Assert.assertEquals( PresetShape.LINEAR, model.getPreset() );

		// The default curve is the identity ramp, exactly its two endpoints.
		final Curve curve = model.getCurve();
		Assert.assertEquals( 2, curve.getPointCount() );
		Assert.assertEquals( 0, curve.evaluate( 0.0 ) );
		Assert.assertEquals( 255, curve.evaluate( 1.0 ) );
		Assert.assertEquals( 128, curve.evaluate( 0.5 ), 1 );
	}

	@Test
	public void testApplyPresetChangesShape()
	{
		final LutEditorMapping model = new LutEditorMapping();
		model.applyPreset( PresetShape.LOG );

		Assert.assertEquals( PresetShape.LOG, model.getPreset() );
		final Curve curve = model.getCurve();
		Assert.assertEquals( 0, curve.evaluate( 0.0 ) );
		Assert.assertEquals( 255, curve.evaluate( 1.0 ) );
		// Log curve rises faster than linear near the low end.
		Assert.assertTrue( curve.evaluate( 0.1 ) > 26 );
	}

	@Test
	public void testLinearPresetIsExactlyTwoPoints()
	{
		Assert.assertArrayEquals( new double[] { 0.0, 1.0 }, PresetShape.LINEAR.xs(), 1e-9 );
		Assert.assertArrayEquals( new int[] { 0, 255 }, PresetShape.LINEAR.ys() );
	}

	@Test
	public void testInvertCurveFlipsMapping()
	{
		final LutEditorMapping model = new LutEditorMapping();
		final Curve curve = model.getCurve();

		// Default Linear preset increases; after inverting it must decrease.
		Assert.assertEquals( 0, curve.evaluate( 0.0 ) );
		Assert.assertEquals( 255, curve.evaluate( 1.0 ) );

		model.invertCurve();

		Assert.assertEquals( 255, curve.evaluate( 0.0 ) );
		Assert.assertEquals( 0, curve.evaluate( 1.0 ) );
	}

	@Test
	public void testInvertCurveAppliesOnTopOfHandDraggedEdits()
	{
		final LutEditorMapping model = new LutEditorMapping();
		final Curve curve = model.getCurve();
		curve.addPoint( 0.5, 200 );
		model.notifyCurveEdited();

		final int before = curve.evaluate( 0.5 );
		model.invertCurve();
		Assert.assertEquals( 255 - before, curve.evaluate( 0.5 ) );
	}

	@Test
	public void testCopyFromCopiesStateAndDoesNotAlias()
	{
		// Not discrete: setDiscrete(true) forces the curve back to LINEAR (see
		// testSetDiscreteForcesLinearCurve), which would fight the SIGMOID
		// preset set up below -- discrete's own copy behavior is covered by
		// testHasSameStateDetectsEachField instead.
		final LutEditorMapping source = new LutEditorMapping();
		source.applyPreset( PresetShape.SIGMOID );
		source.setCyclic( true );
		source.setTreatMinAsBackground( true );
		source.setBackgroundColor( 0xffaabbcc );

		final LutEditorMapping copy = new LutEditorMapping();
		copy.copyFrom( source );

		Assert.assertTrue( source.hasSameState( copy ) );
		Assert.assertEquals( source.getPreset(), copy.getPreset() );
		Assert.assertEquals( source.isCyclic(), copy.isCyclic() );
		Assert.assertEquals( source.isTreatMinAsBackground(), copy.isTreatMinAsBackground() );
		Assert.assertEquals( source.getBackgroundColor(), copy.getBackgroundColor() );

		// Mutating the source afterwards must not affect the copy.
		source.applyPreset( PresetShape.LINEAR );
		source.setCyclic( false );
		source.setTreatMinAsBackground( false );
		source.setBackgroundColor( 0xff000000 );
		Assert.assertEquals( PresetShape.SIGMOID, copy.getPreset() );
		Assert.assertTrue( copy.isCyclic() );
		Assert.assertTrue( copy.isTreatMinAsBackground() );
		Assert.assertEquals( 0xffaabbcc, copy.getBackgroundColor() );
		Assert.assertFalse( source.hasSameState( copy ) );
	}

	@Test
	public void testHasSameStateDetectsEachField()
	{
		final LutEditorMapping a = new LutEditorMapping();
		final LutEditorMapping b = new LutEditorMapping();
		Assert.assertTrue( a.hasSameState( b ) );

		b.setCyclic( true );
		Assert.assertFalse( a.hasSameState( b ) );
		b.setCyclic( false );
		Assert.assertTrue( a.hasSameState( b ) );

		b.setDiscrete( true );
		Assert.assertFalse( a.hasSameState( b ) );
		b.setDiscrete( false );

		b.getCurve().addPoint( 0.5, 100 );
		b.notifyCurveEdited();
		Assert.assertFalse( a.hasSameState( b ) );
	}

	@Test
	public void testSetDiscreteForcesLinearCurve()
	{
		final LutEditorMapping model = new LutEditorMapping();
		model.applyPreset( PresetShape.SIGMOID );
		model.getCurve().addPoint( 0.3, 200 ); // hand-dragged edit on top of the preset
		Assert.assertNotEquals( PresetShape.LINEAR, model.getPreset() );

		model.setDiscrete( true );

		Assert.assertEquals( PresetShape.LINEAR, model.getPreset() );
		final Curve curve = model.getCurve();
		Assert.assertEquals( 2, curve.getPointCount() );
		Assert.assertEquals( 0, curve.evaluate( 0.0 ) );
		Assert.assertEquals( 255, curve.evaluate( 1.0 ) );
	}

	@Test
	public void testSetDiscreteFalseDoesNotDisturbCurve()
	{
		final LutEditorMapping model = new LutEditorMapping();
		model.setDiscrete( true );
		model.setDiscrete( false );
		model.applyPreset( PresetShape.LOG );

		model.setDiscrete( false ); // already false: must be a no-op on the curve

		Assert.assertEquals( PresetShape.LOG, model.getPreset() );
	}

	@Test
	public void testChangeListenerFiresOnEdits()
	{
		final LutEditorMapping model = new LutEditorMapping();
		final int[] count = { 0 };
		model.addChangeListener( () -> count[ 0 ]++ );

		model.setCyclic( true );
		model.setDiscrete( true );
		model.applyPreset( PresetShape.LOG );
		model.notifyCurveEdited();

		Assert.assertEquals( 4, count[ 0 ] );
	}
}
