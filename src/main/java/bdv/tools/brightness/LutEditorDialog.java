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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import net.imglib2.display.ColorTable;

/**
 * A LUT editor dialog. It separates two independent concerns:
 * <ul>
 * <li><b>Data</b>: which source/setup is being edited, and which color
 * palette (LUT) is used to render it.</li>
 * <li><b>Mapping</b>: how a raw source value is turned into an index into
 * that palette, via a user-editable curve over the source's display
 * range.</li>
 * </ul>
 */
public class LutEditorDialog extends JDialog
{
	private final ConverterSetups converterSetups;
	private final ViewerState viewerState;
	private final Runnable repaintAction;

	private final List< SourceAndConverter< ? > > sources = new ArrayList<>();

	private final JComboBox< String > comboSource;
	private final JComboBox< Object > comboPalette;
	private final JLabel labelStatus;

	private final GradientPreviewPanel panelPaletteSwatch;
	private final MappingCurvePanel panelMappingCurve;

	private final JRadioButton radioFit;
	private final JRadioButton radioCyclic;
	private final JCheckBox checkTreatMinAsBackground;
	private final JButton buttonBackgroundColor;
	private final JComboBox< MappingPreset > comboMappingPreset;
	private final JButton buttonInvertCurve;

	/** The palette and mapping currently being edited (not yet applied until "Apply" is pressed). */
	private ColorTable currentPalette = ColorTableLut.DEFAULT;
	private final MappingModel mappingModel = new MappingModel();

	/** The input value range currently being edited (not yet applied until "Apply" is pressed). */
	private double editedRangeMin = 0;
	private double editedRangeMax = 255;

	/** Guards against control listeners firing while we are programmatically syncing them. */
	private boolean loadingControls = false;

	public LutEditorDialog( final Frame owner, final ConverterSetups converterSetups, final ViewerState viewerState, final Runnable repaintAction )
	{
		super( owner, "LUT Editor", false );
		this.converterSetups = converterSetups;
		this.viewerState = viewerState;
		this.repaintAction = repaintAction;

		// -- Widgets ---------------------------------------------------------
		comboSource = new JComboBox<>();
		comboPalette = createPaletteCombo();
		panelPaletteSwatch = new GradientPreviewPanel( mappingModel );
		panelPaletteSwatch.setPreferredSize( new Dimension( 200, 16 ) );
		panelPaletteSwatch.setMaximumSize( new Dimension( Integer.MAX_VALUE, 16 ) );

		radioFit = new JRadioButton( "Fit" );
		radioCyclic = new JRadioButton( "Cyclic" );
		radioFit.setSelected( true );
		checkTreatMinAsBackground = new JCheckBox();
		buttonBackgroundColor = createBackgroundColorButton();
		comboMappingPreset = new JComboBox<>( MappingPreset.values() );
		buttonInvertCurve = new JButton( "Invert" );
		buttonInvertCurve.setFocusable( false );

		panelMappingCurve = new MappingCurvePanel( mappingModel );
		panelMappingCurve.setRangeChangeListener( ( min, max ) ->
		{
			editedRangeMin = min;
			editedRangeMax = max;
			updateTreatMinAsBackgroundText();
		} );
		updateTreatMinAsBackgroundText();

		labelStatus = new JLabel( "" );

		// -- Layout ----------------------------------------------------------
		setLayout( new BorderLayout( 0, 4 ) );
		( ( JPanel ) getContentPane() ).setBorder( new EmptyBorder( 12, 12, 12, 12 ) );

		final JPanel panelLeftColumn = createLeftColumn();
		final JPanel panelMappingCurveColumn = createMappingCurveColumn();

		final JPanel panelCenter = new JPanel( new BorderLayout( 12, 0 ) );
		panelCenter.add( panelLeftColumn, BorderLayout.WEST );
		panelCenter.add( hugContents( panelMappingCurveColumn ), BorderLayout.CENTER );
		add( panelCenter, BorderLayout.CENTER );
		add( createBottomBar(), BorderLayout.SOUTH );

		// -- Behavior --------------------------------------------------------
		installControlListeners();
		rebuildList();
		packAndMatchGraphWidth( panelLeftColumn, panelMappingCurveColumn );
	}

