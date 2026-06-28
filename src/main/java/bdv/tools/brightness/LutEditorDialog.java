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
import java.awt.event.ActionEvent;
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
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import net.imglib2.display.ColorTable8;

/**
 * A LUT editor dialog that allows applying predefined LUTs or interactively editing curves
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
	private final CurveEditorPanel curveEditor;
	private final GradientPreviewPanel gradientPreview;
	private final RangeRemapModel rangeRemapModel;
	private final RangeRemapPanel rangeRemapPanel;

	public LutEditorDialog( final Frame owner, final ConverterSetups converterSetups, final ViewerState viewerState, final Runnable repaintAction )
	{
		super( owner, "LUT Editor", false );
		this.converterSetups = converterSetups;
		this.viewerState = viewerState;
		this.repaintAction = repaintAction;

		setLayout( new BorderLayout() );
		( ( JPanel ) getContentPane() ).setBorder( new EmptyBorder( 12, 12, 12, 12 ) );

		// Top panel: setup selector
		final JPanel top = new JPanel( new BorderLayout( 4, 0 ) );
		top.setBorder( BorderFactory.createEmptyBorder( 4, 4, 4, 4 ) );
		combo = new JComboBox<>();
		top.add( combo, BorderLayout.CENTER );
		final JButton btnHelp = new JButton( "Help" );
		btnHelp.setFocusable( false );
		top.add( btnHelp, BorderLayout.EAST );
		add( top, BorderLayout.NORTH );

		// Main content panel
		final JPanel mainPanel = new JPanel();
		mainPanel.setLayout( new BoxLayout( mainPanel, BoxLayout.PAGE_AXIS ) );
		mainPanel.setBorder( BorderFactory.createEmptyBorder( 4, 4, 4, 4 ) );

		// Presets selector
		final JPanel presetPanel = new JPanel( new BorderLayout( 4, 0 ) );
		// presetPanel.setBorder( BorderFactory.createTitledBorder( BorderFactory.createEmptyBorder(), "Presets" ) );
		presetCombo = new JComboBox<>();
		discoverLuts();
		for ( final String name : lutNames )
			presetCombo.addItem( name );

		// Show Select Preset if no preset is selected
		presetCombo.setRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
			                                              int index, boolean isSelected,
			                                              boolean cellHasFocus) {

				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

				if (index == -1 && value == null) {
					setText("Select Preset");
				}

				return this;
			}
		});
		presetCombo.setSelectedIndex(-1);

		presetPanel.add( presetCombo, BorderLayout.CENTER );
		mainPanel.add( presetPanel );
		mainPanel.add( Box.createVerticalStrut( 8 ) );

		// Curve editor canvas
		final JPanel editorPanel = new JPanel();
		editorPanel.setLayout( new BoxLayout( editorPanel, BoxLayout.PAGE_AXIS ) );
		editorPanel.setBorder( BorderFactory.createTitledBorder( "Editor" ) );

		// Channel selector
		final JPanel channelPanel = new JPanel( new BorderLayout( 4, 0 ) );
		channelPanel.add( new JLabel( "Channel:" ), BorderLayout.WEST );
		final JPanel channelButtons = new JPanel( new GridLayout( 1, 4, 4, 0 ) );
		final JToggleButton btnRGB = new JToggleButton( "All" );
		final JToggleButton btnR = new JToggleButton( "Red" );
		final JToggleButton btnG = new JToggleButton( "Green" );
		final JToggleButton btnB = new JToggleButton( "Blue" );
		final ButtonGroup channelGroup = new ButtonGroup();
		channelGroup.add( btnRGB );
		channelGroup.add( btnR );
		channelGroup.add( btnG );
		channelGroup.add( btnB );
		btnRGB.setSelected( true );
		normalizeButtonSizes( btnRGB, btnR, btnG, btnB );
		channelButtons.add( btnRGB );
		channelButtons.add( btnR );
		channelButtons.add( btnG );
		channelButtons.add( btnB );
		channelPanel.add( channelButtons, BorderLayout.CENTER );
		editorPanel.add( channelPanel );
		editorPanel.add( Box.createVerticalStrut( 8 ) );

		curveEditor = new CurveEditorPanel();
		curveEditor.setBorder( BorderFactory.createLineBorder( new Color( 100, 100, 100 ) ) );
		editorPanel.add( curveEditor );

		// Gradient preview
		gradientPreview = new GradientPreviewPanel();
		gradientPreview.setBorder( BorderFactory.createTitledBorder( BorderFactory.createEmptyBorder(), "Preview" ) );
		editorPanel.add( gradientPreview );

		statusLabel = new JLabel( "" );

		// Range remap panel
		rangeRemapModel = new RangeRemapModel();
		rangeRemapPanel = new RangeRemapPanel( rangeRemapModel );
		rangeRemapModel.addChangeListener( () ->
		{
			statusLabel.setText( rangeRemapModel.splitOutOfRange ? "Out of range" : "" );
			gradientPreview.update( curveEditor.generateColorTable() );
		} );
		editorPanel.add( rangeRemapPanel );

		mainPanel.add( editorPanel );
		mainPanel.add( Box.createVerticalStrut( 8 ) );

		// Apply and reset buttons
		final JPanel bottomPanel = new JPanel( new BorderLayout( 4, 0 ) );
		final JPanel statusPanel = new JPanel( new BorderLayout( 4, 0 ) );
		statusLabel.setPreferredSize( new Dimension( 150, 20 ) );
		statusPanel.add( statusLabel );

		final JPanel btnPanel = new JPanel( new GridLayout( 1, 2, 4, 0 ) );
		final JButton btnApply = new JButton( "Apply" );
		final JButton btnReset = new JButton( "Reset Curves" );
		normalizeButtonSizes( btnApply, btnReset );
		btnPanel.add( btnApply );
		btnPanel.add( btnReset );

		bottomPanel.add( statusPanel, BorderLayout.WEST );
		bottomPanel.add( btnPanel, BorderLayout.EAST );
		mainPanel.add( bottomPanel );

		add( mainPanel, BorderLayout.CENTER );

		// Event listeners
		presetCombo.addActionListener( e ->
		{
			final int pi = presetCombo.getSelectedIndex();
			if ( pi >= 0 && pi < lutNames.size() )
				applyPreset( lutNames.get( pi ) );
		} );

		btnRGB.addActionListener( e ->
		{
			curveEditor.setChannel( CurveEditorPanel.Channel.RGB );
			curveEditor.repaint();
		} );
		btnR.addActionListener( e ->
		{
			curveEditor.setChannel( CurveEditorPanel.Channel.R );
			curveEditor.repaint();
		} );
		btnG.addActionListener( e ->
		{
			curveEditor.setChannel( CurveEditorPanel.Channel.G );
			curveEditor.repaint();
		} );
		btnB.addActionListener( e ->
		{
			curveEditor.setChannel( CurveEditorPanel.Channel.B );
			curveEditor.repaint();
		} );

		btnApply.addActionListener( e -> applyEditedCurve() );
		btnReset.addActionListener( e ->
		{
			curveEditor.resetCurves();
			gradientPreview.update( curveEditor.generateColorTable() );
		} );
		btnHelp.addActionListener( e -> showHelp() );
		getRootPane().registerKeyboardAction( e -> showHelp(), KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );

		// Update gradient preview when curves change
		curveEditor.addChangeListener( () ->
				gradientPreview.update( curveEditor.generateColorTable() ) );

		combo.addActionListener( ( final ActionEvent e ) -> updateStatus() );

		rebuildList();
		gradientPreview.update( curveEditor.generateColorTable() );

		pack();
		setMinimumSize( new Dimension( 500, 500 ) );
	}

	private void applyPreset( final String lutName )
	{
		final int idx = combo.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
			return;
		final SourceAndConverter< ? > soc = sources.get( idx );
		final Object conv = soc.getConverter();
		if ( conv instanceof RealLUTConverter )
		{
			final ColorTable8 ct = loadLutResource( lutName );
			if ( ct == null )
			{
				statusLabel.setText( "Failed to load LUT: " + lutName );
				return;
			}
			final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
			lutConv.setLUT( ct );
			lutConv.setRangeRemap( rangeRemapModel );
			curveEditor.loadColorTable( ct );
			statusLabel.setText( "Applied " + lutName + " LUT." );
			if ( repaintAction != null )
				repaintAction.run();
		}
	}

	private void applyEditedCurve()
	{
		final int idx = combo.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
			return;
		final SourceAndConverter< ? > soc = sources.get( idx );
		final Object conv = soc.getConverter();
		if ( conv instanceof RealLUTConverter )
		{
			final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
			final ColorTable8 ct = curveEditor.generateColorTable();
			lutConv.setLUT( ct );
			lutConv.setRangeRemap( rangeRemapModel );
			statusLabel.setText( "Applied custom curve." );
			if ( repaintAction != null )
				repaintAction.run();
		}
	}

	private void rebuildList()
	{
		combo.removeAllItems();
		sources.clear();
		final List< SourceAndConverter< ? > > stateSources = viewerState.getSources();
		for ( final SourceAndConverter< ? > soc : stateSources )
		{
			final bdv.tools.brightness.ConverterSetup setup = converterSetups.getConverterSetup( soc );
			if ( setup == null )
				continue;
			sources.add( soc );
			final String name = soc.getSpimSource() != null ? soc.getSpimSource().getName() : Integer.toString( setup.getSetupId() );
			combo.addItem( "[" + setup.getSetupId() + "] " + name );
		}
		if ( combo.getItemCount() > 0 )
			combo.setSelectedIndex( 0 );
		updateStatus();
	}

	private void updateStatus()
	{
		final int idx = combo.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
		{
			statusLabel.setText( "no setup selected" );
			return;
		}
		final Object conv = sources.get( idx ).getConverter();
		if ( conv instanceof RealLUTConverter )
			statusLabel.setText( "" );
		else
			statusLabel.setText( "Converter does not use a LUT." );
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
				"Presets:",
				"- Select a LUT from the dropdown to apply it to the selected setup.",
				"",
				"Curve editor:",
				"- Choose All / Red / Green / Blue to edit channels.",
				"- Left-click to add or select a control point.",
				"- Drag a point vertically to adjust the curve.",
				"- Right-click a point to remove it.",
				"- Apply commits the current curve to the selected setup.",
				"- Reset Curves restores the default linear LUT.",
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
	 * Load a LUT from a resource text file. Each line has 3 space-separated R G B values (0-255).
	 */
	private static ColorTable8 loadLutResource( final String lutName )
	{
		final String path = LUT_RESOURCE_DIR + "/" + lutName + ".txt";
		try ( final InputStream is = LutEditorDialog.class.getClassLoader().getResourceAsStream( path ) )
		{
			if ( is == null )
				return null;
			final BufferedReader reader = new BufferedReader( new InputStreamReader( is ) );
			final byte[] r = new byte[ 256 ];
			final byte[] g = new byte[ 256 ];
			final byte[] b = new byte[ 256 ];
			int idx = 0;
			String line;
			while ( ( line = reader.readLine() ) != null && idx < 256 )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue;
				final String[] parts = line.split( "\\s+" );
				if ( parts.length < 3 )
					continue;
				r[ idx ] = ( byte ) Integer.parseInt( parts[ 0 ] );
				g[ idx ] = ( byte ) Integer.parseInt( parts[ 1 ] );
				b[ idx ] = ( byte ) Integer.parseInt( parts[ 2 ] );
				idx++;
			}
			if ( idx < 256 )
				return null;
			return new ColorTable8( r, g, b );
		} catch ( final IOException | NumberFormatException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * A preview panel showing the current color gradient as a horizontal bar.
	 */
	private static class GradientPreviewPanel extends JPanel
	{
		private ColorTable8 colorTable;

		public GradientPreviewPanel()
		{
			setPreferredSize( new Dimension( 300, 40 ) );
			this.colorTable = new ColorTable8();
		}

		public void update( final ColorTable8 ct )
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

			// Border
			g.setColor( Color.BLACK );
			g.drawRect( 0, 0, w - 1, h - 1 );
		}

		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;
}
