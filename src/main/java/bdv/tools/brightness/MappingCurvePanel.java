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
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.function.BiConsumer;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import bdv.tools.brightness.colorscheme.ColorScheme;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.PaletteWrapper;

/**
 * Displays the mapping curve (input value -&gt; output value) as an
 * interactive graph, together with two color bars previewing the LUT:
 * a vertical one to the left, along the y (output value) axis, which is
 * always the identity ramp through the palette regardless of the curve, and
 * a horizontal one below, along the x (input value) axis, which shows the
 * color actually produced after passing each input value through the curve.
 * <p>
 * Control points are only shown, and editable, in edit mode (see
 * {@link #setEditMode(boolean)}); left-click adds or drags a control point of
 * the underlying {@link Curve}, right-click removes one.
 */
public class MappingCurvePanel extends JPanel implements MouseListener, MouseMotionListener
{
	private static final int POINT_RADIUS = 5;

	private static final int LABEL_WIDTH = 56;

	private static final int RIGHT_MARGIN = 40;

	private static final int TOP_MARGIN = 10;

	private static final int LABEL_HEIGHT = 26;

	private static final int RANGE_FIELD_WIDTH = 32;

	private static final int RANGE_FIELD_HEIGHT = 16;

	private static final int COLORBAR_WIDTH = 16;

	private static final int COLORBAR_GAP = 10;

	private static final Color CURVE_COLOR = Color.BLACK;

	private static final Color POINT_FILL = Color.WHITE;

	private static final Color POINT_BORDER = new Color( 230, 160, 20 );

	private static final double[] OUTPUT_TICK_FRACTIONS = { 0.0, 0.25, 0.5, 0.75, 1.0 };

	private final LutEditorMapping model;

	private double rangeMin = 0;

	private double rangeMax = 255;

	private Palette palette = Palette.DEFAULT;

	private Integer draggedPoint = null;

	/**
	 * Whether curve control points are shown and editable. Off by default,
	 * since most of the time the curve is left at its preset shape and only
	 * the palette/range settings are adjusted.
	 */
	private boolean editMode = false;

	private final JTextField minField = new JTextField();

	private final JTextField maxField = new JTextField();

	private BiConsumer< Double, Double > rangeChangeListener = null;

	public MappingCurvePanel( final LutEditorMapping model )
	{
		this.model = model;
		setPreferredSize( new Dimension( 280, 200 ) );
		setBackground( Color.WHITE );
		addMouseListener( this );
		addMouseMotionListener( this );

		model.addChangeListener( this::repaint );

		setLayout( null );
		for ( final JTextField field : new JTextField[] { minField, maxField } )
		{
			field.setHorizontalAlignment( SwingConstants.CENTER );
			add( field );
		}
		minField.setText( formatValue( rangeMin ) );
		maxField.setText( formatValue( rangeMax ) );
		minField.addActionListener( e -> commitMinField() );
		maxField.addActionListener( e -> commitMaxField() );
		minField.addFocusListener( new FocusAdapter()
		{
			@Override
			public void focusLost( final FocusEvent e )
			{
				commitMinField();
			}
		} );
		maxField.addFocusListener( new FocusAdapter()
		{
			@Override
			public void focusLost( final FocusEvent e )
			{
				commitMaxField();
			}
		} );
	}

