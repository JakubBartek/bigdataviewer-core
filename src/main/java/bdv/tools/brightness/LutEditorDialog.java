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

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import net.imglib2.display.ColorTable;
import net.imglib2.display.ColorTable8;

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

	private final JComboBox< String > combo;
	private final JComboBox< String > presetCombo;
	private final List< String > lutNames = new ArrayList<>();
	private final JLabel statusLabel;

	private final GradientPreviewPanel paletteSwatch;
	private final MappingCurvePanel mappingCurvePanel;

	private final JComboBox< ValueMatching > valueMatchingCombo;
	private final JRadioButton rbFit;
	private final JRadioButton rbCyclic;
	private final JCheckBox chkTreatMinAsBackground;
	private final JButton backgroundColorButton;
	private final JComboBox< MappingPreset > mappingPresetCombo;

	/** The palette and mapping currently being edited (not yet applied until "Apply" is pressed). */
	private ColorTable currentPalette = new ColorTable8();
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

		setLayout( new BorderLayout() );
		( ( JPanel ) getContentPane() ).setBorder( new EmptyBorder( 12, 12, 12, 12 ) );

		// -- Data panel: source + color palette -----------------------------
		combo = new JComboBox<>();

		presetCombo = new JComboBox<>();
		discoverLuts();
		for ( final String name : lutNames )
			presetCombo.addItem( name );
		presetCombo.setRenderer( new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent( final JList< ? > list, final Object value,
					final int index, final boolean isSelected, final boolean cellHasFocus )
			{
				super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
				if ( index == -1 && value == null )
					setText( "Select Preset" );
				return this;
			}
		} );
		presetCombo.setSelectedIndex( -1 );

		paletteSwatch = new GradientPreviewPanel();
		paletteSwatch.setPreferredSize( new Dimension( 200, 16 ) );
		paletteSwatch.setMaximumSize( new Dimension( Integer.MAX_VALUE, 16 ) );

		final JPanel dataPanel = new JPanel();
		dataPanel.setLayout( new BoxLayout( dataPanel, BoxLayout.PAGE_AXIS ) );
		dataPanel.setBorder( BorderFactory.createTitledBorder( "Data" ) );
		dataPanel.add( labeledRow( "Source:", combo ) );
		dataPanel.add( Box.createVerticalStrut( 8 ) );
		dataPanel.add( labeledRow( "Color palette:", presetCombo ) );
		dataPanel.add( Box.createVerticalStrut( 4 ) );
		dataPanel.add( paletteSwatch );

		dataPanel.setAlignmentX( Component.LEFT_ALIGNMENT );
		dataPanel.setMaximumSize( new Dimension( Integer.MAX_VALUE, dataPanel.getPreferredSize().height ) );

		// -- Mapping panel: value matching, range mode, preset --------------
		valueMatchingCombo = new JComboBox<>( ValueMatching.values() );
		rbFit = new JRadioButton( "Fit" );
		rbCyclic = new JRadioButton( "Cyclic" );
		final ButtonGroup rangeModeGroup = new ButtonGroup();
		rangeModeGroup.add( rbFit );
		rangeModeGroup.add( rbCyclic );
		rbFit.setSelected( true );
		chkTreatMinAsBackground = new JCheckBox( "Treat min as Bg" );
		chkTreatMinAsBackground.setVisible( false );
		backgroundColorButton = new JButton();
		backgroundColorButton.setToolTipText( "Background color" );
		backgroundColorButton.setPreferredSize( new Dimension( 20, 20 ) );
		backgroundColorButton.setMinimumSize( new Dimension( 20, 20 ) );
		backgroundColorButton.setMaximumSize( new Dimension( 20, 20 ) );
		backgroundColorButton.setBackground( new Color( 0xff000000, false ) );
		backgroundColorButton.setVisible( false );
		backgroundColorButton.setEnabled( false );
		mappingPresetCombo = new JComboBox<>( MappingPreset.values() );

		final JPanel mappingPanel = new JPanel();
		mappingPanel.setLayout( new BoxLayout( mappingPanel, BoxLayout.PAGE_AXIS ) );
		mappingPanel.setBorder( BorderFactory.createTitledBorder( "Mapping" ) );
		mappingPanel.add( labeledRow( "Value matching:", valueMatchingCombo ) );
		final JPanel rangeModeRow = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		rangeModeRow.add( new JLabel( "Range mode:" ) );
		rangeModeRow.add( Box.createHorizontalStrut( 8 ) );
		rangeModeRow.add( rbFit );
		rangeModeRow.add( Box.createHorizontalStrut( 4 ) );
		rangeModeRow.add( rbCyclic );
		rangeModeRow.add( Box.createHorizontalStrut( 8 ) );
		rangeModeRow.add( chkTreatMinAsBackground );
		rangeModeRow.add( Box.createHorizontalStrut( 4 ) );
		rangeModeRow.add( backgroundColorButton );
		rangeModeRow.setAlignmentX( Component.LEFT_ALIGNMENT );
		mappingPanel.add( Box.createVerticalStrut( 4 ) );
		mappingPanel.add( rangeModeRow );
		mappingPanel.add( Box.createVerticalStrut( 4 ) );
		mappingPanel.add( labeledRow( "Mapping preset:", mappingPresetCombo ) );
		mappingPanel.setAlignmentX( Component.LEFT_ALIGNMENT );
		mappingPanel.setMaximumSize( new Dimension( Integer.MAX_VALUE, mappingPanel.getPreferredSize().height ) );

		final JPanel leftColumn = new JPanel();
		leftColumn.setLayout( new BoxLayout( leftColumn, BoxLayout.PAGE_AXIS ) );
		leftColumn.add( dataPanel );
		leftColumn.add( mappingPanel );

		// -- Mapping curve panel ---------------------------------------------
		mappingCurvePanel = new MappingCurvePanel( mappingModel );
		mappingCurvePanel.setRangeChangeListener( ( min, max ) ->
		{
			editedRangeMin = min;
			editedRangeMax = max;
		} );
		// Wrap in a non-stretching FlowLayout so the panel renders at its own
		// preferred size instead of being stretched to fill BorderLayout.CENTER.
		final JPanel graphWrapper = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		graphWrapper.add( mappingCurvePanel );

		final JPanel rightColumn = new JPanel( new BorderLayout() );
		rightColumn.setBorder( BorderFactory.createTitledBorder( "Mapping curve" ) );
		rightColumn.add( graphWrapper, BorderLayout.CENTER );

		// Likewise, wrap rightColumn itself so its titled border hugs the graph
		// tightly instead of stretching to fill centerPanel's CENTER slot.
		final JPanel rightColumnWrapper = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		rightColumnWrapper.add( rightColumn );

		final JPanel centerPanel = new JPanel( new BorderLayout( 12, 0 ) );
		centerPanel.add( leftColumn, BorderLayout.WEST );
		centerPanel.add( rightColumnWrapper, BorderLayout.CENTER );
		add( centerPanel, BorderLayout.CENTER );

		// -- Bottom bar: status/reset on the left, cancel/apply on the right -
		statusLabel = new JLabel( "" );

		final JPanel bottomPanel = new JPanel( new BorderLayout() );
		final JPanel leftBottom = new JPanel( new FlowLayout( FlowLayout.LEFT, 8, 0 ) );
		final JButton btnHelp = new JButton( "Help" );
		btnHelp.setFocusable( false );
		leftBottom.add( btnHelp );
		final JToggleButton btnEditCurve = new JToggleButton( "Edit Curve" );
		btnEditCurve.setFocusable( false );
		leftBottom.add( btnEditCurve );
		leftBottom.add( statusLabel );
		bottomPanel.add( leftBottom, BorderLayout.WEST );

		final JButton btnCancel = new JButton( "Cancel" );
		final JButton btnApply = new JButton( "Apply" );
		normalizeButtonSizes( btnCancel, btnApply );
		final JPanel rightBottom = new JPanel( new GridLayout( 1, 2, 8, 0 ) );
		rightBottom.add( btnCancel );
		rightBottom.add( btnApply );
		bottomPanel.add( rightBottom, BorderLayout.EAST );

		add( bottomPanel, BorderLayout.SOUTH );

		// -- Event listeners --------------------------------------------------
		combo.addActionListener( e -> onSourceChanged() );

		presetCombo.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			final int pi = presetCombo.getSelectedIndex();
			if ( pi < 0 || pi >= lutNames.size() )
				return;
			final ColorTable ct = loadLutResource( lutNames.get( pi ) );
			if ( ct == null )
			{
				statusLabel.setText( "Failed to load LUT: " + lutNames.get( pi ) );
				return;
			}
			currentPalette = ct;
			paletteSwatch.update( ct );
			mappingCurvePanel.setPalette( ct );
			statusLabel.setText( "" );
		} );

		valueMatchingCombo.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.setValueMatching( ( ValueMatching ) valueMatchingCombo.getSelectedItem() );
		} );

		final ActionListener rangeModeListener = e ->
		{
			setTreatMinAsBackgroundVisible( rbCyclic.isSelected() );
			if ( loadingControls )
				return;
			mappingModel.setRangeMode( rbFit.isSelected() ? RangeMode.FIT : RangeMode.CYCLIC );
		};
		rbFit.addActionListener( rangeModeListener );
		rbCyclic.addActionListener( rangeModeListener );

		chkTreatMinAsBackground.addActionListener( e ->
		{
			backgroundColorButton.setEnabled( chkTreatMinAsBackground.isSelected() );
			if ( loadingControls )
				return;
			mappingModel.setTreatMinAsBackground( chkTreatMinAsBackground.isSelected() );
		} );

		backgroundColorButton.addActionListener( e ->
		{
			final Color chosen = JColorChooser.showDialog( this, "Background Color", backgroundColorButton.getBackground() );
			if ( chosen == null )
				return;
			final int argb = 0xff000000 | ( chosen.getRGB() & 0xffffff );
			backgroundColorButton.setBackground( new Color( argb, false ) );
			mappingModel.setBackgroundColor( argb );
		} );

		mappingPresetCombo.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.applyPreset( ( MappingPreset ) mappingPresetCombo.getSelectedItem() );
		} );

		btnApply.addActionListener( e -> applyCurrent() );
		btnCancel.addActionListener( e ->
		{
			onSourceChanged();
			setVisible( false );
		} );

		btnEditCurve.addActionListener( e -> mappingCurvePanel.setEditMode( btnEditCurve.isSelected() ) );

		btnHelp.addActionListener( e -> showHelp() );
		getRootPane().registerKeyboardAction( e -> showHelp(), KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );

		mappingModel.addChangeListener( mappingCurvePanel::repaint );

		rebuildList();

		// A first pack() is needed before we can trust any preferred-size
		// measurements below: JComboBox (and text components generally)
		// under-measure their preferred width until the component hierarchy
		// is actually realized (addNotify()) and real font metrics become
		// available, so measuring leftColumn's width before this point can
		// be significantly too narrow.
		pack();

		// Match the left column's actual rendered width (not just dataPanel's
		// own preferred width: BoxLayout stretches dataPanel to leftColumn's
		// width, which is the widest of dataPanel/mappingPanel), accounting
		// for rightColumn's own titled border insets so the two titled panels
		// line up exactly.
		final Insets rightInsets = rightColumn.getBorder().getBorderInsets( rightColumn );
		final int targetGraphWidth = leftColumn.getWidth() - rightInsets.left - rightInsets.right;
		mappingCurvePanel.setPreferredSize( new Dimension( targetGraphWidth, mappingCurvePanel.getPreferredSize().height ) );

		// Second pack() applies the corrected graph width to the final layout.
		pack();
		setMinimumSize( getPreferredSize() );
	}

	/**
	 * Load the currently applied palette and mapping (if any) of the selected
	 * source/setup into the editor, discarding any unapplied local edits.
	 */
	private void onSourceChanged()
	{
		final int idx = combo.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
		{
			statusLabel.setText( "no setup selected" );
			return;
		}
		final SourceAndConverter< ? > soc = sources.get( idx );
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		final Object conv = soc.getConverter();
		if ( !( conv instanceof RealLUTConverter ) )
		{
			statusLabel.setText( "Converter does not use a LUT." );
			return;
		}
		statusLabel.setText( "" );

		final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
		currentPalette = lutConv.getLUT() != null ? lutConv.getLUT() : new ColorTable8();
		paletteSwatch.update( currentPalette );

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
		mappingCurvePanel.setRange( editedRangeMin, editedRangeMax );
		mappingCurvePanel.setPalette( currentPalette );

		loadingControls = true;
		try
		{
			valueMatchingCombo.setSelectedItem( mappingModel.getValueMatching() );
			rbFit.setSelected( mappingModel.getRangeMode() == RangeMode.FIT );
			rbCyclic.setSelected( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			chkTreatMinAsBackground.setSelected( mappingModel.isTreatMinAsBackground() );
			backgroundColorButton.setBackground( new Color( mappingModel.getBackgroundColor(), false ) );
			backgroundColorButton.setEnabled( mappingModel.isTreatMinAsBackground() );
			setTreatMinAsBackgroundVisible( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			mappingPresetCombo.setSelectedItem( mappingModel.getPreset() );
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
		final int idx = combo.getSelectedIndex();
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

		statusLabel.setText( "Applied." );
		if ( repaintAction != null )
			repaintAction.run();
	}

	private void rebuildList()
	{
		combo.removeAllItems();
		sources.clear();
		final List< SourceAndConverter< ? > > stateSources = viewerState.getSources();
		for ( final SourceAndConverter< ? > soc : stateSources )
		{
			final ConverterSetup setup = converterSetups.getConverterSetup( soc );
			if ( setup == null )
				continue;
			sources.add( soc );
			final String name = soc.getSpimSource() != null ? soc.getSpimSource().getName() : Integer.toString( setup.getSetupId() );
			combo.addItem( "[" + setup.getSetupId() + "] " + name );
		}
		if ( combo.getItemCount() > 0 )
			combo.setSelectedIndex( 0 );
		onSourceChanged();
	}

	private void setTreatMinAsBackgroundVisible( final boolean visible )
	{
		if ( chkTreatMinAsBackground.isVisible() == visible )
			return;
		chkTreatMinAsBackground.setVisible( visible );
		backgroundColorButton.setVisible( visible );
		chkTreatMinAsBackground.getParent().revalidate();
		chkTreatMinAsBackground.getParent().repaint();
	}

	private static JPanel labeledRow( final String label, final JComponent component )
	{
		final JPanel row = new JPanel( new BorderLayout( 8, 0 ) );
		row.add( new JLabel( label ), BorderLayout.WEST );
		row.add( component, BorderLayout.CENTER );

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
				"- Value matching controls how the curve is evaluated between control points:",
				"  Interpolate (smooth), Round (nearest point), or Truncate (hold previous point).",
				"- Range mode controls how input values are handled:",
				"  Fit clamps values to [min, max]. Cyclic ignores max and instead cycles",
				"  values through the palette's actual number of colors (shown above the",
				"  color bar on the left), anchored at min -- e.g. with a 10-color palette",
				"  and min=5, value 5 gets the palette's first color, value 15 gets the",
				"  same color again, and so on.",
				"- When Cyclic is selected, \"Treat min as Background\" forces the range's min",
				"  value (the left value of the range), and anything below it, to always map",
				"  to a dedicated background color, instead of cycling like other values.",
				"  Values above min still cycle normally. Click the swatch next to the",
				"  checkbox to choose that color (defaults to black). It is not one of the",
				"  palette's cycled colors.",
				"- Mapping preset replaces the curve with a predefined shape (Linear, Percentile",
				"  Stretch, Log, Exp, Sigmoid, α-Sigmoid, Tan, Atan). The curve can still be",
				"  adjusted afterwards.",
				"",
				"Mapping curve:",
				"- Left-click to add or drag a control point.",
				"- Right-click a point to remove it.",
				"- The color bar on the right previews the resulting LUT color across the input range.",
				"- The boxes at the left/right ends of the x axis set the input value range.",
				"",
				"- Apply commits the palette and mapping to the selected setup.",
				"- Cancel discards unapplied edits.",
				"",
				"Shortcut:",
				"- Press F1 anywhere in this dialog to open this help." );

		JOptionPane.showMessageDialog( this, message, "LUT Editor Help", JOptionPane.INFORMATION_MESSAGE );
	}

	// -- LUT loading from resources -----------------------------------------

	private static final String LUT_RESOURCE_DIR = "bdv/ui/luts";

	/**
	 * Discover available LUT names from resource files.
	 */
	private void discoverLuts()
	{
		lutNames.clear();
		try
		{
			final URL dirUrl = LutEditorDialog.class.getClassLoader().getResource( LUT_RESOURCE_DIR );
			if ( dirUrl == null )
				return;
			final URI uri = dirUrl.toURI();
			final TreeMap< String, String > sorted = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
			if ( "jar".equals( uri.getScheme() ) )
			{
				try ( final FileSystem fs = FileSystems.newFileSystem( uri, Collections.emptyMap() );
				      final Stream< Path > paths = Files.walk( fs.getPath( LUT_RESOURCE_DIR ), 1 ) )
				{
					paths.filter( p -> p.toString().endsWith( ".txt" ) )
							.forEach( p ->
							{
								final String fn = p.getFileName().toString();
								sorted.put( fn.substring( 0, fn.length() - 4 ), fn );
							} );
				}
			} else
			{
				try ( final Stream< Path > paths = Files.walk( Paths.get( uri ), 1 ) )
				{
					paths.filter( p -> p.toString().endsWith( ".txt" ) )
							.forEach( p ->
							{
								final String fn = p.getFileName().toString();
								sorted.put( fn.substring( 0, fn.length() - 4 ), fn );
							} );
				}
			}
			lutNames.addAll( sorted.keySet() );
		} catch ( final Exception e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Load a LUT from a resource text file. Each line is a control point with
	 * 5 space-separated values: {@code position red green blue alpha}, all in
	 * [0, 1]. The number of control points is arbitrary (not tied to 256);
	 * colors between control points are obtained by linear interpolation.
	 */
	private static ColorTable loadLutResource( final String lutName )
	{
		final String path = LUT_RESOURCE_DIR + "/" + lutName + ".txt";
		try ( final InputStream is = LutEditorDialog.class.getClassLoader().getResourceAsStream( path ) )
		{
			if ( is == null )
				return null;
			final BufferedReader reader = new BufferedReader( new InputStreamReader( is ) );
			final List< double[] > rows = new ArrayList<>();
			String line;
			while ( ( line = reader.readLine() ) != null )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue;
				final String[] parts = line.split( "\\s+" );
				if ( parts.length < 4 )
					continue;
				final double position = Double.parseDouble( parts[ 0 ] );
				final double r = Double.parseDouble( parts[ 1 ] );
				final double g = Double.parseDouble( parts[ 2 ] );
				final double b = Double.parseDouble( parts[ 3 ] );
				final double a = parts.length >= 5 ? Double.parseDouble( parts[ 4 ] ) : 1.0;
				rows.add( new double[] { position, r, g, b, a } );
			}
			if ( rows.size() < 2 )
				return null;
			rows.sort( Comparator.comparingDouble( row -> row[ 0 ] ) );

			final int n = rows.size();
			final double[] positions = new double[ n ];
			final double[] red = new double[ n ];
			final double[] green = new double[ n ];
			final double[] blue = new double[ n ];
			final double[] alpha = new double[ n ];
			for ( int i = 0; i < n; i++ )
			{
				final double[] row = rows.get( i );
				positions[ i ] = row[ 0 ];
				red[ i ] = row[ 1 ];
				green[ i ] = row[ 2 ];
				blue[ i ] = row[ 3 ];
				alpha[ i ] = row[ 4 ];
			}
			return new LutPalette( positions, red, green, blue, alpha );
		} catch ( final IOException | NumberFormatException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * A preview panel showing a color table as a horizontal gradient bar.
	 */
	private static class GradientPreviewPanel extends JPanel
	{
		private ColorTable colorTable;

		public GradientPreviewPanel()
		{
			setPreferredSize( new Dimension( 300, 16 ) );
			this.colorTable = new ColorTable8();
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
				for ( int i = 0; i < w; i++ )
				{
					final int idx = ( int ) ( i * 255.0 / w );
					final int argb = colorTable.lookupARGB( 0, 255, idx );
					g.setColor( new Color( argb ) );
					g.fillRect( i, 0, 1, h );
				}
			}

			g.setColor( Color.BLACK );
			g.drawRect( 0, 0, w - 1, h - 1 );
		}

		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;
}
