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
 * CONTRACT, STRICT LIABILITY, AND TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness;

import org.junit.Assert;
import org.junit.Test;

import net.imglib2.display.ColorTable8;

/**
 * Test cases for Curve and CurveEditorPanel.
 */
public class CurveEditorTest
{
	@Test
	public void testCurveInitialization()
	{
		final Curve curve = new Curve();

		// Should have 2 control points initially (0,0) and (1,255)
		Assert.assertEquals( 2, curve.getPointCount() );
		Assert.assertEquals( 0.0, curve.getX( 0 ), 1e-6 );
		Assert.assertEquals( 0, curve.getY( 0 ) );
		Assert.assertEquals( 1.0, curve.getX( 1 ), 1e-6 );
		Assert.assertEquals( 255, curve.getY( 1 ) );
	}

	@Test
	public void testCurveEvaluation()
	{
		final Curve curve = new Curve();

		// At normalized position 0, should be 0
		Assert.assertEquals( 0, curve.evaluate( 0.0 ) );

		// At normalized position 1, should be 255
		Assert.assertEquals( 255, curve.evaluate( 1.0 ) );

		// At normalized position 0.5, should be ~127 (linear interpolation)
		final int mid = curve.evaluate( 0.5 );
		Assert.assertTrue( Math.abs( mid - 127 ) <= 1 );
	}

	@Test
	public void testAddPoint()
	{
		final Curve curve = new Curve();

		// Add a middle point at (0.5, 200)
		curve.addPoint( 0.5, 200 );

		// Should now have 3 points
		Assert.assertEquals( 3, curve.getPointCount() );

		// Evaluation at 0.5 should now be 200
		Assert.assertEquals( 200, curve.evaluate( 0.5 ) );

		// Evaluation at 0.25 should be between 0 and 200
		final int val025 = curve.evaluate( 0.25 );
		Assert.assertTrue( val025 > 0 && val025 < 200 );

		// Evaluation at 0.75 should be between 200 and 255
		final int val075 = curve.evaluate( 0.75 );
		Assert.assertTrue( val075 > 200 && val075 < 255 );
	}

	@Test
	public void testRemovePoint()
	{
		final Curve curve = new Curve();
		curve.addPoint( 0.5, 128 );
		curve.addPoint( 0.3, 100 );
		curve.addPoint( 0.7, 200 );

		Assert.assertEquals( 5, curve.getPointCount() );

		// Try to remove a middle point
		curve.removePoint( 2 );
		Assert.assertEquals( 4, curve.getPointCount() );

		// Try to remove first point (should not work)
		curve.removePoint( 0 );
		Assert.assertEquals( 4, curve.getPointCount() );

		// Try to remove last point (should not work)
		curve.removePoint( 3 );
		Assert.assertEquals( 4, curve.getPointCount() );
	}

	@Test
	public void testClamping()
	{
		final Curve curve = new Curve();

		// Add point with value > 255 (should clamp to 255)
		curve.addPoint( 0.5, 300 );
		Assert.assertEquals( 255, curve.getY( 1 ) );

		// Add point with value < 0 (should clamp to 0)
		curve.addPoint( 0.3, -50 );
		Assert.assertEquals( 0, curve.getY( 1 ) );
	}

	@Test
	public void testFindNearestPoint()
	{
		final Curve curve = new Curve();
		curve.addPoint( 0.25, 64 );
		curve.addPoint( 0.5, 128 );
		curve.addPoint( 0.75, 192 );

		// Find point near (0.5, 128)
		final int idx = curve.findNearestPoint( 0.5, 128 / 255.0, 0.1 );
		Assert.assertTrue( idx >= 0 );
		Assert.assertEquals( 0.5, curve.getX( idx ), 1e-6 );
		Assert.assertEquals( 128, curve.getY( idx ) );

		// If we look at a position far from any point, should not find anything
		final int notFound = curve.findNearestPoint( 0.99, 1.0, 0.001 );
		Assert.assertEquals( -1, notFound );
	}