	@Override
	public void doLayout()
	{
		if ( getWidth() <= 0 || getHeight() <= 0 )
			return;
		final int y = transformBarBottom() + 2;
		final int left = plotLeft();

		// The min/max fields straddle the plot's left/right edges, centered on
		// them (clamped so the min field never runs into the max field).
		final int maxFieldX = curveXToPixelX( 1 ) - RANGE_FIELD_WIDTH / 2;
		final int minFieldX = Math.min( left - RANGE_FIELD_WIDTH / 2, maxFieldX - RANGE_FIELD_WIDTH );

		minField.setBounds( minFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
		maxField.setBounds( maxFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
	}

	/**
	 * Set the actual data range represented by the horizontal axis.
	 */
	public void setRange( final double min, final double max )
	{
		this.rangeMin = min;
		this.rangeMax = max;
		minField.setText( formatValue( min ) );
		maxField.setText( formatValue( max ) );
		// The range scales raw values to pixels, so it also changes how wide
		// the background swatch is -- re-place the min field, not just repaint.
		revalidate();
		repaint();
	}

	/**
	 * Set the listener to be notified when the user edits the range's min or
	 * max value via the input boxes at the ends of the x axis.
	 */
	public void setRangeChangeListener( final BiConsumer< Double, Double > listener )
	{
		this.rangeChangeListener = listener;
	}

	private void commitMinField()
	{
		try
		{
			final double v = Double.parseDouble( minField.getText().trim() );
			if ( v < rangeMax )
			{
				rangeMin = v;
				if ( rangeChangeListener != null )
					rangeChangeListener.accept( rangeMin, rangeMax );
			}
		}
		catch ( final NumberFormatException ignored )
		{
		}
		minField.setText( formatValue( rangeMin ) );
		repaint();
	}

	private void commitMaxField()
	{
		try
		{
			final double v = Double.parseDouble( maxField.getText().trim() );
			if ( v > rangeMin )
			{
				rangeMax = v;
				if ( rangeChangeListener != null )
					rangeChangeListener.accept( rangeMin, rangeMax );
			}
		}
		catch ( final NumberFormatException ignored )
		{
		}
		maxField.setText( formatValue( rangeMax ) );
		repaint();
	}

	/**
	 * Set the color palette used to render the color bar.
	 */
	public void setPalette( final Palette palette )
	{
		this.palette = palette == null ? Palette.DEFAULT : palette;
		repaint();
	}

	/**
	 * Set whether curve control points are shown and can be added, dragged, or
	 * removed. When {@code false}, the curve is still drawn and the range
	 * fields still work, but the control points themselves are hidden and
	 * inert.
	 */
	public void setEditMode( final boolean editMode )
	{
		this.editMode = editMode;
		repaint();
	}

	private int plotLeft()
	{
		return LABEL_WIDTH + COLORBAR_WIDTH + COLORBAR_GAP;
	}

	private int plotRight()
	{
		return getWidth() - RIGHT_MARGIN;
	}

	private int plotTop()
	{
		return TOP_MARGIN;
	}

	private int plotBottom()
	{
		return getHeight() - LABEL_HEIGHT - COLORBAR_GAP - COLORBAR_WIDTH;
	}

	private int transformBarTop()
	{
		return plotBottom() + COLORBAR_GAP;
	}

	private int transformBarBottom()
	{
		return transformBarTop() + COLORBAR_WIDTH;
	}

	private int plotWidth()
	{
		return Math.max( 1, plotRight() - plotLeft() );
	}

	private int plotHeight()
	{
		return Math.max( 1, plotBottom() - plotTop() );
	}

	// -- Coordinate conversions, named <from>To<to> --------------------------
	// Three domains meet in this panel: pixels, raw input values (the x axis,
	// spanning [rangeMin, rangeMax]), and the curve's own normalized [0, 1] x
	// / [0, 255] output. Every conversion below names both ends explicitly so
	// which one is in play is never in doubt at the call site.

	/**
	 * Raw input value at a pixel x-coordinate, i.e. [plotLeft, plotRight]
	 * mapped onto [rangeMin, rangeMax].
	 */
	private double pixelXToValue( final int pixelX )
	{
		final double normX = Math.max( 0.0, Math.min( 1.0, ( pixelX - plotLeft() ) / ( double ) plotWidth() ) );
		return rangeMin + normX * ( rangeMax - rangeMin );
	}

	/** Normalized curve position (in [0, 1]) for a raw input value: the fraction of the way across [rangeMin, rangeMax]. */
	private double valueToCurveX( final double value )
	{
		final double span = rangeMax - rangeMin;
		final double frac = span > 0 ? ( value - rangeMin ) / span : 0.0;
		return Math.max( 0.0, Math.min( 1.0, frac ) );
	}

	/** Pixel x-coordinate for a normalized curve position in [0, 1]. */
	private int curveXToPixelX( final double normX )
	{
		return plotLeft() + ( int ) Math.round( normX * plotWidth() );
	}

	/** Curve output value in [0, 255] at a pixel y-coordinate. */
	private int pixelYToOutput( final int pixelY )
	{
		final double v = ( plotBottom() - pixelY ) / ( double ) plotHeight() * 255.0;
		return Math.max( 0, Math.min( 255, ( int ) Math.round( v ) ) );
	}

	/** Pixel y-coordinate for a curve output value in [0, 255]. */
	private int outputToPixelY( final int outputValue )
	{
		return plotBottom() - ( int ) Math.round( outputValue / 255.0 * plotHeight() );
	}

	@Override
	protected void paintComponent( final Graphics g )
	{
		super.paintComponent( g );
		final Graphics2D g2 = ( Graphics2D ) g;
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

		// The new color-mapping representation renders the preview: the wrapper
		// turns each raw value straight into its final color, and its color
		// scheme is the palette bar. Rebuilt per paint from the current curve /
		// palette / range -- cheap for a preview, and always in sync.
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( palette, model, rangeMin, rangeMax );

		drawGrid( g2 );
		drawCurve( g2, wrapper );
		// A discrete palette's shape comes from its step size, not the curve
		// (see LutEditorMapping), so there are no control points to show.
		if ( editMode && !model.isDiscrete() )
			drawControlPoints( g2 );
		drawOutputColorBar( g2, wrapper.getColorScheme() );
		drawTransformColorBar( g2, wrapper );
	}

	private void drawGrid( final Graphics2D g )
	{
		final int left = plotLeft();
		final int right = plotRight();
		final int top = plotTop();
		final int bottom = plotBottom();

		g.setColor( new Color( 225, 225, 225 ) );
		g.setStroke( new BasicStroke( 1 ) );
		for ( int i = 1; i < 4; i++ )
		{
			final int x = left + i * ( right - left ) / 4;
			g.drawLine( x, top, x, bottom );
			final int y = top + i * ( bottom - top ) / 4;
			g.drawLine( left, y, right, y );
		}

		g.setColor( new Color( 120, 120, 120 ) );
		g.drawRect( left, top, right - left, bottom - top );
	}

	/**
	 * Draws the transfer function actually being rendered: each pixel column's
	 * input value put through {@code wrapper}, so the line always agrees with
	 * the color bar below it. Deliberately read off the wrapper rather than
	 * {@link #model}'s own {@link Curve}, because the curve is only half the
	 * story -- a discrete (categorical) palette's shape comes from its step
	 * size instead (see {@link LutEditorMapping}), and drawing the curve there
	 * would show a mapping that is not the one in effect.
	 * <p>
	 * For a discrete palette the value is additionally floored to its stop
	 * before being drawn, matching
	 * {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme}: a smooth
	 * line would show transitions that never happen in the rendered color.
	 */
	private void drawCurve( final Graphics2D g, final PaletteWrapper wrapper )
	{
		g.setColor( CURVE_COLOR );
		g.setStroke( new BasicStroke( 2 ) );

		final int paletteRangeLength = wrapper.getColorScheme().getPaletteRangeLength();
		final boolean discrete = model.isDiscrete();
		final int left = plotLeft();
		final int right = plotRight();

		Integer prevX = null;
		Integer prevY = null;
		for ( int px = left; px <= right; px++ )
		{
			final float paletteValue = wrapper.getPaletteValueForRaw( ( float ) pixelXToValue( px ) );
			final int py = outputToPixelY( paletteValueToOutput( paletteValue, paletteRangeLength, discrete ) );
			if ( prevX != null )
				g.drawLine( prevX, prevY, px, py );
			prevX = px;
			prevY = py;
		}
	}

	/**
	 * A palette value (in {@code [0, paletteRangeLength]}) expressed in the
	 * graph's own {@code [0, 255]} output units. When {@code discrete}, it is
	 * first floored to a stop index exactly the way
	 * {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme#colorAt}
	 * does (same {@code floor}, same clamp to the last stop at the top edge),
	 * which is what turns the drawn line into a staircase.
	 */
	private static int paletteValueToOutput( final float paletteValue, final int paletteRangeLength, final boolean discrete )
	{
		final double stops = discrete
				? Math.max( 0, Math.min( paletteRangeLength - 1, ( int ) Math.floor( paletteValue ) ) )
				: Math.max( 0.0, Math.min( paletteRangeLength, paletteValue ) );
		return ( int ) Math.round( stops / paletteRangeLength * 255.0 );
	}

	/** Draws each curve control point at its normalized (x, output) position. */
	private void drawControlPoints( final Graphics2D g )
	{
		final Curve curve = model.getCurve();
		for ( int i = 0; i < curve.getPointCount(); i++ )
			drawControlPointAt( g, curveXToPixelX( curve.getX( i ) ), outputToPixelY( curve.getY( i ) ) );
	}

	private void drawControlPointAt( final Graphics2D g, final int px, final int py )
	{
		g.setColor( POINT_FILL );
		g.fillOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );
		g.setColor( POINT_BORDER );
		g.setStroke( new BasicStroke( 2 ) );
		g.drawOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );
	}

