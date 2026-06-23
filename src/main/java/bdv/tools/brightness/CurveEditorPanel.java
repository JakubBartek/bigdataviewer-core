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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import net.imglib2.display.ColorTable8;

/**
 * An interactive panel for editing color lookup table curves.
 * Supports editing separate R, G, B channels with mouse interaction:
 * - Left-click to add control points
 * - Drag to move control points
 * - Right-click to remove control points
 * <p>
 * Provides a visual representation of the curves and generates ColorTable8 objects.
 */
public class CurveEditorPanel extends JPanel implements MouseListener, MouseMotionListener
{
	private static final int CANVAS_WIDTH = 300;
	private static final int CANVAS_HEIGHT = 200;
	private static final int POINT_RADIUS = 5;

	private final Curve curveR = new Curve();
	private final Curve curveG = new Curve();
	private final Curve curveB = new Curve();

	private Channel currentChannel = Channel.RGB;
	private Integer draggedPoint = null;

	private final List< Runnable > changeListeners = new ArrayList<>();

	public enum Channel
	{
		R, G, B, RGB
	}

	public CurveEditorPanel()
	{
		setPreferredSize( new Dimension( CANVAS_WIDTH, CANVAS_HEIGHT ) );
		setBackground( new Color( 30, 30, 30 ) );
		addMouseListener( this );
		addMouseMotionListener( this );
	}

	/**
	 * Set the current channel being edited.
	 */
	public void setChannel( final Channel channel )
	{
		currentChannel = channel;
		repaint();
	}

	/**
	 * Get the current channel being edited.
	 */
	public Channel getChannel()
	{
		return currentChannel;
	}

	/**
	 * Generate a ColorTable8 from the current curves.
	 */
	public ColorTable8 generateColorTable()
	{
		final byte[] r = new byte[ 256 ];
		final byte[] g = new byte[ 256 ];
		final byte[] b = new byte[ 256 ];

		for ( int i = 0; i < 256; i++ )
		{
			final double normalized = i / 255.0;
			r[ i ] = ( byte ) curveR.evaluate( normalized );
			g[ i ] = ( byte ) curveG.evaluate( normalized );
			b[ i ] = ( byte ) curveB.evaluate( normalized );
		}

		return new ColorTable8( r, g, b );
	}

	/**
	 * Reset all curves to linear gradients.
	 */
	public void resetCurves()
	{
		curveR.reset();
		curveG.reset();
		curveB.reset();
		draggedPoint = null;
		repaint();
		fireChangeListeners();
	}

	/**
	 * Add a listener to be notified when curves change.
	 */
	public void addChangeListener( final Runnable listener )
	{
		changeListeners.add( listener );
	}

	/**
	 * Remove a change listener.
	 */
	public void removeChangeListener( final Runnable listener )
	{
		changeListeners.remove( listener );
	}

	private void fireChangeListeners()
	{
		for ( final Runnable listener : changeListeners )
			listener.run();
	}

	@Override
	protected void paintComponent( final Graphics g )
	{
		super.paintComponent( g );

		final Graphics2D g2d = ( Graphics2D ) g;
		g2d.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

		final int w = getWidth();
		final int h = getHeight();

		drawGrid( g2d, w, h );
		drawCurves( g2d, w, h );
		drawControlPoints( g2d, w, h );
	}

	private void drawGrid( final Graphics2D g, final int w, final int h )
	{
		g.setColor( new Color( 60, 60, 60 ) );
		g.setStroke( new BasicStroke( 1 ) );

		// Vertical grid lines
		for ( int i = 1; i < 4; i++ )
		{
			final int x = i * w / 4;
			g.drawLine( x, 0, x, h );
		}

		// Horizontal grid lines
		for ( int i = 1; i < 4; i++ )
		{
			final int y = i * h / 4;
			g.drawLine( 0, y, w, y );
		}

		// Border
		g.setColor( new Color( 100, 100, 100 ) );
		g.drawRect( 0, 0, w - 1, h - 1 );
	}

	private void drawCurves( final Graphics2D g, final int w, final int h )
	{
		final Curve[] curves = { curveR, curveG, curveB };
		final Color[] colors = { Color.RED, Color.GREEN, Color.BLUE };

		for ( int ch = 0; ch < 3; ch++ )
		{
			final Curve curve = curves[ ch ];
			final Color color = colors[ ch ];

			// Only draw non-active channels at reduced opacity
			if ( currentChannel != Channel.RGB && currentChannel.ordinal() != ch )
			{
				g.setColor( new Color( color.getRed(), color.getGreen(), color.getBlue(), 80 ) );
			} else
			{
				g.setColor( new Color( color.getRed(), color.getGreen(), color.getBlue(), 200 ) );
			}

			g.setStroke( new BasicStroke( 2 ) );

			// Draw the curve as a polyline
			int prevX = 0;
			int prevY = h;
			for ( int i = 0; i <= 256; i++ )
			{
				final double x = i / 255.0;
				final int y = curve.evaluate( x );
				final int px = ( int ) ( i * w / 256.0 );
				final int py = h - ( int ) ( y * h / 255.0 );

				if ( i > 0 )
				{
					g.drawLine( prevX, prevY, px, py );
				}

				prevX = px;
				prevY = py;
			}
		}
	}

