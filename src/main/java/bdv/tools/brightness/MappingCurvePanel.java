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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import bdv.tools.brightness.colorscheme.ColorScheme;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.PresetFunc;

/**
 * Interactive graph of the transfer function, with the palette as a color bar
 * along the y axis and the resulting colors along the x axis. Hosts the pencil
 * toggle ({@link #setEditMode(boolean)}) and the range boxes at the ends of the
 * x axis.
 * <p>
 * In edit mode, left-click adds or drags a {@link Curve} control point and
 * right-click removes one.
 */
public class MappingCurvePanel extends JPanel implements MouseListener, MouseMotionListener
{
	private static final int POINT_RADIUS = 5;

	/** Fits a three-digit palette index. */
	private static final int LABEL_WIDTH = 40;

	/** Holds the pencil toggle and half the max box. */
	private static final int RIGHT_MARGIN = 36;

	private static final int TOP_MARGIN = 10;

	private static final int LABEL_HEIGHT = 26;

	private static final int RANGE_FIELD_WIDTH = 64;

	private static final int RANGE_FIELD_HEIGHT = 16;

	private static final int COLORBAR_WIDTH = 16;

	private static final int COLORBAR_GAP = 10;

	private static final int BASE_PREFERRED_HEIGHT = 226;

	/** Plot width excluding gutters. */
	private static final int MIN_PLOT_WIDTH = 300;

	private static final int EDIT_TOGGLE_SIZE = 22;

	private static final int PENCIL_MARGIN_X = 6;

	private static final int PENCIL_MARGIN_Y = 0;

	/** Below this, the discrete stop grid is not drawn. */
	private static final int MIN_STOP_SPACING = 4;

	private static final Color CURVE_COLOR = Color.BLACK;

	private static final Color OUT_OF_DOMAIN_CURVE_COLOR = new Color( 0, 0, 0, 140 );

	private static final Color POINT_FILL = Color.WHITE;

	private static final Color POINT_BORDER = new Color( 230, 160, 20 );

	private static final Color GRID_COLOR = new Color( 225, 225, 225 );

	private static final Color STOP_GRID_COLOR = new Color( 239, 239, 239 );

	private static final Color FRAME_COLOR = new Color( 120, 120, 120 );

	private static final Color DOMAIN_EDGE_COLOR = new Color( 176, 176, 176 );

	private static final Color CHIP_BACKGROUND = new Color( 60, 60, 60 );

	private static final Stroke SOLID_CURVE_STROKE = new BasicStroke( 2 );

	private static final Stroke DASHED_CURVE_STROKE = new BasicStroke( 2, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_ROUND, 10f, new float[] { 5f, 4f }, 0f );

	private static final Stroke POINT_STROKE = new BasicStroke( 2 );

	private static final Stroke THIN_STROKE = new BasicStroke( 1 );

	private static final Stroke DASHED_THIN_STROKE = new BasicStroke( 1, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_ROUND, 10f, new float[] { 4f, 3f }, 0f );

	private static final Stroke GUIDE_STROKE = new BasicStroke( 1, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_ROUND, 10f, new float[] { 3f, 3f }, 0f );

	private static final double[] OUTPUT_TICK_FRACTIONS = { 0.0, 0.25, 0.5, 0.75, 1.0 };

	private static final String EDIT_TOOLTIP = "Edit the transfer function";

	private static final String EDIT_TOOLTIP_DISCRETE = "Only editable for a continuous palette";

	private final LutEditorMapping model;

	private double rangeMin = 0;

	private double rangeMax = 255;

	private Palette palette = Palette.DEFAULT;

	private Integer draggedPoint = null;

	/** Separate from {@link #draggedPoint} so the hover hint shows before a drag starts. */
	private Integer hoveredPoint = null;

	private boolean editMode = false;

	private final JTextField minField = new JTextField();

	private final JTextField maxField = new JTextField();

	private final JToggleButton buttonEditCurve = new JToggleButton( new PencilIcon() );

