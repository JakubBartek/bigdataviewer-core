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
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
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
		top.add( new JLabel( "Setup:" ), BorderLayout.WEST );
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

		// Presets row
		final JPanel presets = new JPanel( new GridLayout( 1, 3, 4, 0 ) );
		final JButton btnGray = new JButton( "Grayscale" );
		final JButton btnHot = new JButton( "Hot" );
		final JButton btnInvert = new JButton( "Invert" );
		presets.add( btnGray );
		presets.add( btnHot );
		presets.add( btnInvert );
		normalizeButtonSizes( btnGray, btnHot, btnInvert );
		presets.setBorder( BorderFactory.createTitledBorder( "Presets" ) );
		mainPanel.add( presets );
		mainPanel.add( Box.createVerticalStrut( 8 ) );

		// Curve editor canvas
		final JPanel editorPanel = new JPanel();
		editorPanel.setLayout( new BoxLayout( editorPanel, BoxLayout.PAGE_AXIS ) );
		editorPanel.setBorder( BorderFactory.createTitledBorder( "Curve Editor" ) );

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
		btnGray.addActionListener( e ->
		{
			applyPreset( Preset.GRAYSCALE );
		} );
		btnHot.addActionListener( e ->
		{
			applyPreset( Preset.HOT );
		} );
		btnInvert.addActionListener( e ->
		{
			applyPreset( Preset.INVERT );
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

	private enum Preset
	{GRAYSCALE, HOT, INVERT}

	private void applyPreset( final Preset p )
	{
		final int idx = combo.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
			return;
		final SourceAndConverter< ? > soc = sources.get( idx );
		final Object conv = soc.getConverter();
		if ( conv instanceof RealLUTConverter )
		{
			final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
			final ColorTable8 ct;
			switch ( p )
			{
				case GRAYSCALE:
					ct = makeGrayscale();
					break;
				case HOT:
					ct = makeHot();
					break;
				case INVERT:
					ct = makeInvert();
					break;
				default:
					throw new IllegalArgumentException( "Unknown preset: " + p );
			}
			lutConv.setLUT( ct );
			lutConv.setRangeRemap( rangeRemapModel );
			curveEditor.loadColorTable( ct );
			statusLabel.setText( "Applied " + p.name().toLowerCase() + " LUT." );
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
				"- Pick a preset palette to apply it to the selected setup.",
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

	// -- preset color tables ------------------------------------------------
	private static ColorTable8 makeGrayscale()
	{
		final byte[] r = new byte[ 256 ];
		final byte[] g = new byte[ 256 ];
		final byte[] b = new byte[ 256 ];
		for ( int i = 0; i < 256; i++ )
		{
			r[ i ] = ( byte ) i;
			g[ i ] = ( byte ) i;
			b[ i ] = ( byte ) i;
		}
		return new ColorTable8( r, g, b );
	}

	private static ColorTable8 makeInvert()
	{
		final byte[] r = new byte[ 256 ];
		final byte[] g = new byte[ 256 ];
		final byte[] b = new byte[ 256 ];
		for ( int i = 0; i < 256; i++ )
		{
			final int v = 255 - i;
			r[ i ] = ( byte ) v;
			g[ i ] = ( byte ) v;
			b[ i ] = ( byte ) v;
		}
		return new ColorTable8( r, g, b );
	}

	private static ColorTable8 makeHot()
	{
		final byte[] r = new byte[ 256 ];
		final byte[] g = new byte[ 256 ];
		final byte[] b = new byte[ 256 ];
		for ( int i = 0; i < 256; i++ )
		{
			int ri, gi, bi;
			if ( i < 85 )
			{
				ri = Math.min( 255, 3 * i );
				gi = 0;
				bi = 0;
			} else if ( i < 170 )
			{
				ri = 255;
				gi = Math.min( 255, 3 * ( i - 85 ) );
				bi = 0;
			} else
			{
				ri = 255;
				gi = 255;
				bi = Math.min( 255, 3 * ( i - 170 ) );
			}
			r[ i ] = ( byte ) ri;
			g[ i ] = ( byte ) gi;
			b[ i ] = ( byte ) bi;
		}
		return new ColorTable8( r, g, b );
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
