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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import net.imglib2.display.ColorTable;

/**
 * A LUT editor dialog, laid out as three stacked panels:
 * <ul>
 * <li><b>Setting</b>: a saved, reusable combination of everything below
 * except the input value range (see {@link EditorPreset}), which can be
 * applied in one step or saved back under a name of the user's choosing.</li>
 * <li><b>Data</b>: which source/setup is being edited, and which color
 * palette (LUT) is used to render it.</li>
 * <li><b>Mapping</b>: how a raw source value is turned into an index into
 * that palette, via a user-editable curve over the source's display
 * range.</li>
 * </ul>
 * Edits take effect in the viewer immediately (see {@link #pushLiveEdits()});
 * "Apply" moves the revert-to baseline forward, and closing without it
 * restores that baseline (see {@link #setVisible(boolean)}).
 */
public class LutEditorDialog extends JDialog
{
	private final ConverterSetups converterSetups;
	private final ViewerState viewerState;
	private final Runnable repaintAction;

	private final List< SourceAndConverter< ? > > sources = new ArrayList<>();

	private final JComboBox< String > comboSource;
	private final JComboBox< Object > comboPalette;
	private final JComboBox< Object > comboEditorPreset;
	private final JButton buttonSaveEditorPreset;
	private final JLabel labelStatus;

	private final GradientPreviewPanel panelPaletteSwatch;
	private final MappingCurvePanel panelMappingCurve;

	private final JRadioButton radioFit;
	private final JRadioButton radioCyclic;
	private final JLabel labelCyclicPeriod;
	private final JTextField fieldCyclicPeriod;
	private final JCheckBox checkTreatMinAsBackground;
	private final JButton buttonBackgroundColor;
	private final JComboBox< MappingPreset > comboMappingPreset;
	private final JButton buttonInvertCurve;

	/**
	 * The palette and mapping currently being edited. Edits are pushed live
	 * to {@link #activeLutConv} as they happen (see {@link #pushLiveEdits()}),
	 * so they are visible in the viewer immediately, not just after "Apply".
	 */
	private ColorTable currentPalette = ColorTableLut.DEFAULT;

	/** Name of {@link #currentPalette} in {@link #comboPalette}, or {@code null} if it doesn't (or isn't known to) correspond to one -- see {@link #loadIntoEditor}. */
	private String currentPaletteName = null;

	private final MappingModel mappingModel = new MappingModel();

	/** The input value range currently being edited; see {@link #currentPalette}. */
	private double editedRangeMin = 0;
	private double editedRangeMax = 255;

	/** The setup/converter {@link #currentPalette} etc. are being live-pushed to; {@code null} if none is currently editable. */
	private RealLUTConverter< ? > activeLutConv = null;
	private ConverterSetup activeSetup = null;

	/**
	 * Snapshot of {@link #activeLutConv}'s state as of the last "Apply" (or,
	 * absent that, as loaded by {@link #onSourceChanged()}) -- what
	 * {@link #revertLiveEdits()} restores unapplied live edits back to.
	 */
	private ColorTable baselinePalette = ColorTableLut.DEFAULT;
	private String baselinePaletteName = null;
	private final MappingModel baselineMapping = new MappingModel();
	private double baselineRangeMin = 0;
	private double baselineRangeMax = 255;

	/** Guards against control listeners (including the live-push one) firing while we are programmatically syncing them. */
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
		comboEditorPreset = createEditorPresetCombo();
		buttonSaveEditorPreset = new JButton( "Save..." );
		buttonSaveEditorPreset.setFocusable( false );
		panelPaletteSwatch = new GradientPreviewPanel( mappingModel );
		panelPaletteSwatch.setPreferredSize( new Dimension( 200, 16 ) );
		panelPaletteSwatch.setMaximumSize( new Dimension( Integer.MAX_VALUE, 16 ) );