	/**
	 * Vertical color bar along the y (output value) axis, to the left of the
	 * curve editor. It is the palette itself (output value == position through
	 * the palette) and does not depend on the mapping curve, so it always looks
	 * the same for a given palette. Tick labels show the actual palette color
	 * index (0 to {@code palette.getLength() - 1}), the scale that matters to
	 * the user, rather than the internal 0-255 output value.
	 * <p>
	 * Rendered through the {@code scheme}, so a discrete (categorical) palette
	 * shows each color as a flat band and a continuous one as a gradient,
	 * automatically -- see {@link ColorScheme}.
	 */
	private void drawOutputColorBar( final Graphics2D g, final ColorScheme scheme )
	{
		final int barLeft = LABEL_WIDTH;
		final int top = plotTop();
		final int bottom = plotBottom();

		final int paletteRangeLength = scheme.getPaletteRangeLength();

		for ( int py = top; py < bottom; py++ )
		{
			final double t = ( bottom - py ) / ( double ) plotHeight();
			g.setColor( new Color( scheme.getRGB( ( float ) ( t * paletteRangeLength ) ) ) );
			// fillRect, not drawLine: the stroke is whatever the last caller
			// left set (drawCurve uses 2px) and antialiasing is on, which
			// together smear each 1px row across its neighbours instead of
			// laying down the exact color.
			g.fillRect( barLeft, py, COLORBAR_WIDTH + 1, 1 );
		}

		g.setColor( new Color( 120, 120, 120 ) );
		// Explicit 1px stroke: whatever was set last (drawCurve leaves 2px)
		// would otherwise be antialiased over the bar's own edge columns.
		g.setStroke( new BasicStroke( 1 ) );
		g.drawRect( barLeft, top, COLORBAR_WIDTH, bottom - top );

		g.setColor( Color.DARK_GRAY );
		final FontMetrics fm = g.getFontMetrics();
		final int lastColor = palette.getLength() - 1;
		for ( final double frac : OUTPUT_TICK_FRACTIONS )
		{
			final String text = Integer.toString( ( int ) Math.round( frac * lastColor ) );
			final int py = outputToPixelY( ( int ) Math.round( frac * 255.0 ) );
			g.drawString( text, barLeft - fm.stringWidth( text ) - 6, py + fm.getAscent() / 2 - 1 );
		}
	}

