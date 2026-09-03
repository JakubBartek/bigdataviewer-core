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
 * Displays the transfer function (input value -&gt; output value) as an
 * interactive graph, together with two color bars previewing the LUT:
 * a vertical one to the left, along the y (output value) axis, which is
 * always the identity ramp through the palette regardless of the curve, and
 * a horizontal one below, along the x (input value) axis, which shows the
 * color actually produced after passing each input value through the curve.
 * <p>
 * The panel also hosts two groups of controls, placed against the graph
 * itself rather than out in the dialog around it, because in each case the
 * position is what explains the control:
 * <ul>
 * <li>the <b>pencil</b> toggle beside the plot's own top-right corner (see
 * {@link #setEditMode(boolean)}); it is disabled for a discrete palette,
 * whose shape comes from its step size rather than from the curve;</li>
 * <li>the <b>range</b> boxes straddling the two ends of the x axis.</li>
 * </ul>
 * Control points are only shown, and editable, in edit mode; left-click adds
 * or drags a control point of the underlying {@link Curve}, right-click
 * removes one. Hovering or dragging one annotates it with the values that
 * place it (see {@link #drawHoverHint}).
 */
public class MappingCurvePanel extends JPanel implements MouseListener, MouseMotionListener
{
	private static final int POINT_RADIUS = 5;

	/** Gutter for the y-axis labels, which carry a palette color index: at most three digits. */
	private static final int LABEL_WIDTH = 40;

	/**
	 * Gutter past the right end of the x axis: holds the pencil toggle, beside
	 * the plot rather than overlapping it (see {@link #doLayout()}), and is
	 * comfortably wider than the half of the max box that also hangs over
	 * this edge.
	 */
	private static final int RIGHT_MARGIN = 36;

	private static final int TOP_MARGIN = 10;

	private static final int LABEL_HEIGHT = 26;

	private static final int RANGE_FIELD_WIDTH = 32;

	private static final int RANGE_FIELD_HEIGHT = 16;

	private static final int COLORBAR_WIDTH = 16;

	private static final int COLORBAR_GAP = 10;

	private static final int BASE_PREFERRED_HEIGHT = 226;

	/**
	 * The narrowest the plot itself (excluding the label/colorbar gutters) is
	 * worth drawing at -- what makes {@link #minimumGraphWidth()} keep the
	 * graph column comfortably wider than the settings column beside it,
	 * rather than merely as wide.
	 */
	private static final int MIN_PLOT_WIDTH = 300;

	/** Side of the square pencil toggle. */
	private static final int EDIT_TOGGLE_SIZE = 22;

	/** Gap between the plot's right edge and the pencil toggle beside it. */
	private static final int PENCIL_MARGIN_X = 6;

	/** Gap between the plot's top edge and the pencil toggle beside it. */
	private static final int PENCIL_MARGIN_Y = 0;

	/**
	 * Smallest pixel spacing at which the per-stop grid of a discrete palette
	 * is still worth drawing. Below it the lines merge into a smear that hides
	 * the very thing they are there to show, so they are dropped entirely.
	 */
	private static final int MIN_STOP_SPACING = 4;

	private static final Color CURVE_COLOR = Color.BLACK;

	/** The curve past the ends of the palette's domain, where its shape is the boundary condition's doing rather than the transfer function's. */
	private static final Color OUT_OF_DOMAIN_CURVE_COLOR = new Color( 0, 0, 0, 140 );

	private static final Color POINT_FILL = Color.WHITE;

	private static final Color POINT_BORDER = new Color( 230, 160, 20 );

	private static final Color GRID_COLOR = new Color( 225, 225, 225 );

	/** The per-stop grid of a discrete palette: lighter than {@link #GRID_COLOR}, since there are many more of these lines. */
	private static final Color STOP_GRID_COLOR = new Color( 239, 239, 239 );

	private static final Color FRAME_COLOR = new Color( 120, 120, 120 );

	/** The dashed marker at the raw value where the palette's domain ends. */
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

	/**
	 * The control point the pointer is over, or {@code null} if none -- what
	 * {@link #drawHoverHint} annotates. Kept separate from
	 * {@link #draggedPoint} because the hint is wanted <em>before</em> the drag
	 * starts, which is when the user is deciding where to put the point.
	 */
	private Integer hoveredPoint = null;

	/**
	 * Whether curve control points are shown and editable. Off by default,
	 * since most of the time the curve is left at its preset shape and only
	 * the palette/range settings are adjusted.
	 */
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

		// The min/max fields straddle the plot's left/right edges, centered on
		// them (clamped so the min field never runs into the max field).
		final int maxFieldX = curveXToPixelX( 1 ) - RANGE_FIELD_WIDTH / 2;
		final int minFieldX = Math.min( left - RANGE_FIELD_WIDTH / 2, maxFieldX - RANGE_FIELD_WIDTH );

		minField.setBounds( minFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );
		maxField.setBounds( maxFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT );

		// In the right margin beside the plot's top-right corner -- not
		// overlapping the plot itself, which would sit it on top of the curve
		// -- so it does not need a row of its own below the graph.
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

	/**
	 * Set the listener to be notified when edit mode is turned on or off,
	 * which happens through the pencil toggle this panel owns rather than
	 * through the dialog around it.
	 */
	public void setEditModeListener( final Consumer< Boolean > listener )
	{
		this.editModeListener = listener;
	}

	/**
	 * The narrowest this panel is worth being drawn at: enough for the plot
	 * itself to stay comfortably readable (see {@link #MIN_PLOT_WIDTH}), which
	 * keeps the graph column wider than the settings column beside it rather
	 * than merely matching it.
	 */
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

	/**
	 * Set whether curve control points are shown and can be added, dragged, or
	 * removed. When {@code false}, the curve is still drawn and the range
	 * fields still work, but the control points themselves are hidden and
	 * inert.
	 */
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

	/** Whether control points are currently shown and editable; see {@link #setEditMode(boolean)}. */
	public boolean isEditMode()
	{
		return editMode;
	}

	/**
	 * Follow the model's own kind: a discrete palette maps through its step
	 * size rather than through the curve, so there is no curve to edit and the
	 * pencil says so by being disabled -- turning edit mode off first, so that
	 * it is never left on with nothing to act on.
	 */
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

	/**
	 * The palette color index a curve output value in [0, 255] lands on -- the
	 * scale the y axis is labelled in, and so the one the hover chip quotes.
	 */
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

		// The new color-mapping representation renders the preview: the wrapper
		// turns each raw value straight into its final color, and its color
		// scheme is the palette bar. Rebuilt per paint from the current curve /
		// palette / range -- cheap for a preview, and always in sync.
		final PresetPaletteWrapper wrapper = PaletteWrapperBuilder.build( palette, model, rangeMin, rangeMax );
		final int paletteRangeLength = wrapper.getColorScheme().getPaletteRangeLength();
		final int[] stopBoundaries = model.isDiscrete() ? stopBoundaryColumns( wrapper ) : null;

		drawGrid( g2, stopBoundaries, paletteRangeLength );
		drawDomainEdge( g2, wrapper );
		drawCurve( g2, wrapper );
		// A discrete palette's shape comes from its step size, not the curve
		// (see LutEditorMapping), so there are no control points to show.
		if ( editMode && !model.isDiscrete() )
			drawControlPoints( g2 );
		drawOutputColorBar( g2, wrapper.getColorScheme() );
		drawTransformColorBar( g2, wrapper, stopBoundaries );
		// Last, so that its guides read over the color bars they run out to.
		drawHoverHint( g2 );
	}

	/**
	 * The plot's background grid. For a discrete palette this is the grid of
	 * the palette's own stops -- one horizontal line per color, and a vertical
	 * one wherever the color changes -- rather than the usual quarters: the
	 * transfer function itself is a plain ramp there (see {@link #drawCurve}),
	 * so it is this grid that carries the discreteness, showing exactly where
	 * one color gives way to the next.
	 * <p>
	 * {@code stopBoundaries} is {@code null} for a continuous palette, and the
	 * stop grid is skipped when its lines would be packed closer than
	 * {@link #MIN_STOP_SPACING}, where they would read as a smear.
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

	/** Whether the per-stop grid is open enough to read; see {@link #drawGrid}. */
	private boolean drawsStopGrid( final int[] stopBoundaries, final int paletteRangeLength )
	{
		if ( stopBoundaries == null )
			return false;
		if ( plotHeight() / ( double ) paletteRangeLength < MIN_STOP_SPACING )
			return false;
		return stopBoundaries.length == 0 || plotWidth() / ( double ) stopBoundaries.length >= MIN_STOP_SPACING;
	}

	/**
	 * The pixel columns at which a discrete palette's color changes, i.e.
	 * where the floored palette value moves to the next stop.
	 * <p>
	 * Read off the wrapper by scanning rather than computed from the step
	 * size, so that it stays right whatever produced the mapping: it picks up
	 * the wrap points of a cycling boundary condition for free, and it cannot
	 * drift from the color actually painted in the bar below, which is scanned
	 * the same way.
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

	/**
	 * Which color stop a palette value resolves to, exactly the way
	 * {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme} does --
	 * same floor, same clamp of {@code N} back onto the last stop.
	 */
	private static int stopIndex( final double paletteValue, final int paletteRangeLength )
	{
		return Math.max( 0, Math.min( paletteRangeLength - 1, ( int ) Math.floor( paletteValue ) ) );
	}

	/**
	 * A dashed marker at the raw value where the palette's domain ends, when
	 * that falls inside the displayed range. Only a discrete palette gets
	 * there: its domain ends at {@code min + stepSize * N} however wide the
	 * display range is (see {@code StepPresetFunc}), and everything to the
	 * right of the marker is the "above range" condition's doing rather than
	 * the transfer function's.
	 */
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
	 * Draws the transfer function actually being rendered: each pixel column's
	 * input value put through {@code wrapper}, so the line always agrees with
	 * the color bar below it. Deliberately read off the wrapper rather than
	 * {@link #model}'s own {@link Curve}, because the curve is only half the
	 * story -- a discrete (categorical) palette's shape comes from its step
	 * size instead (see {@link LutEditorMapping}), and drawing the curve there
	 * would show a mapping that is not the one in effect.
	 * <p>
	 * The line is drawn as the continuous function it is even for a discrete
	 * palette, where it is a plain ramp of slope {@code 1 / stepSize}:
	 * snapping to a color is the color scheme's job, not the function's, and a
	 * staircase here would claim the function itself has steps in it. What
	 * carries the discreteness instead is the grid the line crosses (see
	 * {@link #drawGrid}).
	 * <p>
	 * Past either end of the palette's domain the line goes dashed, since
	 * there its shape is decided by the boundary condition rather than by the
	 * function -- see {@link #drawDomainEdge}. Drawn as two whole paths rather
	 * than segment by segment, so that the dash pattern runs along the line
	 * instead of restarting, invisibly, at every pixel.
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
				// Pick the other path up at the previous point, so that the two
				// meet at the domain edge instead of leaving a gap there.
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

	/**
	 * A palette value (in {@code [0, paletteRangeLength]}) expressed in the
	 * graph's own {@code [0, 255]} output units.
	 */
	private static int paletteValueToOutput( final double paletteValue, final int paletteRangeLength )
	{
		final double clamped = Math.max( 0.0, Math.min( paletteRangeLength, paletteValue ) );
		return ( int ) Math.round( clamped / paletteRangeLength * 255.0 );
	}

	/** Draws each curve control point at its normalized (x, output) position, filling in the one the hover hint is about. */
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
			g.setColor( new Color( scheme.getRGB( t * paletteRangeLength ) ) );
			// fillRect, not drawLine: the stroke is whatever the last caller
			// left set (drawCurve uses 2px) and antialiasing is on, which
			// together smear each 1px row across its neighbours instead of
			// laying down the exact color.
			g.fillRect( barLeft, py, COLORBAR_WIDTH + 1, 1 );
		}

		g.setColor( FRAME_COLOR );
		// Explicit 1px stroke: whatever was set last (drawCurve leaves 2px)
		// would otherwise be antialiased over the bar's own edge columns.
		g.setStroke( THIN_STROKE );
		g.drawRect( barLeft, top, COLORBAR_WIDTH, bottom - top );

		g.setColor( Color.DARK_GRAY );
		if ( labelsEveryBand( paletteRangeLength, g.getFontMetrics() ) )
			drawBandLabels( g, barLeft, paletteRangeLength );
		else
			drawFractionLabels( g, barLeft );
	}

	/**
	 * Whether every color of a discrete palette gets its own label, which is
	 * only useful while the bands are tall enough to tell apart and few enough
	 * to leave room for the text between them.
	 */
	private boolean labelsEveryBand( final int paletteRangeLength, final FontMetrics fm )
	{
		return model.isDiscrete() && plotHeight() / ( double ) paletteRangeLength >= fm.getHeight() + 2;
	}

	/**
	 * One label per color of a discrete palette, centered on its band: with
	 * the palette used as individually chosen colors, <em>which</em> color a
	 * value lands on is the thing being read off the axis, so the axis names
	 * them all rather than sampling itself at quarters.
	 */
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

	/** Labels at fixed fractions of the palette, for a continuous palette (or a discrete one with more colors than the axis has room to name). */
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

	/**
	 * Horizontal color bar along the x (input value) axis, showing the color
	 * actually produced after passing each input value through the whole
	 * mapping ("after transform") -- i.e. exactly what the renderer would show
	 * for that raw value, straight from
	 * {@link PresetPaletteWrapper#getRGBForRaw(double)} (curve, then color
	 * scheme, then boundary handling).
	 * <p>
	 * For a discrete palette a tick is drawn under every color change, which
	 * puts the input value at which one color gives way to the next on the axis
	 * that value belongs to.
	 */
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
			// fillRect rather than drawLine -- see drawOutputColorBar.
			g.fillRect( px, top, 1, bottom - top + 1 );
		}

		g.setColor( FRAME_COLOR );
		// See drawOutputColorBar's border for why the stroke is set explicitly.
		g.setStroke( THIN_STROKE );
		g.drawRect( left, top, right - left, bottom - top );

		if ( drawsStopGrid( stopBoundaries, wrapper.getColorScheme().getPaletteRangeLength() ) )
			for ( final int x : stopBoundaries )
				g.drawLine( x, bottom + 1, x, bottom + 4 );

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

	/**
	 * The control point the hover hint is currently about -- the one being
	 * dragged, or failing that the one under the pointer -- or {@code -1}.
	 */
	private int annotatedPoint()
	{
		if ( draggedPoint != null && draggedPoint >= 0 )
			return draggedPoint;
		return hoveredPoint == null ? -1 : hoveredPoint;
	}

	/**
	 * Annotates the control point under the pointer (or the one being dragged)
	 * with the two numbers that place it: the input value it sits at and the
	 * palette color it maps to, in the units the two axes are labelled in.
	 * Guides run out to both color bars and across them, so that the chip says
	 * what the point is worth while the guides say where that lands in the data
	 * and in the palette -- which is what lets a point be put on a value the
	 * user actually has in mind, instead of by eye.
	 */
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

	/** The small dark label carrying the hover hint's numbers, to the right of the point unless that would run off the plot. */
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

	/**
	 * Track which control point the pointer is over, repainting only when the
	 * answer changes -- a repaint per mouse-move event would redraw both color
	 * bars, pixel column by pixel column, for nothing.
	 */
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

	/**
	 * The pencil on the edit toggle, drawn rather than loaded from a resource
	 * so that it picks up the disabled color when the palette is discrete and
	 * there is no curve to edit.
	 */
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
