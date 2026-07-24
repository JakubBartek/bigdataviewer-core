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

import net.imglib2.display.ColorTable;
import net.imglib2.display.ColorTable8;

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

	private final MappingModel model;

	private double rangeMin = 0;

	private double rangeMax = 255;

	private ColorTable palette = new ColorTable8();

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

	public MappingCurvePanel( final MappingModel model )
	{
		this.model = model;
		setPreferredSize( new Dimension( 280, 183 ) );
		setBackground( Color.WHITE );
		addMouseListener( this );
		addMouseMotionListener( this );

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
		minField.setBounds( pixelX( 0 ) - RANGE_FIELD_WIDTH / 2, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
		maxField.setBounds( pixelX( 1 ) - RANGE_FIELD_WIDTH / 2, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
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
	public void setPalette( final ColorTable palette )
	{
		this.palette = palette == null ? new ColorTable8() : palette;
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

	private double toNormX( final int pixelX )
	{
		return Math.max( 0.0, Math.min( 1.0, ( pixelX - plotLeft() ) / ( double ) plotWidth() ) );
	}

	/**
	 * Raw input value represented by a pixel x-coordinate, i.e. the inverse of
	 * mapping [plotLeft, plotRight] onto [rangeMin, rangeMax].
	 */
	private double pixelXToValue( final int pixelX )
	{
		return rangeMin + toNormX( pixelX ) * ( rangeMax - rangeMin );
	}

	/**
	 * Pixel x-coordinate for a raw input value, i.e. the inverse of
	 * {@link #pixelXToValue}.
	 */
	private int pixelXFromValue( final double value )
	{
		final double span = rangeMax - rangeMin;
		final double frac = span > 0 ? ( value - rangeMin ) / span : 0.0;
		return plotLeft() + ( int ) Math.round( frac * plotWidth() );
	}

	/**
	 * Normalized curve position (in [0, 1]) for a raw input value, using
	 * exactly the same formula as {@link MappingModel#mapToLutIndex}, so that
	 * the curve is drawn/edited consistently with what is actually applied.
	 */
	private double valueToCurveX( final double value )
	{
		return model.getRangeMode() == RangeMode.CYCLIC
				? MappingModel.cyclicPosition( value, rangeMin, palette.getLength(), model.isTreatMinAsBackground() )
				: MappingModel.fitPosition( value, rangeMin, rangeMax );
	}

	private int toValueY( final int pixelY )
	{
		final double v = ( plotBottom() - pixelY ) / ( double ) plotHeight() * 255.0;
		return Math.max( 0, Math.min( 255, ( int ) Math.round( v ) ) );
	}

	private int pixelX( final double normX )
	{
		return plotLeft() + ( int ) Math.round( normX * plotWidth() );
	}

	private int pixelY( final int outputValue )
	{
		return plotBottom() - ( int ) Math.round( outputValue / 255.0 * plotHeight() );
	}

	@Override
	protected void paintComponent( final Graphics g )
	{
		super.paintComponent( g );
		final Graphics2D g2 = ( Graphics2D ) g;
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

		drawGrid( g2 );
		drawCurve( g2 );
		if ( editMode )
			drawControlPoints( g2 );
		drawOutputColorBar( g2 );
		drawTransformColorBar( g2 );
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
	 * Draws the curve exactly as it is actually applied to input values (via
	 * {@link MappingModel#mapToLutIndex}), rather than just plotting the raw
	 * {@link Curve} across the full plot width. In {@link RangeMode#CYCLIC}
	 * this means the curve repeats every {@code palette.getLength()} input
	 * units; the line is not drawn across a wrap boundary, so each repetition
	 * appears as a separate segment (like a sawtooth), matching the actual
	 * discontinuity in the mapping.
	 * <p>
	 * {@code mapToLutIndex} itself always evaluates the curve smoothly (see
	 * its javadoc for why). For Round/Truncate, this draws the corresponding
	 * step visualization instead, by snapping that smooth value to the
	 * palette's own control-point resolution (not the curve's), via
	 * {@link LutPalette#steppedPosition}, matching exactly what the color
	 * bars below actually show.
	 */
	private void drawCurve( final Graphics2D g )
	{
		g.setColor( CURVE_COLOR );
		g.setStroke( new BasicStroke( 2 ) );

		final int n = palette.getLength();
		final boolean cyclic = model.getRangeMode() == RangeMode.CYCLIC && n > 1;
		final ValueMatching matching = model.getValueMatching();
		final int left = plotLeft();
		final int right = plotRight();

		Integer prevX = null;
		Integer prevY = null;
		double prevValue = 0;
		for ( int px = left; px <= right; px++ )
		{
			final double value = pixelXToValue( px );

			// The dedicated background color (see MappingModel#isBackgroundValue)
			// is not part of the curve's 0-255 output scale at all, so skip
			// drawing the line here entirely rather than showing a misleading
			// position for it.
			if ( model.isBackgroundValue( value, rangeMin ) )
			{
				prevX = null;
				continue;
			}

			final int smoothY = model.mapToLutIndex( value, rangeMin, rangeMax, n );
			final double steppedT = LutPalette.steppedPosition( palette, smoothY / 255.0, matching );
			final int py = pixelY( ( int ) Math.round( steppedT * 255.0 ) );

			final boolean wrapped = cyclic && prevX != null && Math.floor( value / n ) != Math.floor( prevValue / n );
			if ( prevX != null && !wrapped )
				g.drawLine( prevX, prevY, px, py );

			prevX = px;
			prevY = py;
			prevValue = value;
		}
	}

	/**
	 * Draws each curve control point. In {@link RangeMode#CYCLIC}, the cycle
	 * is anchored at rangeMin (see {@link MappingModel#cyclicPosition}), so a
	 * control point at normalized x corresponds to a raw value of
	 * {@code k*n + rangeMin + backgroundShift + x*(n-1)} for every repetition
	 * {@code k} (where {@code backgroundShift} is 1 if min is reserved for
	 * the background color, so the cycle starts at min+1 instead of min); it
	 * is drawn once per repetition that falls within the visible
	 * [rangeMin, rangeMax] range, matching the repeated segments drawn by
	 * {@link #drawCurve}.
	 */
	private void drawControlPoints( final Graphics2D g )
	{
		final Curve curve = model.getCurve();
		final int n = palette.getLength();
		final boolean cyclic = model.getRangeMode() == RangeMode.CYCLIC && n > 1;
		final double backgroundShift = model.isTreatMinAsBackground() ? 1 : 0;

		for ( int i = 0; i < curve.getPointCount(); i++ )
		{
			final int py = pixelY( curve.getY( i ) );

			if ( cyclic )
			{
				final double offset = rangeMin + backgroundShift + curve.getX( i ) * ( n - 1 );
				final int kMin = ( int ) Math.floor( ( rangeMin - offset ) / n ) - 1;
				final int kMax = ( int ) Math.ceil( ( rangeMax - offset ) / n ) + 1;
				for ( int k = kMin; k <= kMax; k++ )
				{
					final double value = k * ( double ) n + offset;
					if ( value < rangeMin || value > rangeMax )
						continue;
					drawControlPointAt( g, pixelXFromValue( value ), py );
				}
			}
			else
			{
				drawControlPointAt( g, pixelX( curve.getX( i ) ), py );
			}
		}
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
	 * curve editor. It is the identity ramp through the palette (LUT index ==
	 * output value) and does not depend on the mapping curve, so it always
	 * looks the same for a given palette. Tick labels show the actual
	 * palette color index (0 to {@code palette.getLength() - 1}), the scale
	 * that matters to the user, rather than the internal 0-255 LUT index.
	 */
	private void drawOutputColorBar( final Graphics2D g )
	{
		final int barLeft = LABEL_WIDTH;
		final int top = plotTop();
		final int bottom = plotBottom();

		for ( int py = top; py < bottom; py++ )
		{
			final double t = ( bottom - py ) / ( double ) plotHeight();
			final int lutIndex = ( int ) Math.round( t * 255.0 );
			final int argb = LutPalette.lookupARGB( palette, 0, 255, lutIndex, model.getValueMatching() );
			g.setColor( new Color( argb ) );
			g.drawLine( barLeft, py, barLeft + COLORBAR_WIDTH, py );
		}

		g.setColor( new Color( 120, 120, 120 ) );
		g.drawRect( barLeft, top, COLORBAR_WIDTH, bottom - top );

		g.setColor( Color.DARK_GRAY );
		final FontMetrics fm = g.getFontMetrics();
		final int lastColor = palette.getLength() - 1;
		for ( final double frac : OUTPUT_TICK_FRACTIONS )
		{
			final String text = Integer.toString( ( int ) Math.round( frac * lastColor ) );
			final int py = pixelY( ( int ) Math.round( frac * 255.0 ) );
			g.drawString( text, barLeft - fm.stringWidth( text ) - 6, py + fm.getAscent() / 2 - 1 );
		}
	}

	/**
	 * Horizontal color bar along the x (input value) axis, showing the LUT
	 * color actually produced after passing each input value through the
	 * mapping curve ("after transform").
	 */
	private void drawTransformColorBar( final Graphics2D g )
	{
		final int left = plotLeft();
		final int right = plotRight();
		final int top = transformBarTop();
		final int bottom = transformBarBottom();

		for ( int px = left; px < right; px++ )
		{
			final double value = pixelXToValue( px );
			final int argb;
			if ( model.isBackgroundValue( value, rangeMin ) )
			{
				argb = model.getBackgroundColor();
			}
			else
			{
				final int lutIndex = model.mapToLutIndex( value, rangeMin, rangeMax, palette.getLength() );
				argb = LutPalette.lookupARGB( palette, 0, 255, lutIndex, model.getValueMatching() );
			}
			g.setColor( new Color( argb ) );
			g.drawLine( px, top, px, bottom );
		}

		g.setColor( new Color( 120, 120, 120 ) );
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
			final int px = pixelX( t );
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
		if ( !editMode )
			return;

		final Curve curve = model.getCurve();
		// Convert the click through the raw input value so that, in Cyclic
		// mode, clicking any repetition of the curve correctly hits/creates
		// the single underlying control point (see #valueToCurveX).
		final double x = valueToCurveX( pixelXToValue( e.getX() ) );
		final int y = toValueY( e.getY() );

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
		if ( editMode && draggedPoint != null && draggedPoint >= 0 )
		{
			model.getCurve().setPoint( draggedPoint, toValueY( e.getY() ) );
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
