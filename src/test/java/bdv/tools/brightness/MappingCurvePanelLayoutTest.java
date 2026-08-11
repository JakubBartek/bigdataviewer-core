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

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JTextField;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for how {@link MappingCurvePanel} places its min/max range
 * fields, in particular around the "treat min as background" swatch.
 * <p>
 * These render the panel to an offscreen image and read the swatch's real
 * extent back out of the pixels, rather than recomputing it: the whole point
 * of the layout code under test is that the field lines up with what is
 * actually <em>drawn</em>, so re-deriving the expected position from the same
 * arithmetic would not catch the drift it exists to prevent.
 */
public class MappingCurvePanelLayoutTest
{
	private static final int PANEL_WIDTH = 400;

	private static final int PANEL_HEIGHT = 200;

	/** Distinct enough not to collide with anything else the panel paints (the curve is black, the default background color also black). */
	private static final int BACKGROUND_ARGB = 0xff112233;

	private static MappingCurvePanel laidOutPanel( final MappingModel model, final double min, final double max )
	{
		final MappingCurvePanel panel = new MappingCurvePanel( model );
		panel.setPalette( LutPalettes.load( "tab10" ) );
		panel.setRange( min, max );
		panel.setSize( PANEL_WIDTH, PANEL_HEIGHT );
		panel.doLayout();
		return panel;
	}

	/** The min field is the first text field added by the constructor. */
	private static JTextField minField( final MappingCurvePanel panel )
	{
		for ( final Component c : panel.getComponents() )
			if ( c instanceof JTextField )
				return ( JTextField ) c;
		throw new AssertionError( "no range fields found" );
	}

	/**
	 * One past the right-most pixel column actually painted in
	 * {@link #BACKGROUND_ARGB}, or {@code -1} if the swatch was not drawn at
	 * all.
	 */
	private static int paintedBackgroundRightEdge( final MappingCurvePanel panel )
	{
		final BufferedImage image = new BufferedImage( PANEL_WIDTH, PANEL_HEIGHT, BufferedImage.TYPE_INT_ARGB );
		final Graphics2D g = image.createGraphics();
		try
		{
			panel.paint( g );
		}
		finally
		{
			g.dispose();
		}

		int rightEdge = -1;
		for ( int x = 0; x < PANEL_WIDTH; x++ )
			for ( int y = 0; y < PANEL_HEIGHT; y++ )
				if ( image.getRGB( x, y ) == BACKGROUND_ARGB )
				{
					rightEdge = x + 1;
					break;
				}
		return rightEdge;
	}

	/**
	 * With the option off nothing is drawn at the plot's left edge, so the
	 * field keeps its original centered-on-the-edge position -- and turning
	 * the option on is what shifts it right, clear of the swatch.
	 */
	@Test
	public void testMinFieldOnlyShiftsRightOnceBackgroundIsShown()
	{
		final MappingModel off = new MappingModel();
		off.setRangeMode( RangeMode.CYCLIC );
		off.setBackgroundColor( BACKGROUND_ARGB );
		final MappingCurvePanel panelOff = laidOutPanel( off, 0, 64 );

		final MappingModel on = new MappingModel();
		on.setRangeMode( RangeMode.CYCLIC );
		on.setTreatMinAsBackground( true );
		on.setBackgroundColor( BACKGROUND_ARGB );
		final MappingCurvePanel panelOn = laidOutPanel( on, 0, 64 );

		Assert.assertEquals( "no swatch when the option is off", -1, paintedBackgroundRightEdge( panelOff ) );
		Assert.assertTrue( "swatch when the option is on", paintedBackgroundRightEdge( panelOn ) > 0 );

		Assert.assertTrue( "enabling the background should push the min field right, not left",
				minField( panelOn ).getX() > minField( panelOff ).getX() );
	}

	/** Horizontal center of the min field -- the point its value actually labels. */
	private static int minFieldCenter( final MappingCurvePanel panel )
	{
		final JTextField min = minField( panel );
		return min.getX() + min.getWidth() / 2;
	}