		radioFit = new JRadioButton( "Fit" );
		radioCyclic = new JRadioButton( "Cyclic" );
		radioFit.setSelected( true );
		labelCyclicPeriod = new JLabel( "Period:" );
		labelCyclicPeriod.setEnabled( false );
		fieldCyclicPeriod = new JTextField( 4 );
		fieldCyclicPeriod.setHorizontalAlignment( SwingConstants.CENTER );
		fieldCyclicPeriod.setEnabled( false );
		fieldCyclicPeriod.addActionListener( e -> commitCyclicPeriodField() );
		fieldCyclicPeriod.addFocusListener( new FocusAdapter()
		{
			@Override
			public void focusLost( final FocusEvent e )
			{
				commitCyclicPeriodField();
			}
		} );
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
			pushLiveEdits();
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
	 * Edits are pushed live to the viewer as they are made (see
	 * {@link #pushLiveEdits()}); hiding the dialog without having pressed
	 * "Apply" since the last edit would otherwise leave those edits in place
	 * with no way back. So: whenever this dialog transitions from visible to
	 * hidden -- via "Cancel", the window's own close button, or toggling it
	 * closed with its keyboard shortcut, all of which just call this -- first
	 * confirm with the user if there are unapplied edits to discard, then
	 * revert to the last-applied (or, absent that, originally loaded) state.
	 */
	@Override
	public void setVisible( final boolean visible )
	{
		if ( !visible && isVisible() && isDirty() )
		{
			final int choice = JOptionPane.showConfirmDialog( this,
					"Discard unapplied changes?", "Unapplied Changes",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
			if ( choice != JOptionPane.YES_OPTION )
				return;
		}
		if ( !visible && isVisible() )
			revertLiveEdits();
		super.setVisible( visible );
	}

	/** Whether the editor currently differs from {@link #baselinePalette} etc., i.e. has edits since the last "Apply" (or load) that closing now would discard. */
	private boolean isDirty()
	{
		return !samePaletteAsBaseline()
				|| editedRangeMin != baselineRangeMin
				|| editedRangeMax != baselineRangeMax
				|| !mappingModel.hasSameState( baselineMapping );
	}

	/**
	 * Whether {@link #currentPalette} is the same palette as
	 * {@link #baselinePalette} -- deliberately not object identity:
	 * {@link LutPalettes#load} returns a fresh instance per call, so
	 * re-picking the palette that is already selected would otherwise count
	 * as an edit and pop the "Discard unapplied changes?" prompt on close.
	 * <p>
	 * Named palettes compare by name (two different names are two different
	 * palettes, even in the unlikely case their colors coincide); an unnamed
	 * one -- e.g. loaded from a converter set up elsewhere, see
	 * {@link #loadIntoEditor} -- has only its colors to go on.
	 */
	private boolean samePaletteAsBaseline()
	{
		if ( currentPalette == baselinePalette )
			return true;
		if ( currentPaletteName != null || baselinePaletteName != null )
			return Objects.equals( currentPaletteName, baselinePaletteName );
		return currentPalette instanceof ColorTableLut
				&& ( ( ColorTableLut ) currentPalette ).hasSameColors( baselinePalette );
	}

	/**
	 * The color palette chooser: every discovered palette, grouped under a
	 * non-selectable {@link CategoryHeader} per {@link LutCategories} category.
	 */
	private JComboBox< Object > createPaletteCombo()
	{
		final JComboBox< Object > combo = createGroupedCombo( "Select Palette" );
		final GroupedComboModel model = ( GroupedComboModel ) combo.getModel();
		for ( final Map.Entry< String, List< String > > category : LutCategories.groupByCategory( LutPalettes.discoverNames() ).entrySet() )
		{
			model.addElement( new CategoryHeader( category.getKey() ) );
			for ( final String name : category.getValue() )
				model.addElement( name );
		}
		return combo;
	}

	/**
	 * The saved-setting chooser (see {@link EditorPresets}): built-in and
	 * user-saved settings, grouped under a non-selectable
	 * {@link CategoryHeader} each. Populated by {@link #refreshEditorPresetCombo}.
	 */
	private JComboBox< Object > createEditorPresetCombo()
	{
		final JComboBox< Object > combo = createGroupedCombo( "Select Preset" );
		refreshEditorPresetCombo( combo );
		return combo;
	}

	/**
	 * Rebuild {@code combo}'s items from {@link EditorPresets#discoverNames()},
	 * grouped into "My Settings" (user-saved) and "Built-in", preserving the
	 * current selection if it is still present. Called on construction and
	 * again after {@link #promptAndSaveEditorPreset()} adds/overwrites one.
	 */
	private static void refreshEditorPresetCombo( final JComboBox< Object > combo )
	{
		final GroupedComboModel model = ( GroupedComboModel ) combo.getModel();
		final Object previouslySelected = combo.getSelectedItem();

		final List< String > userDefined = new ArrayList<>();
		final List< String > builtin = new ArrayList<>();
		for ( final String name : EditorPresets.discoverNames() )
			( EditorPresets.isUserDefined( name ) ? userDefined : builtin ).add( name );

		model.removeAllElements();
		if ( !userDefined.isEmpty() )
		{
			model.addElement( new CategoryHeader( "My Settings" ) );
			for ( final String name : userDefined )
				model.addElement( name );
		}
		if ( !builtin.isEmpty() )
		{
			model.addElement( new CategoryHeader( "Built-in" ) );
			for ( final String name : builtin )
				model.addElement( name );
		}

		combo.setSelectedItem( previouslySelected );
	}

	/**
	 * A combo box that renders {@link CategoryHeader} items as bold,
	 * unselectable group labels (see {@link GroupedComboModel}) among regular
	 * {@code String} items, showing {@code placeholderText} when nothing is
	 * selected. Shared by {@link #createPaletteCombo()} and
	 * {@link #createEditorPresetCombo()}.
	 */
	private static JComboBox< Object > createGroupedCombo( final String placeholderText )
	{
		final JComboBox< Object > combo = new JComboBox<>( new GroupedComboModel() );
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
						setText( placeholderText );
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

	/** The "Setting", "Data" and "Mapping" panels, stacked. */
	private JPanel createLeftColumn()
	{
		final JPanel panelSetting = new JPanel();
		panelSetting.setLayout( new BoxLayout( panelSetting, BoxLayout.PAGE_AXIS ) );
		final JPanel rowPresetSetting = new JPanel( new BorderLayout( 8, 0 ) );
		rowPresetSetting.setBorder( BorderFactory.createEmptyBorder(0, 2, 0, 2) );
		rowPresetSetting.add( comboEditorPreset, BorderLayout.CENTER );
		rowPresetSetting.add( buttonSaveEditorPreset, BorderLayout.EAST );
		rowPresetSetting.setMaximumSize( new Dimension( Integer.MAX_VALUE, rowPresetSetting.getPreferredSize().height ) );
		rowPresetSetting.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelSetting.add( rowPresetSetting );
		panelSetting.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelSetting.setMaximumSize( new Dimension( Integer.MAX_VALUE, panelSetting.getPreferredSize().height ) );

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
		panelRangeMode.add( labelCyclicPeriod );
		panelRangeMode.add( Box.createHorizontalStrut( 4 ) );
		panelRangeMode.add( fieldCyclicPeriod );
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
		column.add( panelSetting );
		column.add( Box.createVerticalStrut( 4 ) );
		column.add( panelData );
		column.add( Box.createVerticalStrut( 4 ) );
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
		buttonCancel.addActionListener( e -> setVisible( false ) );
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
			currentPaletteName = name;
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

			// Revert to the "auto" cyclic period (see MappingModel#getCyclicPeriod)
			// so it re-derives from this palette's own color count, same as
			// picking a palette always overrides value matching above --
			// rather than silently keeping a period sized for the previous,
			// possibly differently-sized, palette.
			mappingModel.setCyclicPeriod( MappingModel.AUTO_CYCLIC_PERIOD );
			updateCyclicPeriodFieldText();
		} );

		final ActionListener listenerRangeMode = e ->
		{
			final boolean cyclic = radioCyclic.isSelected();
			labelCyclicPeriod.setEnabled( cyclic );
			fieldCyclicPeriod.setEnabled( cyclic );
			if ( loadingControls )
				return;
			mappingModel.setRangeMode( cyclic ? RangeMode.CYCLIC : RangeMode.FIT );
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

		comboEditorPreset.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			final Object selected = comboEditorPreset.getSelectedItem();
			if ( !( selected instanceof String ) )
				return;
			final String name = ( String ) selected;
			final EditorPreset preset = EditorPresets.load( name );
			if ( preset == null )
			{
				labelStatus.setText( "Failed to load setting: " + name );
				return;
			}
			applyEditorPreset( preset );
		} );

		buttonSaveEditorPreset.addActionListener( e -> promptAndSaveEditorPreset() );

		getRootPane().registerKeyboardAction( e -> showHelp(), KeyStroke.getKeyStroke( KeyEvent.VK_F1, 0 ), JComponent.WHEN_IN_FOCUSED_WINDOW );

		mappingModel.addChangeListener( panelMappingCurve::repaint );
		mappingModel.addChangeListener( panelPaletteSwatch::repaint );
		mappingModel.addChangeListener( this::pushLiveEdits );
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
	 * source/setup into the editor. Any live-pushed edits still outstanding
	 * on whichever source/setup was previously active are first reverted
	 * back to their own baseline, same as closing the dialog on them without
	 * pressing "Apply" would have done.
	 */
	private void onSourceChanged()
	{
		revertActiveConverterToBaseline();

		final int idx = comboSource.getSelectedIndex();
		if ( idx < 0 || idx >= sources.size() )
		{
			activeLutConv = null;
			activeSetup = null;
			resetEditorToDefaults();
			labelStatus.setText( "no setup selected" );
			return;
		}
		final SourceAndConverter< ? > soc = sources.get( idx );
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		final Object conv = soc.getConverter();
		if ( !( conv instanceof RealLUTConverter ) )
		{
			activeLutConv = null;
			activeSetup = null;
			resetEditorToDefaults();
			labelStatus.setText( "Converter does not use a LUT." );
			return;
		}
		labelStatus.setText( "" );

		final RealLUTConverter< ? > lutConv = ( RealLUTConverter< ? > ) conv;
		activeLutConv = lutConv;
		activeSetup = setup;

		final ColorTable palette = lutConv.getLUT() != null ? lutConv.getLUT() : ColorTableLut.DEFAULT;

		final MappingModel existing = lutConv.getMapping();
		final MappingModel loaded;
		if ( existing != null )
			loaded = existing;
		else
		{
			loaded = new MappingModel();
			loaded.setRangeMode( RangeMode.FIT );
			loaded.setValueMatching( ValueMatching.INTERPOLATE );
			loaded.applyPreset( MappingPreset.LINEAR );
		}

		final double min = setup != null ? setup.getDisplayRangeMin() : 0;
		final double max = setup != null ? setup.getDisplayRangeMax() : 255;
		// The palette itself doesn't remember which named resource (if any)
		// it was loaded from, so recover it by matching colors against the
		// known palettes -- leaving comboPalette deselected ("Select
		// Palette") only for a genuinely unmatched/custom table, rather than
		// unconditionally for every source.
		loadIntoEditor( palette, LutPalettes.findName( palette ), loaded, min, max );

		snapshotBaseline();
	}

	/**
	 * Reset the editor to a neutral default state (as if freshly created),
	 * used whenever there is no valid LUT-backed source/setup to actually
	 * load -- otherwise every control would keep showing whatever the
	 * previously selected source left behind, which is misleading (e.g. the
	 * mapping preset combo still showing "Linear" for a source that isn't
	 * even LUT-based).
	 */
	private void resetEditorToDefaults()
	{
		final MappingModel defaults = new MappingModel();
		defaults.setRangeMode( RangeMode.FIT );
		defaults.setValueMatching( ValueMatching.INTERPOLATE );
		defaults.applyPreset( MappingPreset.LINEAR );
		loadIntoEditor( ColorTableLut.DEFAULT, null, defaults, 0, 255 );
		snapshotBaseline();
	}

	/**
	 * Load a palette/mapping/range into the editor's own controls, without
	 * touching {@link #activeLutConv} itself (callers decide separately
	 * whether/what to push there). Used both for a newly selected source's
	 * actually-applied state, and to reset the editor back to
	 * {@link #baselinePalette} etc. when reverting.
	 */
	private void loadIntoEditor( final ColorTable palette, final String paletteName, final MappingModel mapping, final double min, final double max )
	{
		loadingControls = true;
		try
		{
			currentPalette = palette;
			currentPaletteName = paletteName;
			editedRangeMin = min;
			editedRangeMax = max;

			panelPaletteSwatch.update( palette );
			panelMappingCurve.setRange( min, max );
			panelMappingCurve.setPalette( palette );
			comboPalette.setSelectedItem( paletteName );

			mappingModel.copyFrom( mapping );
			updateTreatMinAsBackgroundText();

			radioFit.setSelected( mappingModel.getRangeMode() == RangeMode.FIT );
			radioCyclic.setSelected( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			labelCyclicPeriod.setEnabled( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			fieldCyclicPeriod.setEnabled( mappingModel.getRangeMode() == RangeMode.CYCLIC );
			updateCyclicPeriodFieldText();
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
	 * Parse {@link #fieldCyclicPeriod}'s text and, if it is a valid positive
	 * number, apply it via {@link MappingModel#setCyclicPeriod}; either way,
	 * reset the field's text to whatever period is now actually in effect
	 * (see {@link #updateCyclicPeriodFieldText()}), same as
	 * {@link MappingCurvePanel}'s own min/max fields do for invalid input.
	 */
	private void commitCyclicPeriodField()
	{
		try
		{
			final double v = Double.parseDouble( fieldCyclicPeriod.getText().trim() );
			if ( v > 0 )
				mappingModel.setCyclicPeriod( v );
		}
		catch ( final NumberFormatException ignored )
		{
		}
		updateCyclicPeriodFieldText();
	}

	/**
	 * Show the cyclic period actually in effect for {@link #currentPalette}
	 * (see {@link MappingModel#effectiveCyclicPeriod}) -- i.e. the palette's
	 * own color count while {@link MappingModel#getCyclicPeriod()} is at its
	 * "auto" default, or the explicitly set value otherwise.
	 */
	private void updateCyclicPeriodFieldText()
	{
		fieldCyclicPeriod.setText( formatValue( mappingModel.effectiveCyclicPeriod( ColorTableLut.colorPositions( currentPalette ) ) ) );
	}

	/** Snapshot the editor's current state as the new {@link #baselinePalette} etc. to revert unapplied edits back to. */
	private void snapshotBaseline()
	{
		baselinePalette = currentPalette;
		baselinePaletteName = currentPaletteName;
		baselineMapping.copyFrom( mappingModel );
		baselineRangeMin = editedRangeMin;
		baselineRangeMax = editedRangeMax;
	}

	/**
	 * Apply a saved {@link EditorPreset} (see {@link #comboEditorPreset}):
	 * its palette, range mode, background handling and curve, live and
	 * immediately, same as any other edit here. Deliberately leaves
	 * {@link #editedRangeMin}/{@link #editedRangeMax} alone -- a preset is a
	 * reusable "look", not tied to any particular source's data range.
	 */
	private void applyEditorPreset( final EditorPreset preset )
	{
		final ColorTable palette = LutPalettes.load( preset.getPaletteName() );
		if ( palette == null )
		{
			labelStatus.setText( "Setting's palette not found: " + preset.getPaletteName() );
			return;
		}

		final MappingModel presetMapping = new MappingModel();
		presetMapping.setRangeMode( preset.getRangeMode() );
		presetMapping.setTreatMinAsBackground( preset.isTreatMinAsBackground() );
		presetMapping.setBackgroundColor( preset.getBackgroundColor() );
		presetMapping.setValueMatching( ColorTableLut.isInterpolated( palette ) ? ValueMatching.INTERPOLATE : ValueMatching.TRUNCATE );
		presetMapping.setCyclicPeriod( preset.getCyclicPeriod() );
		presetMapping.getCurve().setPoints( preset.getCurveXs(), preset.getCurveYs() );

		loadIntoEditor( palette, preset.getPaletteName(), presetMapping, editedRangeMin, editedRangeMax );
		pushLiveEdits();
		labelStatus.setText( "" );
	}

	/**
	 * Ask the user for a name and save the editor's current palette, range
	 * mode, background handling and curve as a reusable {@link EditorPreset}
	 * (see {@link #applyEditorPreset}) under it, confirming first if that
	 * would overwrite an existing one.
	 */
	private void promptAndSaveEditorPreset()
	{
		// Checked before prompting: nothing the user could type would make a
		// palette-less setting saveable, so asking for a name first would
		// only waste their time.
		if ( currentPaletteName == null )
		{
			labelStatus.setText( "Select a named palette before saving a setting." );
			return;
		}

		final Object selected = comboEditorPreset.getSelectedItem();
		final Object input = JOptionPane.showInputDialog( this, "Setting name:", "Save Setting",
				JOptionPane.PLAIN_MESSAGE, null, null, selected instanceof String ? selected : "" );
		if ( input == null )
			return;
		// Canonicalize up front: a preset is identified by its file name, so
		// this is the name it will actually be stored and listed under -- and
		// the only form that can be meaningfully compared against
		// discoverNames() just below.
		final String name = EditorPresets.canonicalName( ( String ) input );
		if ( name.isEmpty() )
		{
			labelStatus.setText( "Setting name cannot be empty." );
			return;
		}
		if ( EditorPresets.discoverNames().contains( name ) )
		{
			final int choice = JOptionPane.showConfirmDialog( this,
					"A setting named \"" + name + "\" already exists. Overwrite it?", "Overwrite Setting",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
			if ( choice != JOptionPane.YES_OPTION )
				return;
		}

		try
		{
			EditorPresets.save( new EditorPreset( name, currentPaletteName, mappingModel.getRangeMode(),
					mappingModel.isTreatMinAsBackground(), mappingModel.getBackgroundColor(), mappingModel.getCyclicPeriod(),
					mappingModel.getCurve().xsArray(), mappingModel.getCurve().ysArray() ) );
		}
		catch ( final RuntimeException e )
		{
			// Saving is the one preset operation that can't degrade silently
			// (see EditorPresets#save) -- report it here rather than letting
			// it escape into the button's action listener.
			labelStatus.setText( "Failed to save setting: " + e.getMessage() );
			return;
		}

		refreshEditorPresetCombo( comboEditorPreset );
		loadingControls = true;
		try
		{
			comboEditorPreset.setSelectedItem( name );
		}
		finally
		{
			loadingControls = false;
		}
		labelStatus.setText( "Saved setting \"" + name + "\"." );
	}

	/**
	 * Move the "revert to" baseline forward to the currently edited state,
	 * which is already live-pushed to the converter as it was edited (see
	 * {@link #pushLiveEdits()}) -- so closing the dialog, or switching to
	 * another source and back, no longer discards it.
	 */
	private void applyCurrent()
	{
		if ( activeLutConv == null )
			return;
		snapshotBaseline();
		labelStatus.setText( "Applied." );
	}

	/**
	 * Push {@link #currentPalette}/{@link #mappingModel}/{@link #editedRangeMin}/
	 * {@link #editedRangeMax} to {@link #activeLutConv} so edits are visible
	 * in the viewer immediately. Wired as {@link #mappingModel}'s change
	 * listener; also called directly wherever the range fields change, since
	 * {@link #mappingModel} itself doesn't track those.
	 */
	private void pushLiveEdits()
	{
		if ( loadingControls )
			return;
		pushToActiveConverter( currentPalette, mappingModel, editedRangeMin, editedRangeMax );
	}

	/** Push {@link #baselinePalette}/{@link #baselineMapping}/{@link #baselineRangeMin}/{@link #baselineRangeMax} to {@link #activeLutConv}, discarding any live-pushed edits made since. */
	private void revertActiveConverterToBaseline()
	{
		pushToActiveConverter( baselinePalette, baselineMapping, baselineRangeMin, baselineRangeMax );
	}

	private void pushToActiveConverter( final ColorTable palette, final MappingModel mapping, final double min, final double max )
	{
		if ( activeLutConv == null )
			return;
		activeLutConv.setLUT( palette );
		final MappingModel target = activeLutConv.getMapping() != null ? activeLutConv.getMapping() : new MappingModel();
		target.copyFrom( mapping );
		activeLutConv.setMapping( target );
		if ( activeSetup != null )
			activeSetup.setDisplayRange( min, max );
		if ( repaintAction != null )
			repaintAction.run();
	}

	/**
	 * Discard unapplied live edits: revert {@link #activeLutConv} to
	 * {@link #baselinePalette} etc., and reset the editor's own controls to
	 * match, so this dialog doesn't reopen showing the discarded edits.
	 */
	private void revertLiveEdits()
	{
		revertActiveConverterToBaseline();
		loadIntoEditor( baselinePalette, baselinePaletteName, baselineMapping, baselineRangeMin, baselineRangeMax );
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
				"Setting:",
				"- Applies a saved combination of palette, range mode, background handling",
				"  and curve (see EditorPreset) -- built-in ones ship with the app, and",
				"  \"Save...\" stores the current combination (under a name you choose) for",
				"  reuse later, next to the built-in ones under \"My Settings\". Applying one",
				"  leaves the current input value range alone, since that is specific to",
				"  whatever source's data you are editing, not part of the saved look.",
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
				"  values with the given Period (raw input units per full cycle), anchored",
				"  at min -- e.g. with Period 10 and min=5, value 5 gets the palette's",
				"  first color, value 15 gets the same color again, and so on.",
				"- Period defaults to the palette's own color count (shown on the graph's",
				"  y axis), spreading exactly one color per raw unit; setting it to",
				"  something else spreads the same colors across a longer or shorter span",
				"  instead. Only used in Cyclic mode; editing the palette resets it back",
				"  to that default for the newly chosen palette.",
				"- \"Treat {min} as Bg\" (its label shows the actual min value) forces raw",
				"  values at or below min to always map to a dedicated background color,",
				"  instead of the palette. In Cyclic mode this also reserves the range's",
				"  min value itself so it stops competing with the cycled colors, and the",
				"  cutoff extends slightly above min (up to but not including min + 1,",
				"  always exactly 1 raw unit regardless of Period) so continuous data",
				"  right next to it doesn't leak through as the palette's last color.",
				"  Click the swatch next to the checkbox to choose the background color",
				"  (defaults to black); it is not one of the palette's own colors.",
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
				"- Edits here take effect in the viewer immediately, as you make them.",
				"- Apply keeps the current edits as the new fallback to revert to.",
				"- Cancel (or closing the dialog, or switching source without Apply)",
				"  reverts to that fallback, discarding edits made since.",
				"",
				"Shortcut:",
				"- Press F1 anywhere in this dialog to open this help." );

		JOptionPane.showMessageDialog( this, message, "LUT Editor Help", JOptionPane.INFORMATION_MESSAGE );
	}

	/**
	 * A non-selectable row in a {@link #createGroupedCombo grouped combo},
	 * labeling the names that follow it: a {@link LutCategories} category in
	 * {@link #comboPalette}, or built-in vs. user-saved in
	 * {@link #comboEditorPreset}.
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
	private static final class GroupedComboModel extends DefaultComboBoxModel< Object >
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