	/**
	 * Horizontal color bar along the x (input value) axis, showing the color
	 * actually produced after passing each input value through the whole
	 * mapping ("after transform") -- i.e. exactly what the renderer would show
	 * for that raw value, straight from {@link PaletteWrapper#getRGBForRaw(float)}
	 * (curve, then color scheme, then boundary handling).
	 */
	private void drawTransformColorBar( final Graphics2D g, final PaletteWrapper wrapper )
	{
		final int left = plotLeft();
		final int right = plotRight();
		final int top = transformBarTop();
		final int bottom = transformBarBottom();

		for ( int px = left; px < right; px++ )
		{
			final double value = pixelXToValue( px );
			g.setColor( new Color( wrapper.getRGBForRaw( ( float ) value ) ) );
			// fillRect rather than drawLine -- see drawOutputColorBar.
			g.fillRect( px, top, 1, bottom - top + 1 );
		}

		g.setColor( new Color( 120, 120, 120 ) );
		// See drawOutputColorBar's border for why the stroke is set explicitly.
		g.setStroke( new BasicStroke( 1 ) );
		g.drawRect( left, top, right - left, bottom - top );

		// The min (t=0) and max (t=1) ticks are editable input boxes instead of
		// plain labels (see #doLayout()); only draw the intermediate ticks here.
		g.setColor( Color.DARK_GRAY );
		final FontMetrics fm = g.getFontMetrics();
		for ( int i = 1; i <= 3; i++ )
		{
			final double t = i / 4.0;
			final double value = rangeMin + t * ( rangeMax - rangeMin );
			final String text = formatValue( value );
			final int px = curveXToPixelX( t );
			g.drawString( text, px - fm.stringWidth( text ) / 2, bottom + fm.getAscent() + 4 );
		}
	}

	private static String formatValue( final double value )
	{
		if ( Math.abs( value - Math.round( value ) ) < 1e-6 )
			return Long.toString( Math.round( value ) );
		return String.format( "%.2f", value );
	}

	@Override
	public void mousePressed( final MouseEvent e )
	{
		// Inert for a discrete palette: its shape is the step size, not the
		// curve, so a dragged point would change nothing that renders.
		if ( !editMode || model.isDiscrete() )
			return;

		final Curve curve = model.getCurve();
		final double x = valueToCurveX( pixelXToValue( e.getX() ) );
		final int y = pixelYToOutput( e.getY() );

		if ( e.getButton() == MouseEvent.BUTTON1 )
		{
			draggedPoint = curve.findNearestPoint( x, y / 255.0, 0.05 );
			if ( draggedPoint < 0 )
			{
				curve.addPoint( x, y );
				draggedPoint = curve.findNearestPoint( x, y / 255.0, 0.01 );
			}
			model.notifyCurveEdited();
			repaint();
		}
		else if ( e.getButton() == MouseEvent.BUTTON3 )
		{
			final int idx = curve.findNearestPoint( x, y / 255.0, 0.05 );
			if ( idx >= 0 )
			{
				curve.removePoint( idx );
				model.notifyCurveEdited();
				repaint();
			}
		}
	}

	@Override
	public void mouseDragged( final MouseEvent e )
	{
		if ( editMode && !model.isDiscrete() && draggedPoint != null && draggedPoint >= 0 )
		{
			model.getCurve().setPoint( draggedPoint, pixelYToOutput( e.getY() ) );
			model.notifyCurveEdited();
			repaint();
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

	private static final long serialVersionUID = 1L;
}