	private void drawPointAndBorder( final Graphics2D g, final int w, final int h, final Curve curve, int i, final Color pointColor, final Color borderColor )
	{
		final double x = curve.getX( i );
		final int y = curve.getY( i );
		final int px = ( int ) ( x * ( w - 1 ) );
		final int py = h - ( int ) ( y * h / 255.0 );

		// Draw point
		g.setColor( pointColor );
		g.fillOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );

		// Draw border
		g.setColor( borderColor );
		g.setStroke( new BasicStroke( 1 ) );
		g.drawOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );
	}

	private void drawControlPoints( final Graphics2D g, final int w, final int h )
	{
		final Curve[] curves = { curveR, curveG, curveB };
		final Color[] colors = { Color.RED, Color.GREEN, Color.BLUE };

		if ( currentChannel == Channel.RGB )
		{
			// In RGB mode, all curves are the same, just draw once
			final Curve curve = curveR;
			for ( int i = 0; i < curve.getPointCount(); i++ )
			{
				drawPointAndBorder( g, w, h, curve, i, Color.WHITE, Color.BLACK );
			}
		} else
		{
			for ( int ch = 0; ch < 3; ch++ )
			{
				final Curve curve = curves[ ch ];
				final Color color = colors[ ch ];

				// Only draw active channel control points
				if ( currentChannel.ordinal() != ch )
					continue;

				for ( int i = 0; i < curve.getPointCount(); i++ )
				{
					drawPointAndBorder( g, w, h, curve, i, color, Color.WHITE );
				}
			}
		}
	}

	@Override
	public void mousePressed( final MouseEvent e )
	{
		if ( e.getButton() == MouseEvent.BUTTON1 )
		{
			// Left-click: find nearest point or add new one
			final double x = e.getX() / ( double ) ( getWidth() - 1 );
			final int y = 255 - ( int ) ( e.getY() * 255.0 / getHeight() );

			if ( currentChannel == Channel.RGB )
			{
				// For RGB mode, add to all channels
				draggedPoint = curveR.findNearestPoint( x, y / 255.0, 0.05 );
				if ( draggedPoint < 0 )
				{
					curveR.addPoint( x, y );
					curveG.addPoint( x, y );
					curveB.addPoint( x, y );
					draggedPoint = curveR.findNearestPoint( x, y / 255.0, 0.01 );
				}
			} else
			{
				draggedPoint = getCurrentCurve().findNearestPoint( x, y / 255.0, 0.05 );
				if ( draggedPoint < 0 )
				{
					getCurrentCurve().addPoint( x, y );
					draggedPoint = getCurrentCurve().findNearestPoint( x, y / 255.0, 0.01 );
				}
			}

			repaint();
			fireChangeListeners();
		} else if ( e.getButton() == MouseEvent.BUTTON3 )
		{
			// Right-click: remove nearest point
			final double x = e.getX() / ( double ) ( getWidth() - 1 );
			final int y = 255 - ( int ) ( e.getY() * 255.0 / getHeight() );

			if ( currentChannel == Channel.RGB )
			{
				final int idx = curveR.findNearestPoint( x, y / 255.0, 0.05 );
				if ( idx >= 0 )
				{
					curveR.removePoint( idx );
					curveG.removePoint( idx );
					curveB.removePoint( idx );
				}
			} else
			{
				final int idx = getCurrentCurve().findNearestPoint( x, y / 255.0, 0.05 );
				if ( idx >= 0 )
				{
					getCurrentCurve().removePoint( idx );
				}
			}

			repaint();
			fireChangeListeners();
		}
	}

	@Override
	public void mouseDragged( final MouseEvent e )
	{
		if ( draggedPoint != null && draggedPoint >= 0 )
		{
			final int y = 255 - ( int ) ( e.getY() * 255.0 / getHeight() );

			if ( currentChannel == Channel.RGB )
			{
				curveR.setPoint( draggedPoint, y );
				curveG.setPoint( draggedPoint, y );
				curveB.setPoint( draggedPoint, y );
			} else
			{
				getCurrentCurve().setPoint( draggedPoint, y );
			}

			repaint();
			fireChangeListeners();
		}
	}

	@Override
	public void mouseReleased( final MouseEvent e )
	{
		draggedPoint = null;
	}

	@Override
	public void mouseClicked( final MouseEvent e )
	{
	}

	@Override
	public void mouseEntered( final MouseEvent e )
	{
	}

	@Override
	public void mouseExited( final MouseEvent e )
	{
	}

	@Override
	public void mouseMoved( final MouseEvent e )
	{
	}

	private Curve getCurrentCurve()
	{
		switch ( currentChannel )
		{
			case R:
				return curveR;
			case G:
				return curveG;
			case B:
				return curveB;
			case RGB:
			default:
				// Edit all channels together
				return curveR;
		}
	}

	private static final long serialVersionUID = 1L;
}