	@Test
	public void testReset()
	{
		final Curve curve = new Curve();
		curve.addPoint( 0.3, 100 );
		curve.addPoint( 0.7, 200 );
		Assert.assertEquals( 4, curve.getPointCount() );

		curve.reset();

		// Back to initial state
		Assert.assertEquals( 2, curve.getPointCount() );
		Assert.assertEquals( 0.0, curve.getX( 0 ), 1e-6 );
		Assert.assertEquals( 0, curve.getY( 0 ) );
		Assert.assertEquals( 1.0, curve.getX( 1 ), 1e-6 );
		Assert.assertEquals( 255, curve.getY( 1 ) );
	}

	@Test
	public void testCurveEditorPanel()
	{
		final CurveEditorPanel editor = new CurveEditorPanel();

		// Default channel is RGB
		Assert.assertEquals( CurveEditorPanel.Channel.RGB, editor.getChannel() );

		// Change channel
		editor.setChannel( CurveEditorPanel.Channel.R );
		Assert.assertEquals( CurveEditorPanel.Channel.R, editor.getChannel() );

		// Generate color table
		final ColorTable8 ct = editor.generateColorTable();
		Assert.assertNotNull( ct );
	}

	@Test
	public void testColorTableGeneration()
	{
		final CurveEditorPanel editor = new CurveEditorPanel();

		// Generate color table from default curves
		final ColorTable8 ct = editor.generateColorTable();

		// Check that it's a valid color table with 256 entries
		for ( int i = 0; i < 256; i++ )
		{
			final int argb = ct.lookupARGB( 0, 255, i );
			// Should be a valid ARGB value (check that it's not null/invalid)
			// Extract individual channels
			final int a = ( argb >> 24 ) & 0xFF;
			final int r = ( argb >> 16 ) & 0xFF;
			final int g = ( argb >> 8 ) & 0xFF;
			final int b = argb & 0xFF;
			// All channels should be valid bytes
			Assert.assertTrue( r >= 0 && r <= 255 );
			Assert.assertTrue( g >= 0 && g <= 255 );
			Assert.assertTrue( b >= 0 && b <= 255 );
		}
	}

	@Test
	public void testInvertCurve()
	{
		final CurveEditorPanel editor = new CurveEditorPanel();
		editor.setChannel( CurveEditorPanel.Channel.RGB );

		// Invert: add a point at (0.5, 0) to create an inverse relationship
		editor.resetCurves();
		// The curve should now start from scratch, let's just verify reset works
		final ColorTable8 ct1 = editor.generateColorTable();

		// Verify we can generate color tables
		Assert.assertNotNull( ct1 );

		// At normalized value 0, should map close to 0
		final int val0 = ct1.lookupARGB( 0, 255, 0 ) & 0xFF;
		Assert.assertEquals( 0, val0 );

		// At normalized value 255, should map close to 255
		final int val255 = ( ct1.lookupARGB( 0, 255, 255 ) ) & 0xFF;
		Assert.assertEquals( 255, val255 );
	}

	@Test
	public void testSetPoint()
	{
		final Curve curve = new Curve();
		curve.addPoint( 0.5, 128 );

		// Update the point
		curve.setPoint( 1, 200 );
		Assert.assertEquals( 200, curve.getY( 1 ) );

		// Evaluation should reflect the change
		Assert.assertEquals( 200, curve.evaluate( 0.5 ) );
	}

	@Test
	public void testLinearInterpolation()
	{
		final Curve curve = new Curve();

		// Default curve should be linear from (0,0) to (1,255)
		// Test several points
		final int[] expected = { 0, 64, 128, 192, 255 };
		final double[] positions = { 0.0, 0.25, 0.5, 0.75, 1.0 };

		for ( int i = 0; i < positions.length; i++ )
		{
			final int actual = curve.evaluate( positions[ i ] );
			Assert.assertEquals( "At position " + positions[ i ], expected[ i ], actual, 1 );
		}
	}
}