	/**
	 * The color palette chooser: every discovered palette, grouped under a
	 * non-selectable {@link CategoryHeader} per {@link LutCategories} category.
	 */
	private JComboBox< Object > createPaletteCombo()
	{
		final PaletteComboModel model = new PaletteComboModel();
		for ( final Map.Entry< String, List< String > > category : LutCategories.groupByCategory( LutPalettes.discoverNames() ).entrySet() )
		{
			model.addElement( new CategoryHeader( category.getKey() ) );
			for ( final String name : category.getValue() )
				model.addElement( name );
		}

		final JComboBox< Object > combo = new JComboBox<>( model );
		combo.setRenderer( new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent( final JList< ? > list, final Object value,
					final int index, final boolean isSelected, final boolean cellHasFocus )
			{
				final boolean header = value instanceof CategoryHeader;
				super.getListCellRendererComponent( list, value, index, isSelected && !header, cellHasFocus && !header );
				if ( header )
				{
					setFont( getFont().deriveFont( Font.BOLD ) );
					setEnabled( false );
					setBorder( BorderFactory.createEmptyBorder( 4, 0, 2, 4 ) );
				}
				else
				{
					setFont( getFont().deriveFont( Font.PLAIN ) );
					setEnabled( true );
					setBorder( BorderFactory.createEmptyBorder( 0, 4, 0, 4 ) );
					if ( index == -1 && value == null )
						setText( "Select Preset" );
				}
				return this;
			}
		} );
		combo.setSelectedIndex( -1 );
		return combo;
	}

	/** The small swatch button that opens the background color chooser. */
	private static JButton createBackgroundColorButton()
	{
		final JButton button = new JButton();
		button.setToolTipText( "Background color" );
		final Dimension size = new Dimension( 20, 20 );
		button.setPreferredSize( size );
		button.setMinimumSize( size );
		button.setMaximumSize( size );
		button.setBackground( new Color( 0xff000000, false ) );
		// Only meaningful once checkTreatMinAsBackground is checked.
		button.setEnabled( false );
		return button;
	}

	/** The "Data" and "Mapping" panels, stacked. */
	private JPanel createLeftColumn()
	{
		final JPanel panelData = new JPanel();
		panelData.setLayout( new BoxLayout( panelData, BoxLayout.PAGE_AXIS ) );
		panelData.setBorder( BorderFactory.createTitledBorder( "Data" ) );
		panelData.add( labeledRow( "Source:", comboSource ) );
		panelData.add( Box.createVerticalStrut( 8 ) );
		panelData.add( labeledRow( "Color palette:", comboPalette ) );
		panelData.add( Box.createVerticalStrut( 4 ) );
		panelData.add( panelPaletteSwatch );
		panelData.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelData.setMaximumSize( new Dimension( Integer.MAX_VALUE, panelData.getPreferredSize().height ) );

		final ButtonGroup groupRangeMode = new ButtonGroup();
		groupRangeMode.add( radioFit );
		groupRangeMode.add( radioCyclic );

		final JPanel panelRangeMode = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panelRangeMode.add( new JLabel( "Range mode:" ) );
		panelRangeMode.add( Box.createHorizontalStrut( 8 ) );
		panelRangeMode.add( radioFit );
		panelRangeMode.add( Box.createHorizontalStrut( 4 ) );
		panelRangeMode.add( radioCyclic );
		panelRangeMode.add( Box.createHorizontalStrut( 8 ) );
		panelRangeMode.add( checkTreatMinAsBackground );
		panelRangeMode.add( Box.createHorizontalStrut( 4 ) );
		panelRangeMode.add( buttonBackgroundColor );
		panelRangeMode.setAlignmentX( Component.LEFT_ALIGNMENT );

		final JPanel panelMapping = new JPanel();
		panelMapping.setLayout( new BoxLayout( panelMapping, BoxLayout.PAGE_AXIS ) );
		panelMapping.setBorder( BorderFactory.createTitledBorder( "Mapping" ) );
		panelMapping.add( Box.createVerticalStrut( 4 ) );
		panelMapping.add( panelRangeMode );
		panelMapping.add( Box.createVerticalStrut( 4 ) );
		panelMapping.add( labeledRow( "Mapping preset:", comboMappingPreset, buttonInvertCurve ) );
		panelMapping.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelMapping.setMaximumSize( new Dimension( Integer.MAX_VALUE, panelMapping.getPreferredSize().height ) );

		final JPanel column = new JPanel();
		column.setLayout( new BoxLayout( column, BoxLayout.PAGE_AXIS ) );
		column.add( panelData );
		column.add( Box.createVerticalStrut( 20 ) );
		column.add( panelMapping );
		return column;
	}

	/** The titled "Mapping curve" panel around the interactive graph. */
	private JPanel createMappingCurveColumn()
	{
		final JPanel column = new JPanel( new BorderLayout() );
		column.setBorder( BorderFactory.createTitledBorder( "Mapping curve" ) );
		column.add( hugContents( panelMappingCurve ), BorderLayout.CENTER );
		return column;
	}

	/** Help/Edit Curve/status on the left, Cancel/Apply on the right. */
	private JPanel createBottomBar()
	{
		final JButton buttonHelp = new JButton( "Help" );
		buttonHelp.setFocusable( false );
		buttonHelp.addActionListener( e -> showHelp() );

		final JToggleButton toggleEditCurve = new JToggleButton( "Edit Curve" );
		toggleEditCurve.setFocusable( false );
		toggleEditCurve.addActionListener( e ->
		{
			final boolean editMode = toggleEditCurve.isSelected();
			panelMappingCurve.setEditMode( editMode );
			labelStatus.setText( editMode ? "Edit mode activated." : "" );
		} );

		final JPanel panelLeftBottom = new JPanel( new FlowLayout( FlowLayout.LEFT, 8, 0 ) );
		panelLeftBottom.add( buttonHelp );
		panelLeftBottom.add( toggleEditCurve );
		panelLeftBottom.add( labelStatus );

		final JButton buttonCancel = new JButton( "Cancel" );
		buttonCancel.addActionListener( e ->
		{
			onSourceChanged();
			setVisible( false );
		} );
		final JButton buttonApply = new JButton( "Apply" );
		buttonApply.addActionListener( e -> applyCurrent() );
		normalizeButtonSizes( buttonCancel, buttonApply );

		final JPanel panelRightBottom = new JPanel( new GridLayout( 1, 2, 8, 0 ) );
		panelRightBottom.add( buttonCancel );
		panelRightBottom.add( buttonApply );

		final JPanel panelBottom = new JPanel( new BorderLayout() );
		panelBottom.add( panelLeftBottom, BorderLayout.WEST );
		panelBottom.add( panelRightBottom, BorderLayout.EAST );
		return panelBottom;
	}

	/**
	 * Wire up the persistent controls (the bottom bar's buttons wire
	 * themselves, in {@link #createBottomBar()}, since nothing else refers to
	 * them).
	 */
	private void installControlListeners()
	{
		comboSource.addActionListener( e -> onSourceChanged() );

		comboPalette.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			final Object selected = comboPalette.getSelectedItem();
			if ( !( selected instanceof String ) )
				return;
			final String name = ( String ) selected;
			final ColorTable ct = LutPalettes.load( name );
			if ( ct == null )
			{
				labelStatus.setText( "Failed to load LUT: " + name );
				return;
			}
			currentPalette = ct;
			panelPaletteSwatch.update( ct );
			panelMappingCurve.setPalette( ct );
			labelStatus.setText( "" );

			// Value matching always follows the palette file's own declared
			// mode, not a user choice: a palette that declares itself
			// non-interpolated (e.g. a qualitative/categorical palette like
			// tab10) is meant to be used as discrete colors, not blended --
			// Truncate is the closest match to how such palettes are
			// typically read (each raw value holds the color of the control
			// point at or before it).
			mappingModel.setValueMatching( ColorTableLut.isInterpolated( ct ) ? ValueMatching.INTERPOLATE : ValueMatching.TRUNCATE );
		} );

		final ActionListener listenerRangeMode = e ->
		{
			if ( loadingControls )
				return;
			mappingModel.setRangeMode( radioFit.isSelected() ? RangeMode.FIT : RangeMode.CYCLIC );
		};
		radioFit.addActionListener( listenerRangeMode );
		radioCyclic.addActionListener( listenerRangeMode );

		checkTreatMinAsBackground.addActionListener( e ->
		{
			buttonBackgroundColor.setEnabled( checkTreatMinAsBackground.isSelected() );
			if ( loadingControls )
				return;
			mappingModel.setTreatMinAsBackground( checkTreatMinAsBackground.isSelected() );
		} );

		buttonBackgroundColor.addActionListener( e ->
		{
			final Color chosen = JColorChooser.showDialog( this, "Background Color", buttonBackgroundColor.getBackground() );
			if ( chosen == null )
				return;
			final int argb = 0xff000000 | ( chosen.getRGB() & 0xffffff );
			buttonBackgroundColor.setBackground( new Color( argb, false ) );
			mappingModel.setBackgroundColor( argb );
		} );

		comboMappingPreset.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.applyPreset( ( MappingPreset ) comboMappingPreset.getSelectedItem() );
		} );

		buttonInvertCurve.addActionListener( e -> mappingModel.invertCurve() );

		getRootPane().registerKeyboardAction( e -> showHelp(), KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );

		mappingModel.addChangeListener( panelMappingCurve::repaint );
		mappingModel.addChangeListener( panelPaletteSwatch::repaint );
	}

	/**
	 * Size the window to its contents, with the graph widened to line up with
	 * the left column's titled panels.
	 */
	private void packAndMatchGraphWidth( final JPanel panelLeftColumn, final JPanel panelMappingCurveColumn )
	{
		// A first pack() is needed before we can trust any preferred-size
		// measurements below: JComboBox (and text components generally)
		// under-measure their preferred width until the component hierarchy
		// is actually realized (addNotify()) and real font metrics become
		// available, so measuring panelLeftColumn's width before this point can
		// be significantly too narrow.
		pack();

		// Match the left column's actual rendered width (not just the Data
		// panel's own preferred width: BoxLayout stretches it to the column's
		// width, which is the widest of Data/Mapping), accounting for the
		// curve column's own titled border insets so the two line up exactly.
		final Insets insets = panelMappingCurveColumn.getBorder().getBorderInsets( panelMappingCurveColumn );
		final int targetGraphWidth = panelLeftColumn.getWidth() - insets.left - insets.right;
		panelMappingCurve.setPreferredSize( new Dimension( targetGraphWidth, panelMappingCurve.getPreferredSize().height ) );

		// Second pack() applies the corrected graph width to the final layout.
		pack();
		setMinimumSize( getPreferredSize() );
	}

	/**
	 * Wrap {@code component} so it renders at its own preferred size instead
	 * of being stretched to fill whatever slot it lands in (e.g. a
	 * {@link BorderLayout#CENTER}), which is what makes titled borders hug
	 * their contents rather than the available space.
	 */
	private static JPanel hugContents( final JComponent component )
	{
		final JPanel wrapper = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		wrapper.add( component );
		return wrapper;
	}

	/**
	 * Load the currently applied palette and mapping (if any) of the selected
	 * source/setup into the editor, discarding any unapplied local edits.
	 */
	private void onSourceChanged()
	{
		final int idx = comboSource.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
		{
			labelStatus.setText( "no setup selected" );
			return;
		}
		final SourceAndConverter< ? > soc = sources.get( idx );
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		final Object conv = soc.getConverter();
		if ( !( conv instanceof RealLUTConverter ) )
		{
			labelStatus.setText( "Converter does not use a LUT." );
			return;
		}
		labelStatus.setText( "" );

		final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
		currentPalette = lutConv.getLUT() != null ? lutConv.getLUT() : ColorTableLut.DEFAULT;
		panelPaletteSwatch.update( currentPalette );

		final MappingModel existing = lutConv.getMapping();
		if ( existing != null )
			mappingModel.copyFrom( existing );
		else
		{
			mappingModel.setRangeMode( RangeMode.FIT );
			mappingModel.setValueMatching( ValueMatching.INTERPOLATE );
			mappingModel.applyPreset( MappingPreset.LINEAR );
		}

		editedRangeMin = setup != null ? setup.getDisplayRangeMin() : 0;
		editedRangeMax = setup != null ? setup.getDisplayRangeMax() : 255;
		panelMappingCurve.setRange( editedRangeMin, editedRangeMax );
		panelMappingCurve.setPalette( currentPalette );
		updateTreatMinAsBackgroundText();

		loadingControls = true;
		try
		{
			radioFit.setSelected( mappingModel.getRangeMode() == RangeMode.FIT );
			radioCyclic.setSelected( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			checkTreatMinAsBackground.setSelected( mappingModel.isTreatMinAsBackground() );
			buttonBackgroundColor.setBackground( new Color( mappingModel.getBackgroundColor(), false ) );
			buttonBackgroundColor.setEnabled( mappingModel.isTreatMinAsBackground() );
			comboMappingPreset.setSelectedItem( mappingModel.getPreset() );
		}
		finally
		{
			loadingControls = false;
		}
	}

	/**
	 * Commit the currently edited palette and mapping to the selected setup's
	 * converter.
	 */
	private void applyCurrent()
	{
		final int idx = comboSource.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
			return;
		final SourceAndConverter< ? > soc = sources.get( idx );
		final Object conv = soc.getConverter();
		if ( !( conv instanceof RealLUTConverter ) )
			return;

		final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
		lutConv.setLUT( currentPalette );

		final MappingModel committed = lutConv.getMapping() != null ? lutConv.getMapping() : new MappingModel();
		committed.copyFrom( mappingModel );
		lutConv.setMapping( committed );

		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		if ( setup != null )
			setup.setDisplayRange( editedRangeMin, editedRangeMax );

		labelStatus.setText( "Applied." );
		if ( repaintAction != null )
			repaintAction.run();
	}

	private void rebuildList()
	{
		comboSource.removeAllItems();
		sources.clear();
		final List< SourceAndConverter< ? > > stateSources = viewerState.getSources();
		for ( final SourceAndConverter< ? > soc : stateSources )
		{
			final ConverterSetup setup = converterSetups.getConverterSetup( soc );
			if ( setup == null )
				continue;
			sources.add( soc );
			final String name = soc.getSpimSource() != null ? soc.getSpimSource().getName() : Integer.toString( setup.getSetupId() );
			comboSource.addItem( "[" + setup.getSetupId() + "] " + name );
		}
		if ( comboSource.getItemCount() > 0 )
			comboSource.setSelectedIndex( 0 );
		onSourceChanged();
	}

	/**
	 * Keep the checkbox's own label showing the actual min value it would
	 * reserve for the background color (e.g. "Treat 5 as Bg"), since "min"
	 * on its own doesn't say what's actually being treated as background.
	 */
	private void updateTreatMinAsBackgroundText()
	{
		checkTreatMinAsBackground.setText( "Treat " + formatValue( editedRangeMin ) + " as Bg" );
	}

	/**
	 * Format a range value the same way {@link MappingCurvePanel} formats
	 * its min/max fields: as a plain integer when it is (numerically) one,
	 * otherwise to 2 decimal places.
	 */
	private static String formatValue( final double value )
	{
		if ( Math.abs( value - Math.round( value ) ) < 1e-6 )
			return Long.toString( Math.round( value ) );
		return String.format( "%.2f", value );
	}

	private static JPanel labeledRow( final String label, final JComponent component )
	{
		return labeledRow( label, component, null );
	}

	private static JPanel labeledRow( final String label, final JComponent component, final JComponent trailing )
	{
		final JPanel row = new JPanel( new BorderLayout( 8, 0 ) );
		row.add( new JLabel( label ), BorderLayout.WEST );
		row.add( component, BorderLayout.CENTER );
		if ( trailing != null )
			row.add( trailing, BorderLayout.EAST );

		row.setMaximumSize( new Dimension( Integer.MAX_VALUE, row.getPreferredSize().height ) );
		row.setAlignmentX( Component.LEFT_ALIGNMENT );

		return row;
	}

	private static void normalizeButtonSizes( final JComponent... components )
	{
		int maxWidth = 0;
		int maxHeight = 0;
		for ( final JComponent component : components )
		{
			final Dimension preferredSize = component.getPreferredSize();
			maxWidth = Math.max( maxWidth, preferredSize.width );
			maxHeight = Math.max( maxHeight, preferredSize.height );
		}
		final Dimension fixedSize = new Dimension( maxWidth, maxHeight );
		for ( final JComponent component : components )
		{
			component.setPreferredSize( fixedSize );
			component.setMinimumSize( fixedSize );
		}
	}

	private void showHelp()
	{
		final String message = String.join( "\n",
				"LUT Editor help:",
				"",
				"Data:",
				"- Source selects which setup you are editing.",
				"- Color palette selects the LUT colors the mapped value is looked up in.",
				"",
				"Mapping:",
				"- How a mapped value selects a palette color (blended smoothly, or",
				"  held to the previous palette color) follows the chosen palette's own",
				"  file: it is not a separate setting here.",
				"- Range mode controls how input values are handled:",
				"  Fit clamps values to [min, max]. Cyclic ignores max and instead cycles",
				"  values through the palette's actual number of colors (shown on the",
				"  graph's y axis), anchored at min -- e.g. with a 10-color palette",
				"  and min=5, value 5 gets the palette's first color, value 15 gets the",
				"  same color again, and so on.",
				"- \"Treat {min} as Bg\" (its label shows the actual min value) forces raw",
				"  values at or below min to always map to a dedicated background color,",
				"  instead of the palette. In Cyclic mode this also reserves the range's",
				"  min value itself so it stops competing with the cycled colors, and the",
				"  cutoff extends slightly above min (up to but not including min + 1) so",
				"  continuous data right next to it doesn't leak through as the palette's",
				"  last color. Click the swatch next to the checkbox to choose the",
				"  background color (defaults to black); it is not one of the palette's",
				"  own colors.",
				"- Mapping preset replaces the curve with a predefined shape (Linear, Percentile",
				"  Stretch, Log, Exp, Sigmoid, α-Sigmoid, Tan, Atan). The curve can still be",
				"  adjusted afterwards.",
				"- Invert flips the current curve vertically (e.g. increasing becomes",
				"  decreasing), on top of whatever shape/edits it already has.",
				"",
				"Mapping curve:",
				"- The color bar to the left previews the palette itself; the one below the",
				"  graph (\"after transform\") previews the color actually produced for each",
				"  input value.",
				"- The boxes at the left/right ends of the x axis set the input value range.",
				"- Click \"Edit Curve\" to show and edit the curve's control points:",
				"  left-click to add or drag a point, right-click a point to remove it.",
				"",
				"- Apply commits the palette and mapping to the selected setup.",
				"- Cancel discards unapplied edits.",
				"",
				"Shortcut:",
				"- Press F1 anywhere in this dialog to open this help." );

		JOptionPane.showMessageDialog( this, message, "LUT Editor Help", JOptionPane.INFORMATION_MESSAGE );
	}

	/**
	 * A non-selectable row in {@link #comboPalette}, labeling the group of
	 * palette names that follow it (see {@link LutCategories}).
	 */
	private static final class CategoryHeader
	{
		private final String label;

		CategoryHeader( final String label )
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/**
	 * A combo box model that refuses to ever make a {@link CategoryHeader}
	 * the actual selected item -- clicking one, or landing on one via the
	 * keyboard and pressing enter, leaves the previous selection in place.
	 * The header rows still show up in the dropdown list itself, just not
	 * as something that can be "chosen".
	 */
	private static final class PaletteComboModel extends DefaultComboBoxModel< Object >
	{
		@Override
		public void setSelectedItem( final Object item )
		{
			if ( item instanceof CategoryHeader )
				return;
			super.setSelectedItem( item );
		}

		private static final long serialVersionUID = 1L;
	}

	/**
	 * A preview panel showing a color table as a horizontal gradient bar,
	 * honoring the current {@link ValueMatching} (e.g. a Truncate palette
	 * shows discrete color bands instead of a smooth blend).
	 */
	private static class GradientPreviewPanel extends JPanel
	{
		private final MappingModel model;

		private ColorTable colorTable;

		public GradientPreviewPanel( final MappingModel model )
		{
			this.model = model;
			setPreferredSize( new Dimension( 300, 16 ) );
			this.colorTable = ColorTableLut.DEFAULT;
		}

		public void update( final ColorTable ct )
		{
			this.colorTable = ct;
			repaint();
		}

		@Override
		protected void paintComponent( final Graphics g )
		{
			super.paintComponent( g );

			final int w = getWidth();
			final int h = getHeight();

			if ( colorTable != null )
			{
				final ValueMatching matching = model.getValueMatching();
				if ( matching == ValueMatching.TRUNCATE )
				{
					final double lastIntervalSize = ColorTableLut.mirroredLastIntervalSize( ColorTableLut.colorPositions( colorTable ) );
					for ( int i = 0; i < w; i++ )
					{
						final int argb = ColorTableLut.lookupARGBQualitative( colorTable, i / ( double ) w, lastIntervalSize );
						g.setColor( new Color( argb ) );
						g.fillRect( i, 0, 1, h );
					}
				}
				else
				{
					for ( int i = 0; i < w; i++ )
					{
						final int idx = ( int ) ( i * 255.0 / w );
						final int argb = ColorTableLut.lookupARGB( colorTable, 0, 255, idx, matching );
						g.setColor( new Color( argb ) );
						g.fillRect( i, 0, 1, h );
					}
				}
			}

			g.setColor( Color.BLACK );
			g.drawRect( 0, 0, w - 1, h - 1 );
		}

		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;
}