	/**
	 * The requirement: with the option on, the min field labels the swatch's
	 * far side rather than sitting on top of the swatch -- still straddling
	 * that boundary, exactly as it straddles the plot edge when the option is
	 * off.
	 */
	@Test
	public void testMinFieldIsCenteredOnRightEdgeOfBackgroundSwatchInCyclicMode()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );
		model.setBackgroundColor( BACKGROUND_ARGB );

		// A 64-wide range over a plot a few hundred px wide makes the reserved
		// [min, min + 1) interval several pixels wide -- comfortably more than
		// the FIT minimum, so this pins down the cyclic cutoff specifically.
		final MappingCurvePanel panel = laidOutPanel( model, 0, 64 );

		final int swatchRight = paintedBackgroundRightEdge( panel );
		Assert.assertTrue( "swatch should be painted", swatchRight > 0 );
		Assert.assertEquals( swatchRight, minFieldCenter( panel ) );
	}

	@Test
	public void testMinFieldIsCenteredOnRightEdgeOfBackgroundSwatchInFitMode()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.FIT );
		model.setTreatMinAsBackground( true );
		model.setBackgroundColor( BACKGROUND_ARGB );

		final MappingCurvePanel panel = laidOutPanel( model, 0, 255 );

		final int swatchRight = paintedBackgroundRightEdge( panel );
		Assert.assertTrue( "swatch should be painted", swatchRight > 0 );
		Assert.assertEquals( swatchRight, minFieldCenter( panel ) );
	}

	/**
	 * The field should shift by exactly the swatch's width -- no more. It
	 * previously moved by the swatch width <em>plus</em> half the field, which
	 * pushed it noticeably right of the boundary it labels.
	 */
	@Test
	public void testMinFieldShiftsByExactlyTheSwatchWidth()
	{
		final MappingModel off = new MappingModel();
		off.setRangeMode( RangeMode.CYCLIC );
		off.setBackgroundColor( BACKGROUND_ARGB );
		final MappingCurvePanel panelOff = laidOutPanel( off, 0, 64 );

		final MappingModel on = new MappingModel();
		on.setRangeMode( RangeMode.CYCLIC );
		on.setTreatMinAsBackground( true );
		on.setBackgroundColor( BACKGROUND_ARGB );
		final MappingCurvePanel panelOn = laidOutPanel( on, 0, 64 );

		// Width of the drawn swatch, read straight off the rendered bar: it
		// starts at the plot's left edge, which is where the field was
		// centered before the option was turned on.
		final int swatchWidth = paintedBackgroundRightEdge( panelOn ) - minFieldCenter( panelOff );
		Assert.assertTrue( "swatch should have a real width", swatchWidth > 0 );

		Assert.assertEquals( swatchWidth, minField( panelOn ).getX() - minField( panelOff ).getX() );
	}

	/**
	 * Even when the reserved interval covers most of the plot, the min field
	 * must not be pushed onto (or past) the max field.
	 */
	@Test
	public void testMinFieldNeverOverlapsMaxFieldForAVeryWideSwatch()
	{
		final MappingModel model = new MappingModel();
		model.setRangeMode( RangeMode.CYCLIC );
		model.setTreatMinAsBackground( true );
		model.setBackgroundColor( BACKGROUND_ARGB );

		// Range of 2 raw units: the reserved [min, min + 1) interval is half
		// the entire plot.
		final MappingCurvePanel panel = laidOutPanel( model, 0, 2 );

		final JTextField min = minField( panel );
		JTextField max = null;
		for ( final Component c : panel.getComponents() )
			if ( c instanceof JTextField && c != min )
				max = ( JTextField ) c;
		Assert.assertNotNull( max );

		Assert.assertTrue( "min field (x=" + min.getX() + ", w=" + min.getWidth()
				+ ") must stay clear of max field (x=" + max.getX() + ")",
				min.getX() + min.getWidth() <= max.getX() );
	}
}