	private BiConsumer< Double, Double > rangeChangeListener = null;

	private Consumer< Boolean > editModeListener = null;

	public MappingCurvePanel( final LutEditorMapping model )
	{
		this.model = model;
		setPreferredSize( new Dimension( 280, BASE_PREFERRED_HEIGHT ) );
		setBackground( Color.WHITE );
		addMouseListener( this );
		addMouseMotionListener( this );

		model.addChangeListener( this::syncToModel );

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

		buttonEditCurve.setToolTipText( EDIT_TOOLTIP );
		buttonEditCurve.setFocusable( false );
		buttonEditCurve.setMargin( new Insets( 0, 0, 0, 0 ) );
		buttonEditCurve.addActionListener( e -> setEditMode( buttonEditCurve.isSelected() ) );
		add( buttonEditCurve );
	}

	@Override
	public void doLayout()
	{
		if ( getWidth() <= 0 || getHeight() <= 0 )
			return;
		final int y = transformBarBottom() + 2;
		final int left = plotLeft();

		// centered on the plot edges, min clamped so it never overlaps max
		final int maxFieldX = curveXToPixelX( 1 ) - RANGE_FIELD_WIDTH / 2;
		final int minFieldX = Math.min( left - RANGE_FIELD_WIDTH / 2, maxFieldX - RANGE_FIELD_WIDTH );

		minField.setBounds( minFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
		maxField.setBounds( maxFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );

		// beside the plot, not over the curve
		buttonEditCurve.setBounds( plotRight() + PENCIL_MARGIN_X,
				plotTop() + PENCIL_MARGIN_Y, EDIT_TOGGLE_SIZE, EDIT_TOGGLE_SIZE );
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
		revalidate();
		repaint();
	}

	/** Notified when the user edits the range boxes. */
	public void setRangeChangeListener( final BiConsumer< Double, Double > listener )
	{
		this.rangeChangeListener = listener;
	}

	/** Notified when the pencil toggle changes edit mode. */
	public void setEditModeListener( final Consumer< Boolean > listener )
	{
		this.editModeListener = listener;
	}
	public int minimumGraphWidth()
	{
		return plotLeft() + MIN_PLOT_WIDTH + RIGHT_MARGIN;
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

	/** Whether curve control points are shown and editable. */
	public void setEditMode( final boolean editMode )
	{
		if ( this.editMode == editMode )
			return;
		this.editMode = editMode;
		buttonEditCurve.setSelected( editMode );
		if ( !editMode )
			hoveredPoint = null;
		if ( editModeListener != null )
			editModeListener.accept( editMode );
		repaint();
	}

	public boolean isEditMode()
	{
		return editMode;
	}

	/** A discrete palette has no curve to edit, so the pencil is disabled. */
	private void syncToModel()
	{
		final boolean editable = !model.isDiscrete();
		if ( !editable )
			setEditMode( false );
		buttonEditCurve.setEnabled( editable );
		buttonEditCurve.setToolTipText( editable ? EDIT_TOOLTIP : EDIT_TOOLTIP_DISCRETE );
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
	// pixels, raw values [rangeMin, rangeMax], curve x [0, 1], curve output [0, 255]

	private double pixelXToValue( final int pixelX )
	{
		final double normX = Math.max( 0.0, Math.min( 1.0, ( pixelX - plotLeft() ) / ( double ) plotWidth() ) );
		return rangeMin + normX * ( rangeMax - rangeMin );
	}

	private double valueToCurveX( final double value )
	{
		final double span = rangeMax - rangeMin;
		final double frac = span > 0 ? ( value - rangeMin ) / span : 0.0;
		return Math.max( 0.0, Math.min( 1.0, frac ) );
	}

	private int curveXToPixelX( final double normX )
	{
		return plotLeft() + ( int ) Math.round( normX * plotWidth() );
	}

	private int pixelYToOutput( final int pixelY )
	{
		final double v = ( plotBottom() - pixelY ) / ( double ) plotHeight() * 255.0;
		return Math.max( 0, Math.min( 255, ( int ) Math.round( v ) ) );
	}

	private int outputToPixelY( final int outputValue )
	{
		return plotBottom() - ( int ) Math.round( outputValue / 255.0 * plotHeight() );
	}

	/** The scale the y axis is labelled in. */
	private int outputToColorIndex( final int outputValue )
	{
		return ( int ) Math.round( outputValue / 255.0 * ( palette.getLength() - 1 ) );
	}

	@Override
	protected void paintComponent( final Graphics g )
	{
		super.paintComponent( g );
		final Graphics2D g2 = ( Graphics2D ) g;
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

		// rebuilt per paint so the preview is always in sync
		final PresetPaletteWrapper wrapper = PaletteWrapperBuilder.build( palette, model, rangeMin, rangeMax );
		final int paletteRangeLength = wrapper.getColorScheme().getPaletteRangeLength();
		final int[] stopBoundaries = model.isDiscrete() ? stopBoundaryColumns( wrapper ) : null;

		drawGrid( g2, stopBoundaries, paletteRangeLength );
		drawDomainEdge( g2, wrapper );
		drawCurve( g2, wrapper );
		if ( editMode && !model.isDiscrete() )
			drawControlPoints( g2 );
		drawOutputColorBar( g2, wrapper.getColorScheme() );
		drawTransformColorBar( g2, wrapper, stopBoundaries );
		// last, so its guides draw over the color bars
		drawHoverHint( g2 );
	}

	/**
	 * Quarters, or for a discrete palette one line per stop, which is what
	 * shows the discreteness. {@code stopBoundaries} is {@code null} for a
	 * continuous palette.
	 */
	private void drawGrid( final Graphics2D g, final int[] stopBoundaries, final int paletteRangeLength )
	{
		final int left = plotLeft();
		final int right = plotRight();
		final int top = plotTop();
		final int bottom = plotBottom();

		g.setStroke( THIN_STROKE );
		if ( drawsStopGrid( stopBoundaries, paletteRangeLength ) )
		{
			g.setColor( STOP_GRID_COLOR );
			for ( int stop = 1; stop < paletteRangeLength; stop++ )
			{
				final int y = outputToPixelY( ( int ) Math.round( stop / ( double ) paletteRangeLength * 255.0 ) );
				g.drawLine( left, y, right, y );
			}
			for ( final int x : stopBoundaries )
				g.drawLine( x, top, x, bottom );
		}
		else
		{
			g.setColor( GRID_COLOR );
			for ( int i = 1; i < 4; i++ )
			{
				final int x = left + i * ( right - left ) / 4;
				g.drawLine( x, top, x, bottom );
				final int y = top + i * ( bottom - top ) / 4;
				g.drawLine( left, y, right, y );
			}
		}

		g.setColor( FRAME_COLOR );
		g.drawRect( left, top, right - left, bottom - top );
	}

	private boolean drawsStopGrid( final int[] stopBoundaries, final int paletteRangeLength )
	{
		if ( stopBoundaries == null )
			return false;
		if ( plotHeight() / ( double ) paletteRangeLength < MIN_STOP_SPACING )
			return false;
		return stopBoundaries.length == 0 || plotWidth() / ( double ) stopBoundaries.length >= MIN_STOP_SPACING;
	}

	/**
	 * Pixel columns where a discrete palette's color changes. Scanned from the
	 * wrapper rather than computed, so CYCLE wraps are included and it matches
	 * the color bar.
	 */
	private int[] stopBoundaryColumns( final PresetPaletteWrapper wrapper )
	{
		final int paletteRangeLength = wrapper.getColorScheme().getPaletteRangeLength();
		final int left = plotLeft();
		final int right = plotRight();
		final int[] columns = new int[ Math.max( 1, right - left + 1 ) ];
		int count = 0;
		int previousStop = Integer.MIN_VALUE;
		for ( int px = left; px <= right; px++ )
		{
			final int stop = stopIndex( wrapper.getPaletteValueForRaw( pixelXToValue( px ) ), paletteRangeLength );
			if ( previousStop != Integer.MIN_VALUE && stop != previousStop )
				columns[ count++ ] = px;
			previousStop = stop;
		}
		final int[] result = new int[ count ];
		System.arraycopy( columns, 0, result, 0, count );
		return result;
	}

	/** As {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme} resolves it. */
	private static int stopIndex( final double paletteValue, final int paletteRangeLength )
	{
		return Math.max( 0, Math.min( paletteRangeLength - 1, ( int ) Math.floor( paletteValue ) ) );
	}

	/** Marks where the domain ends inside the display range (only happens for a discrete palette). */
	private void drawDomainEdge( final Graphics2D g, final PresetPaletteWrapper wrapper )
	{
		final double domainMax = wrapper.getPresetFunc().getMax();
		if ( !( domainMax > rangeMin ) || !( domainMax < rangeMax ) )
			return;
		final int x = curveXToPixelX( valueToCurveX( domainMax ) );
		g.setColor( DOMAIN_EDGE_COLOR );
		g.setStroke( DASHED_THIN_STROKE );
		g.drawLine( x, plotTop(), x, plotBottom() );
	}

	/**
	 * Read off the wrapper, not the {@link Curve}, which a discrete palette
	 * ignores. Drawn continuous even then: snapping is the color scheme's job.
	 * Dashed outside the domain; two whole paths so the dash pattern does not
	 * restart at every pixel.
	 */
	private void drawCurve( final Graphics2D g, final PresetPaletteWrapper wrapper )
	{
		final PresetFunc presetFunc = wrapper.getPresetFunc();
		final double domainMin = presetFunc.getMin();
		final double domainMax = presetFunc.getMax();
		final int paletteRangeLength = wrapper.getColorScheme().getPaletteRangeLength();
		final int left = plotLeft();
		final int right = plotRight();

		final Path2D.Double inDomain = new Path2D.Double();
		final Path2D.Double outOfDomain = new Path2D.Double();

		int previousX = 0;
		int previousY = 0;
		boolean previousIn = false;
		boolean started = false;
		for ( int px = left; px <= right; px++ )
		{
			final double value = pixelXToValue( px );
			final boolean in = value >= domainMin && value <= domainMax;
			final int py = outputToPixelY( paletteValueToOutput( wrapper.getPaletteValueForRaw( value ), paletteRangeLength ) );
			final Path2D.Double path = in ? inDomain : outOfDomain;
			if ( !started )
				path.moveTo( px, py );
			else if ( in == previousIn )
				path.lineTo( px, py );
			else
			{
				// start at the previous point so the paths meet
				path.moveTo( previousX, previousY );
				path.lineTo( px, py );
			}
			previousX = px;
			previousY = py;
			previousIn = in;
			started = true;
		}

		g.setColor( CURVE_COLOR );
		g.setStroke( SOLID_CURVE_STROKE );
		g.draw( inDomain );
		g.setColor( OUT_OF_DOMAIN_CURVE_COLOR );
		g.setStroke( DASHED_CURVE_STROKE );
		g.draw( outOfDomain );
	}

	private static int paletteValueToOutput( final double paletteValue, final int paletteRangeLength )
	{
		final double clamped = Math.max( 0.0, Math.min( paletteRangeLength, paletteValue ) );
		return ( int ) Math.round( clamped / paletteRangeLength * 255.0 );
	}

	private void drawControlPoints( final Graphics2D g )
	{
		final Curve curve = model.getCurve();
		final int highlighted = annotatedPoint();
		for ( int i = 0; i < curve.getPointCount(); i++ )
			drawControlPointAt( g, curveXToPixelX( curve.getX( i ) ), outputToPixelY( curve.getY( i ) ), i == highlighted );
	}

	private void drawControlPointAt( final Graphics2D g, final int px, final int py, final boolean highlighted )
	{
		g.setColor( highlighted ? POINT_BORDER : POINT_FILL );
		g.fillOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );
		g.setColor( POINT_BORDER );
		g.setStroke( POINT_STROKE );
		g.drawOval( px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS );
	}

	/** The palette itself, labelled in color indices rather than 0-255. */
	private void drawOutputColorBar( final Graphics2D g, final ColorScheme scheme )
	{
		final int barLeft = LABEL_WIDTH;
		final int top = plotTop();
		final int bottom = plotBottom();

		final int paletteRangeLength = scheme.getPaletteRangeLength();

		for ( int py = top; py < bottom; py++ )
		{
			final double t = ( bottom - py ) / ( double ) plotHeight();
			g.setColor( new Color( scheme.getRGB( t * paletteRangeLength ) ) );
			// fillRect: an antialiased drawLine with a leftover 2px stroke smears rows
			g.fillRect( barLeft, py, COLORBAR_WIDTH + 1, 1 );
		}

		g.setColor( FRAME_COLOR );
		g.setStroke( THIN_STROKE );
		g.drawRect( barLeft, top, COLORBAR_WIDTH, bottom - top );

		g.setColor( Color.DARK_GRAY );
		if ( labelsEveryBand( paletteRangeLength, g.getFontMetrics() ) )
			drawBandLabels( g, barLeft, paletteRangeLength );
		else
			drawFractionLabels( g, barLeft );
	}

	/** Only for a discrete palette whose bands are tall enough for text. */
	private boolean labelsEveryBand( final int paletteRangeLength, final FontMetrics fm )
	{
		return model.isDiscrete() && plotHeight() / ( double ) paletteRangeLength >= fm.getHeight() + 2;
	}

	private void drawBandLabels( final Graphics2D g, final int barLeft, final int paletteRangeLength )
	{
		final FontMetrics fm = g.getFontMetrics();
		for ( int stop = 0; stop < paletteRangeLength; stop++ )
		{
			final String text = Integer.toString( stop );
			final int py = outputToPixelY( ( int ) Math.round( ( stop + 0.5 ) / paletteRangeLength * 255.0 ) );
			g.drawString( text, barLeft - fm.stringWidth( text ) - 6, py + fm.getAscent() / 2 - 1 );
		}
	}

	private void drawFractionLabels( final Graphics2D g, final int barLeft )
	{
		final FontMetrics fm = g.getFontMetrics();
		final int lastColor = palette.getLength() - 1;
		for ( final double frac : OUTPUT_TICK_FRACTIONS )
		{
			final String text = Integer.toString( ( int ) Math.round( frac * lastColor ) );
			final int py = outputToPixelY( ( int ) Math.round( frac * 255.0 ) );
			g.drawString( text, barLeft - fm.stringWidth( text ) - 6, py + fm.getAscent() / 2 - 1 );
		}
	}

	/** The rendered color for each raw value, with a tick under each discrete color change. */
	private void drawTransformColorBar( final Graphics2D g, final PresetPaletteWrapper wrapper, final int[] stopBoundaries )
	{
		final int left = plotLeft();
		final int right = plotRight();
		final int top = transformBarTop();
		final int bottom = transformBarBottom();

		for ( int px = left; px < right; px++ )
		{
			final double value = pixelXToValue( px );
			g.setColor( new Color( wrapper.getRGBForRaw( value ) ) );
			g.fillRect( px, top, 1, bottom - top + 1 );
		}

		g.setColor( FRAME_COLOR );
		g.setStroke( THIN_STROKE );
		g.drawRect( left, top, right - left, bottom - top );

		if ( drawsStopGrid( stopBoundaries, wrapper.getColorScheme().getPaletteRangeLength() ) )
			for ( final int x : stopBoundaries )
				g.drawLine( x, bottom + 1, x, bottom + 4 );

		// min and max are the range boxes
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

	/** The dragged point, else the hovered one, else {@code -1}. */
	private int annotatedPoint()
	{
		if ( draggedPoint != null && draggedPoint >= 0 )
			return draggedPoint;
		return hoveredPoint == null ? -1 : hoveredPoint;
	}

	/** Labels the annotated point with its raw value and color index, with guides to both color bars. */
	private void drawHoverHint( final Graphics2D g )
	{
		if ( !editMode || model.isDiscrete() )
			return;
		final int index = annotatedPoint();
		final Curve curve = model.getCurve();
		if ( index < 0 || index >= curve.getPointCount() )
			return;

		final int px = curveXToPixelX( curve.getX( index ) );
		final int py = outputToPixelY( curve.getY( index ) );

		g.setColor( POINT_BORDER );
		g.setStroke( GUIDE_STROKE );
		g.drawLine( LABEL_WIDTH, py, px, py );
		g.drawLine( px, py, px, transformBarBottom() );

		final double value = rangeMin + curve.getX( index ) * ( rangeMax - rangeMin );
		drawChip( g, px, py, formatValue( value ) + "  →  " + outputToColorIndex( curve.getY( index ) ) );
	}

	/** Right of the point unless that would run off the plot. */
	private void drawChip( final Graphics2D g, final int px, final int py, final String text )
	{
		final FontMetrics fm = g.getFontMetrics();
		final int w = fm.stringWidth( text ) + 14;
		final int h = fm.getHeight() + 4;
		final int gap = POINT_RADIUS + 5;
		final int x = px + gap + w <= plotRight() ? px + gap : px - gap - w;
		final int y = Math.max( plotTop(), Math.min( plotBottom() - h, py - h / 2 ) );

		g.setColor( CHIP_BACKGROUND );
		g.fillRoundRect( x, y, w, h, 6, 6 );
		g.setColor( Color.WHITE );
		g.drawString( text, x + 7, y + fm.getAscent() + 2 );
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
				hoveredPoint = null;
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
		updateHoveredPoint( e );
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
		if ( hoveredPoint != null )
		{
			hoveredPoint = null;
			repaint();
		}
	}

	@Override
	public void mouseMoved( final MouseEvent e )
	{
		updateHoveredPoint( e );
	}

	/** Repaints only when the hovered point changes; repainting is expensive. */
	private void updateHoveredPoint( final MouseEvent e )
	{
		if ( !editMode || model.isDiscrete() )
			return;
		final double x = valueToCurveX( pixelXToValue( e.getX() ) );
		final int y = pixelYToOutput( e.getY() );
		final int found = model.getCurve().findNearestPoint( x, y / 255.0, 0.04 );
		final Integer updated = found < 0 ? null : found;
		if ( !Objects.equals( updated, hoveredPoint ) )
		{
			hoveredPoint = updated;
			repaint();
		}
	}

	/** Drawn rather than loaded, so it greys out when disabled. */
	private static class PencilIcon implements Icon
	{
		private static final int SIZE = 16;

		private static final Color ENABLED_COLOR = new Color( 60, 60, 60 );

		private static final Color DISABLED_COLOR = new Color( 168, 168, 168 );

		@Override
		public void paintIcon( final Component c, final Graphics g, final int x, final int y )
		{
			final Graphics2D g2 = ( Graphics2D ) g.create();
			try
			{
				g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
				g2.translate( x, y );
				g2.setColor( c.isEnabled() ? ENABLED_COLOR : DISABLED_COLOR );
				g2.setStroke( new BasicStroke( 1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND ) );

				final Path2D.Double body = new Path2D.Double();
				body.moveTo( 11.1, 2.2 );
				body.lineTo( 13.8, 4.9 );
				body.lineTo( 5.7, 13.0 );
				body.lineTo( 2.2, 13.8 );
				body.lineTo( 3.0, 10.3 );
				body.closePath();
				g2.draw( body );
				g2.draw( new Line2D.Double( 9.8, 3.5, 12.5, 6.2 ) );
				g2.draw( new Line2D.Double( 2.2, 13.8, 4.6, 12.6 ) );
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth()
		{
			return SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return SIZE;
		}
	}

	private static final long serialVersionUID = 1L;
}
