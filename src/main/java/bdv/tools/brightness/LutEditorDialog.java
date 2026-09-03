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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import bdv.tools.brightness.colorscheme.ColorScheme;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.BoundaryCondition;
import bdv.tools.brightness.palette.PaletteWrapper;
import bdv.tools.brightness.presetfunc.StepPresetFunc;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;

/**
 * A LUT editor dialog, laid out as a header strip over two columns:
 * <ul>
 * <li><b>Configuration</b>: the strip across the top -- a saved, reusable
 * combination of everything below it except the input value range (see
 * {@link EditorPreset}), which can be applied in one step or saved back under
 * a name of the user's choosing. It sits at the top not because it is the
 * control reached for most often, but because it is the one whose scope is
 * the whole window; it is styled to read as subordinate to what it
 * governs.</li>
 * <li><b>Data</b>: which color palette (LUT) is used to render the source,
 * and whether that palette is a smooth gradient or individually chosen
 * colors.</li>
 * <li><b>Function</b>: how a raw source value is turned into a position in
 * that palette -- a transfer function for a continuous palette, a step size
 * for a discrete one.</li>
 * <li><b>Mapping</b>: what happens to raw values past either end of the
 * input value range, independently for each end (see
 * {@link BoundaryCondition}).</li>
 * <li><b>Transfer function</b>: the graph, which also carries the controls
 * whose meaning is positional -- the input range at the ends of the x axis,
 * and the pencil beside the plot it edits (see {@link MappingCurvePanel}).</li>
 * </ul>
 * Which source is being edited is not chosen here: this window follows the
 * viewer's own current-source selection and names the source it is bound to in
 * its title (see {@link #beginSession}). It is not modal, so it can be left
 * open beside the viewer while sources are switched there.
 * <p>
 * Edits take effect in the viewer immediately (see {@link #pushLiveEdits()});
 * "Apply" moves the revert-to baseline forward, "Reset" goes back to it, and
 * closing without applying restores it (see {@link #setVisible(boolean)}).
 */
public class LutEditorDialog extends JDialog
{
	private final ConverterSetups converterSetups;
	private final ViewerState viewerState;
	private final Runnable repaintAction;

	/** Kept so {@link #dispose()} can unregister it again; see {@link #installViewerStateListener()}. */
	private ViewerStateChangeListener viewerStateListener;

	/**
	 * Set by {@link #dispose()}, after which this dialog must not touch the
	 * viewer again. Volatile because {@link #dispose()} is not guaranteed to
	 * run on the EDT -- {@code BdvHandle.close()} is routinely called from
	 * whichever thread closes the window -- while what it stops runs on the
	 * EDT.
	 */
	private volatile boolean disposed = false;

	private final JComboBox< Object > comboPalette;
	private final JComboBox< Object > comboEditorPreset;
	private final JButton buttonSaveEditorPreset;
	private final JLabel labelStatus;

	/** Whether the selected palette is used as a gradient or as individually chosen colors, and how many colors it has; see {@link #updateShapeControls}. */
	private final JLabel labelPaletteKind;

	/** How far the palette reaches at the current step size; see {@link #updateStepCoverageLabel}. */
	private final JLabel labelStepCoverage;

	/** What the graph is currently offering, if anything: how to edit the transfer function, or why it cannot be edited; see {@link #updateCurveHint()}. */
	private final JLabel labelCurveHint;

	private final GradientPreviewPanel panelPaletteSwatch;
	private final MappingCurvePanel panelMappingCurve;

	private final JComboBox< BoundaryCondition > comboLeftBoundary;
	private final JComboBox< BoundaryCondition > comboRightBoundary;
	private final JButton buttonLeftSpecialColor;
	private final JButton buttonRightSpecialColor;

	/**
	 * The shape controls, swapped by {@link #SHAPE_CARD_CONTINUOUS}/
	 * {@link #SHAPE_CARD_DISCRETE}: a continuous palette is shaped by a preset
	 * curve, a discrete one by a step size -- see {@link LutEditorMapping}.
	 */
	private final JPanel panelShape;
	private final CardLayout layoutShape = new CardLayout();
	private static final String SHAPE_CARD_CONTINUOUS = "continuous";
	private static final String SHAPE_CARD_DISCRETE = "discrete";

	/** Height of the help window's scrolling text box; see {@link #showHelp()}. */
	private static final int HELP_HEIGHT = 420;

	/** Ceiling on the help window's width; see {@link #showHelp()}. */
	private static final int MAX_HELP_WIDTH = 720;

	private final JComboBox< PresetShape > comboMappingPreset;
	private final JButton buttonInvertCurve;
	private final JTextField fieldStepSize;

	/**
	 * The palette and mapping currently being edited. Edits are pushed live
	 * to {@link #activeLutConv} as they happen (see {@link #pushLiveEdits()}),
	 * so they are visible in the viewer immediately, not just after "Apply".
	 */
	private Palette currentPalette = Palette.DEFAULT;

	/** Name of {@link #currentPalette} in {@link #comboPalette}, or {@code null} if it doesn't (or isn't known to) correspond to one -- see {@link #loadIntoEditor}. */
	private String currentPaletteName = null;

	private final LutEditorMapping mappingModel = new LutEditorMapping();

	/** The input value range currently being edited; see {@link #currentPalette}. */
	private double editedRangeMin = 0;
	private double editedRangeMax = 255;

	/**
	 * The source this editing session belongs to (see {@link #beginSession}),
	 * or {@code null} if there is none. Its name is what the window title
	 * announces, and it is what {@link #syncToCurrentSource()} compares the
	 * viewer's current source against to decide whether anything changed.
	 */
	private SourceAndConverter< ? > sessionSource = null;

	/** The setup/converter {@link #currentPalette} etc. are being live-pushed to; {@code null} if none is currently editable. */
	private PaletteConverter< ? > activeLutConv = null;

	/**
	 * The {@link SourceAndConverter#asVolatile() volatile} counterpart of
	 * {@link #activeLutConv}, if the source has one and it is also a
	 * {@link PaletteConverter}. Edits go to both: the volatile converter is
	 * what renders while data is still loading, so leaving it behind would
	 * show the old colors until the last block arrives and then snap.
	 */
	private PaletteConverter< ? > activeVolatileLutConv = null;

	private ConverterSetup activeSetup = null;

	/**
	 * Sources whose foreign converter the user has already declined to convert
	 * (see {@link #offerConversion}), so that selecting one again -- which
	 * happens on every pass through the sources with the 1..9 keys in the
	 * viewer -- does not ask a second time. Weakly held so it does not keep
	 * sources alive.
	 */
	private final Set< SourceAndConverter< ? > > declinedConversion =
			Collections.newSetFromMap( new WeakHashMap<>() );

	/**
	 * The editor-facing configuration ({@link Palette} + editable
	 * {@link LutEditorMapping}) last pushed to each {@link PaletteConverter}, so
	 * re-selecting a source can restore what the editor last showed for it.
	 * <p>
	 * The converter itself only stores the derived {@link PaletteWrapper} it
	 * renders through (which cannot be read back into the editor's richer
	 * palette-plus-curve terms); this remembers those terms instead. Weakly
	 * keyed so it does not keep converters (hence sources) alive. The display
	 * range is deliberately not stored here -- it lives on the setup and is
	 * always read back fresh, so brightness/contrast changes made outside this
	 * dialog are not clobbered.
	 */
	private final Map< PaletteConverter< ? >, EditorState > converterStates = new WeakHashMap<>();

	/** The editor-facing palette + mapping remembered per converter; see {@link #converterStates}. */
	private static final class EditorState
	{
		final Palette palette;
		final String paletteName;
		final LutEditorMapping mapping;

		EditorState( final Palette palette, final String paletteName, final LutEditorMapping mapping )
		{
			this.palette = palette;
			this.paletteName = paletteName;
			this.mapping = mapping;
		}
	}

	/**
	 * Snapshot of {@link #activeLutConv}'s state as of the last "Apply" (or,
	 * absent that, as loaded by {@link #onSourceChanged()}) -- what
	 * {@link #revertLiveEdits()} restores unapplied live edits back to.
	 */
	private Palette baselinePalette = Palette.DEFAULT;
	private String baselinePaletteName = null;
	private final LutEditorMapping baselineMapping = new LutEditorMapping();
	private double baselineRangeMin = 0;
	private double baselineRangeMax = 255;

	/** Guards against control listeners (including the live-push one) firing while we are programmatically syncing them. */
	private boolean loadingControls = false;

	/** The text {@link #updateStepSizeField()} last put in {@link #fieldStepSize}; see {@link #commitStepSizeField()} for why it is remembered. */
	private String lastShownStepSize = "";

	public LutEditorDialog( final Frame owner, final ConverterSetups converterSetups, final ViewerState viewerState, final Runnable repaintAction )
	{
		super( owner, "LUT Editor", false );
		this.converterSetups = converterSetups;
		this.viewerState = viewerState;
		this.repaintAction = repaintAction;

		// -- Widgets ---------------------------------------------------------
		comboPalette = createPaletteCombo();
		comboEditorPreset = createEditorPresetCombo();
		buttonSaveEditorPreset = new JButton( "Save as..." );
		buttonSaveEditorPreset.setFocusable( false );
		panelPaletteSwatch = new GradientPreviewPanel();
		panelPaletteSwatch.setPreferredSize( new Dimension( 200, 16 ) );
		panelPaletteSwatch.setMaximumSize( new Dimension( Integer.MAX_VALUE, 16 ) );

		comboLeftBoundary = createBoundaryCombo();
		comboRightBoundary = createBoundaryCombo();
		buttonLeftSpecialColor = createSpecialColorButton( "Color for values below the range" );
		buttonRightSpecialColor = createSpecialColorButton( "Color for values above the range" );
		comboMappingPreset = new JComboBox<>( PresetShape.values() );
		buttonInvertCurve = new JButton( "Invert" );
		buttonInvertCurve.setFocusable( false );
		fieldStepSize = new JTextField();
		panelShape = heightCapped( layoutShape );

		panelMappingCurve = new MappingCurvePanel( mappingModel );
		panelMappingCurve.setRangeChangeListener( ( min, max ) ->
		{
			editedRangeMin = min;
			editedRangeMax = max;
			// An automatic step size is derived from the range, so the field
			// showing it has to follow the range to keep telling the truth.
			updateStepSizeField();
			pushLiveEdits();
		} );

		labelStatus = new JLabel( "" );
		labelPaletteKind = mutedLabel( "" );
		labelStepCoverage = mutedLabel( "" );
		labelCurveHint = mutedLabel( "" );

		panelMappingCurve.setEditModeListener( editing -> updateCurveHint() );

		// -- Layout ----------------------------------------------------------
		setLayout( new BorderLayout( 0, 4 ) );
		( ( JPanel ) getContentPane() ).setBorder( new EmptyBorder( 12, 12, 12, 12 ) );

		final JPanel panelLeftColumn = createLeftColumn();
		final JPanel panelMappingCurveColumn = createMappingCurveColumn();

		final JPanel panelCenter = new JPanel( new BorderLayout( 12, 0 ) );
		panelCenter.add( panelLeftColumn, BorderLayout.WEST );
		panelCenter.add( hugContents( panelMappingCurveColumn ), BorderLayout.CENTER );
		add( createConfigurationStrip(), BorderLayout.NORTH );
		add( panelCenter, BorderLayout.CENTER );
		add( createBottomBar(), BorderLayout.SOUTH );

		// -- Behavior --------------------------------------------------------
		installControlListeners();
		installViewerStateListener();
		beginSession( viewerState.getCurrentSource() );
		packAndMatchGraphWidth( panelLeftColumn, panelMappingCurveColumn );
	}

	/**
	 * Follow the viewer: which source this window edits is the viewer's own
	 * current-source selection, and changing it there starts a new editing
	 * session here (see {@link #beginSession}).
	 * <p>
	 * {@code CURRENT_SOURCE_CHANGED} is the only change worth listening for,
	 * because it also covers the source list itself changing underneath:
	 * {@code BasicViewerState} fires it when the first source arrives (there
	 * was no current source before), when the current source is removed (the
	 * first remaining one takes over), and when the sources are cleared (there
	 * is no current source left).
	 * <p>
	 * Bounced onto the EDT, since a {@code ViewerState} change can be made from
	 * any thread. That hand-off is why the listener has to be unregistered in
	 * {@link #dispose()} rather than left to be collected with the window: a
	 * notification can outlive the viewer it came from, and the work it queues
	 * would then run against a viewer that has already been torn down.
	 */
	private void installViewerStateListener()
	{
		viewerStateListener = change ->
		{
			if ( change == ViewerStateChange.CURRENT_SOURCE_CHANGED )
				SwingUtilities.invokeLater( this::syncToCurrentSource );
		};
		viewerState.changeListeners().add( viewerStateListener );
	}

	/**
	 * Stop following the viewer, then dispose the window as usual.
	 * <p>
	 * Both halves are needed, because unregistering the listener does not
	 * recall the notifications it has already turned into queued EDT work:
	 * {@code BdvHandle.close()} disposes this dialog and then drops the viewer,
	 * so a {@link #syncToCurrentSource()} still sitting on the queue would come
	 * to run afterwards and drive a repaint of a viewer that is no longer
	 * there. {@link #disposed} is what those queued runnables check.
	 * <p>
	 * This is teardown, not hiding -- closing the window with "Cancel", its
	 * close button or the keyboard shortcut goes through
	 * {@link #setVisible(boolean)} and leaves the dialog reusable. A disposed
	 * dialog is done: showing it again would give a window that no longer
	 * follows the viewer's source selection.
	 */
	@Override
	public void dispose()
	{
		disposed = true;
		if ( viewerStateListener != null )
		{
			viewerState.changeListeners().remove( viewerStateListener );
			viewerStateListener = null;
		}
		super.dispose();
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
	 * <p>
	 * Becoming visible is the other half of the same idea: it opens a new
	 * session (see {@link #restartSession()}), so the window always shows the
	 * source the viewer is on and takes its backup from what is on screen now,
	 * not from whenever it was last closed.
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

		final boolean showing = visible && !isVisible();
		super.setVisible( visible );
		if ( showing )
			restartSession();
	}

	/**
	 * Open a session for the source the viewer is currently on, now that the
	 * window is actually on screen.
	 * <p>
	 * Needed as its own entry point because a session started while the window
	 * was hidden cannot warn about a converter it is unable to edit (see
	 * {@link #offerConversion}) -- there would be a modal prompt on screen with
	 * nothing behind it to explain where it came from. The dialog is
	 * constructed with the viewer and only shown later, so without this the
	 * warning would never appear for the source the user opens it on.
	 * <p>
	 * The previous session is dropped rather than reverted: hiding the window
	 * already reverted whatever was outstanding, and forgetting it here is what
	 * stops {@link #beginSession} from pushing a stale display range back over
	 * one the brightness controls changed while this window was closed.
	 */
	private void restartSession()
	{
		activeLutConv = null;
		activeVolatileLutConv = null;
		activeSetup = null;
		beginSession( viewerState.getCurrentSource() );
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
		return Objects.equals( currentPalette, baselinePalette );
	}

	/**
	 * The color palette chooser: every discovered palette, grouped under a
	 * non-selectable {@link CategoryHeader} per {@link LutCategories} category.
	 */
	private JComboBox< Object > createPaletteCombo()
	{
		final JComboBox< Object > combo = createGroupedCombo( "Select Palette" );
		// Without a prototype the combo is as wide as its widest item, which
		// is one of the bold category headers ("Perceptually Uniform
		// Sequential") rather than any palette -- and that width then set the
		// width of the whole settings column. Sized for the longest bundled
		// palette name instead; the popup is free to be wider than the box.
		combo.setPrototypeDisplayValue( "twilight_shifted_r" );
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
		final JComboBox< Object > combo = createGroupedCombo( "Select Configuration" );
		// As for the palette combo -- and configuration names are the user's
		// own, so there is no longest one to size to. It is stretched to the
		// width of the strip anyway.
		combo.setPrototypeDisplayValue( "Select Configuration" );
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

	/**
	 * A per-end boundary-condition chooser: what happens to raw values past
	 * that end of the input range. Offers the render model's own
	 * {@link BoundaryCondition}s directly, just relabeled for the UI (see
	 * {@link #boundaryLabel}).
	 */
	private static JComboBox< BoundaryCondition > createBoundaryCombo()
	{
		final JComboBox< BoundaryCondition > combo = new JComboBox<>( BoundaryCondition.values() );
		combo.setRenderer( new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent( final JList< ? > list, final Object value,
					final int index, final boolean isSelected, final boolean cellHasFocus )
			{
				super.getListCellRendererComponent( list, value, index, isSelected, cellHasFocus );
				if ( value instanceof BoundaryCondition )
					setText( boundaryLabel( ( BoundaryCondition ) value ) );
				return this;
			}
		} );
		return combo;
	}

	/** How a {@link BoundaryCondition} is named in this UI; see the enum itself for what each one actually does. */
	private static String boundaryLabel( final BoundaryCondition condition )
	{
		switch ( condition )
		{
			case CLAMP:
				return "Clamp";
			case CYCLE:
				return "Cycle";
			case SPECIAL:
				return "Fixed color";
			default:
				return condition.name();
		}
	}

	/**
	 * The small swatch button that opens one end's fixed-color chooser. Starts
	 * disabled: it is only meaningful while that end is set to
	 * {@link BoundaryCondition#SPECIAL}.
	 */
	private static JButton createSpecialColorButton( final String toolTip )
	{
		final JButton button = new JButton();
		button.setToolTipText( toolTip );
		final Dimension size = new Dimension( 20, 20 );
		button.setPreferredSize( size );
		button.setMinimumSize( size );
		button.setMaximumSize( size );
		button.setBackground( new Color( LutEditorMapping.DEFAULT_SPECIAL_COLOR, false ) );
		button.setEnabled( false );
		return button;
	}

	/**
	 * The configuration strip across the top of the window: a saved
	 * combination that can be applied in one step, or saved back under a name.
	 * <p>
	 * Full width, above both columns, because it governs everything below it
	 * -- but deliberately understated (a muted label, no group box, a rule to
	 * separate it) rather than presented as the window's headline control,
	 * which it is not: the palette and the input range are what actually get
	 * touched on most visits here.
	 */
	private JPanel createConfigurationStrip()
	{
		final JPanel row = new JPanel( new BorderLayout( 8, 0 ) );
		row.add( mutedLabel( "Configuration" ), BorderLayout.WEST );
		row.add( comboEditorPreset, BorderLayout.CENTER );
		row.add( buttonSaveEditorPreset, BorderLayout.EAST );

		final JPanel strip = new JPanel( new BorderLayout( 0, 6 ) );
		strip.add( row, BorderLayout.CENTER );
		strip.add( new JSeparator(), BorderLayout.SOUTH );
		return strip;
	}

	/** The "Mapping" panel: what happens to raw values past either end of the input range, one labelled row per end. */
	private JPanel createBoundaryGroup()
	{
		final JPanel panel = heightCapped( null );
		panel.setLayout( new BoxLayout( panel, BoxLayout.PAGE_AXIS ) );
		panel.setBorder( BorderFactory.createTitledBorder( "Mapping" ) );
		panel.add( labeledRow( "Below range:", comboLeftBoundary, buttonLeftSpecialColor ) );
		panel.add( Box.createVerticalStrut( 4 ) );
		panel.add( labeledRow( "Above range:", comboRightBoundary, buttonRightSpecialColor ) );
		panel.setAlignmentX( Component.LEFT_ALIGNMENT );
		return panel;
	}

	/** The "Data", "Function" and "Mapping" panels, stacked. */
	private JPanel createLeftColumn()
	{
		final JPanel panelData = heightCapped( null );
		panelData.setLayout( new BoxLayout( panelData, BoxLayout.PAGE_AXIS ) );
		panelData.setBorder( BorderFactory.createTitledBorder( "Data" ) );
		panelData.add( labeledRow( "Color palette:", comboPalette ) );
		panelData.add( Box.createVerticalStrut( 4 ) );
		// Left-aligned like every other row: a BoxLayout column asked to mix
		// alignments reserves room for the widest child on BOTH sides of the
		// alignment point, so one centered child was making the whole settings
		// column about a hundred pixels wider than its widest row.
		panelPaletteSwatch.setAlignmentX( Component.LEFT_ALIGNMENT );
		panelData.add( panelPaletteSwatch );
		panelData.add( Box.createVerticalStrut( 4 ) );
		panelData.add( leftAligned( labelPaletteKind ) );
		panelData.setAlignmentX( Component.LEFT_ALIGNMENT );

		// One card or the other, never both: which one is showing follows the
		// palette's own kind (see #updateShapeControls). A CardLayout rather
		// than swapping visibility so the panel keeps the taller card's height
		// either way, and the dialog does not resize as the palette changes.
		panelShape.add( continuousShapeCard(), SHAPE_CARD_CONTINUOUS );
		panelShape.add( discreteShapeCard(), SHAPE_CARD_DISCRETE );
		panelShape.setAlignmentX( Component.LEFT_ALIGNMENT );

		final JPanel panelShapeGroup = heightCapped( null );
		panelShapeGroup.setLayout( new BoxLayout( panelShapeGroup, BoxLayout.PAGE_AXIS ) );
		panelShapeGroup.setBorder( BorderFactory.createTitledBorder( "Function" ) );
		panelShapeGroup.add( panelShape );
		panelShapeGroup.setAlignmentX( Component.LEFT_ALIGNMENT );

		final JPanel column = new JPanel();
		column.setLayout( new BoxLayout( column, BoxLayout.PAGE_AXIS ) );
		column.add( panelData );
		column.add( Box.createVerticalStrut( 4 ) );
		column.add( panelShapeGroup );
		column.add( Box.createVerticalStrut( 4 ) );
		column.add( createBoundaryGroup() );
		return column;
	}

	/** A continuous palette's shape: a predefined transfer function, optionally flipped. */
	private JPanel continuousShapeCard()
	{
		final JPanel card = new JPanel();
		card.setLayout( new BoxLayout( card, BoxLayout.PAGE_AXIS ) );
		card.add( labeledRow( "Preset:", comboMappingPreset ) );
		card.add( Box.createVerticalStrut( 4 ) );
		final JPanel invertRow = new JPanel( new FlowLayout( FlowLayout.RIGHT, 0, 0 ) );
		invertRow.add( buttonInvertCurve );
		invertRow.setAlignmentX( Component.LEFT_ALIGNMENT );
		invertRow.setMaximumSize( new Dimension( Integer.MAX_VALUE, invertRow.getPreferredSize().height ) );
		card.add( invertRow );
		card.add( Box.createVerticalGlue() );
		return card;
	}

	/** A discrete palette's shape: how many input values one color covers, and how far that takes the palette. */
	private JPanel discreteShapeCard()
	{
		final JPanel card = new JPanel();
		card.setLayout( new BoxLayout( card, BoxLayout.PAGE_AXIS ) );
		card.add( labeledRow( "Step size:", fieldStepSize ) );
		card.add( Box.createVerticalStrut( 4 ) );
		card.add( leftAligned( labelStepCoverage ) );
		card.add( Box.createVerticalGlue() );
		return card;
	}

	/** Wrap {@code component} so a {@link BoxLayout} column leaves it at the left edge rather than centering it. */
	private static JPanel leftAligned( final JComponent component )
	{
		final JPanel row = heightCapped( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		row.add( component );
		row.setAlignmentX( Component.LEFT_ALIGNMENT );
		return row;
	}

	/**
	 * A panel that never grows taller than its contents need <em>now</em>.
	 * <p>
	 * A {@link BoxLayout} column stretches each child up to its maximum size,
	 * so every child has to declare a ceiling -- but taking that ceiling once,
	 * while the window is being built, silently freezes out anything with
	 * nothing to show yet: an empty {@link JLabel} measures zero pixels high,
	 * so a line that only gets its text when a palette is chosen would never
	 * be given the room to appear. Computing the cap on demand is what lets
	 * {@link #labelPaletteKind} and {@link #labelStepCoverage} turn up later.
	 */
	private static JPanel heightCapped( final LayoutManager layout )
	{
		return new JPanel( layout )
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension( Integer.MAX_VALUE, getPreferredSize().height );
			}

			private static final long serialVersionUID = 1L;
		};
	}

	/** A label for something said <em>about</em> a control rather than by it: present, but not competing with the control itself. */
	private static JLabel mutedLabel( final String text )
	{
		final JLabel label = new JLabel( text );
		final Color disabled = UIManager.getColor( "Label.disabledForeground" );
		label.setForeground( disabled != null ? disabled : Color.GRAY );
		return label;
	}

	/** The titled "Transfer function" panel around the interactive graph. */
	private JPanel createMappingCurveColumn()
	{
		final JPanel column = new JPanel( new BorderLayout() );
		column.setBorder( BorderFactory.createTitledBorder( "Transfer function" ) );
		column.add( hugContents( panelMappingCurve ), BorderLayout.CENTER );
		return column;
	}

	/** Help and whatever the graph currently has to say on the left, Reset/Cancel/Apply on the right. */
	private JPanel createBottomBar()
	{
		final JButton buttonHelp = new JButton( "?" );
		buttonHelp.setToolTipText( "Help (F1)" );
		buttonHelp.setFocusable( false );
		buttonHelp.setMargin( new Insets( 0, 0, 0, 0 ) );
		buttonHelp.setPreferredSize( new Dimension( 24, 24 ) );
		buttonHelp.addActionListener( e -> showHelp() );

		final JPanel panelLeftBottom = new JPanel( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panelLeftBottom.add( buttonHelp );
		panelLeftBottom.add( Box.createHorizontalStrut( 8 ) );
		panelLeftBottom.add( labelCurveHint );
		panelLeftBottom.add( Box.createHorizontalStrut( 8 ) );
		panelLeftBottom.add( labelStatus );

		final JButton buttonReset = new JButton( "Reset" );
		buttonReset.addActionListener( e -> resetToSessionBaseline() );
		final JButton buttonCancel = new JButton( "Cancel" );
		buttonCancel.addActionListener( e -> setVisible( false ) );
		final JButton buttonApply = new JButton( "Apply" );
		buttonApply.addActionListener( e -> applyCurrent() );
		normalizeButtonSizes( buttonReset, buttonCancel, buttonApply );

		final JPanel panelRightBottom = new JPanel( new GridLayout( 1, 3, 8, 0 ) );
		panelRightBottom.add( buttonReset );
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
		comboPalette.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			final Object selected = comboPalette.getSelectedItem();
			if ( !( selected instanceof String ) )
				return;
			final String name = ( String ) selected;
			final Palette ct = LutPalettes.load( name );
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
			mappingModel.setDiscrete( !ct.isInterpolated() );
			updateShapeControls();
		} );

		comboLeftBoundary.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.setLeftBoundaryCondition( ( BoundaryCondition ) comboLeftBoundary.getSelectedItem() );
			updateSpecialColorButtonStates();
		} );

		comboRightBoundary.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.setRightBoundaryCondition( ( BoundaryCondition ) comboRightBoundary.getSelectedItem() );
			updateSpecialColorButtonStates();
		} );

		buttonLeftSpecialColor.addActionListener( e -> chooseSpecialColor( true ) );
		buttonRightSpecialColor.addActionListener( e -> chooseSpecialColor( false ) );

		comboMappingPreset.addActionListener( e ->
		{
			if ( loadingControls )
				return;
			mappingModel.applyPreset( ( PresetShape ) comboMappingPreset.getSelectedItem() );
		} );

		fieldStepSize.addActionListener( e -> commitStepSizeField() );
		fieldStepSize.addFocusListener( new FocusAdapter()
		{
			@Override
			public void focusLost( final FocusEvent e )
			{
				commitStepSizeField();
			}
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
				labelStatus.setText( "Failed to load configuration: " + name );
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
		// width, which is the widest of Data/Function/Mapping), accounting for
		// the curve column's own titled border insets so the two line up
		// exactly.
		final Insets insets = panelMappingCurveColumn.getBorder().getBorderInsets( panelMappingCurveColumn );
		// Never narrower than the graph itself needs at minimum, in case the
		// settings column should ever end up the narrower of the two.
		final int targetGraphWidth = Math.max( panelLeftColumn.getWidth() - insets.left - insets.right,
				panelMappingCurve.minimumGraphWidth() );
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
	 * Start a new editing session on {@code soc}: bind the window to that
	 * source, take the backup that {@link #resetToSessionBaseline()} and
	 * closing the dialog restore, and load the source's applied palette and
	 * mapping into the controls.
	 * <p>
	 * Any live-pushed edits still outstanding on the previous session's
	 * source/converter are first reverted back to <em>its</em> baseline, the
	 * same as closing the dialog without pressing "Apply" would have done --
	 * so leaving a source behind never silently commits what was being tried
	 * out on it.
	 * <p>
	 * A source rendered by some other kind of converter cannot be edited here;
	 * the user is warned and offered a conversion (see
	 * {@link #offerConversion}), and if that comes to nothing the editor falls
	 * back to a neutral state that is pushed nowhere.
	 */
	private void beginSession( final SourceAndConverter< ? > soc )
	{
		revertActiveConverterToBaseline();

		sessionSource = soc;
		activeLutConv = null;
		activeVolatileLutConv = null;
		activeSetup = null;
		updateTitle();

		if ( soc == null )
		{
			resetEditorToDefaults();
			labelStatus.setText( "no setup selected" );
			return;
		}

		PaletteConverter< ? > lutConv = asPaletteConverter( soc.getConverter() );
		if ( lutConv == null )
			lutConv = offerConversion( soc );
		if ( lutConv == null )
		{
			resetEditorToDefaults();
			labelStatus.setText( "Converter does not use a LUT." );
			return;
		}
		labelStatus.setText( "" );

		activeLutConv = lutConv;
		activeVolatileLutConv = soc.asVolatile() == null ? null : asPaletteConverter( soc.asVolatile().getConverter() );
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		activeSetup = setup;

		// The converter renders through a PaletteWrapper, which cannot be read
		// back into the editor's palette-plus-curve terms; restore what the
		// editor last pushed for this converter instead (see converterStates),
		// falling back to a neutral default for one set up outside this dialog.
		final EditorState state = converterStates.get( lutConv );
		final Palette palette = state != null ? state.palette : Palette.DEFAULT;
		final String paletteName = state != null ? state.paletteName : LutPalettes.findName( palette );
		final LutEditorMapping loaded = state != null ? state.mapping : defaultMapping();

		final double min = setup != null ? setup.getDisplayRangeMin() : 0;
		final double max = setup != null ? setup.getDisplayRangeMax() : 255;
		loadIntoEditor( palette, paletteName, loaded, min, max );

		// The session backup. Deep exactly where it has to be: a Palette is
		// immutable and safe to share, but a mapping is not -- baselineMapping
		// is a separate object whose copyFrom clones the curve's control point
		// arrays, so editing the live mapping cannot reach into the backup.
		snapshotBaseline();
	}

	/** {@code converter} as a {@link PaletteConverter}, or {@code null} if it is some other implementation. */
	private static PaletteConverter< ? > asPaletteConverter( final Converter< ?, ? > converter )
	{
		return converter instanceof PaletteConverter ? ( PaletteConverter< ? > ) converter : null;
	}

	/**
	 * Warn that {@code soc} is rendered by a converter this editor does not
	 * understand, and offer to re-render it through one that it does -- see
	 * {@link PaletteConverterFactory}, which spells out how much of the
	 * original setup survives that translation. Returns the converter now
	 * rendering the source, or {@code null} if it was not converted.
	 * <p>
	 * Only asked while the dialog is actually on screen, and only once per
	 * source: this runs on every source switch, and a modal prompt appearing
	 * behind the user's back, or again every time they cycle past the same
	 * source with the 1..9 keys, would cost more than the warning is worth.
	 */
	private PaletteConverter< ? > offerConversion( final SourceAndConverter< ? > soc )
	{
		if ( !isVisible() || !declinedConversion.add( soc ) )
			return null;

		final Converter< ?, ? > conv = soc.getConverter();
		final String kind = conv == null ? "no converter" : conv.getClass().getSimpleName();
		final String preamble = "Source \"" + sourceName( soc ) + "\" is rendered by " + kind + ",\n"
				+ "which this LUT editor cannot edit.";

		if ( !PaletteConverterFactory.canApproximate( soc ) )
		{
			JOptionPane.showMessageDialog( this,
					preamble + "\n\nIt cannot be converted to a palette-based converter either.",
					"Unsupported Converter", JOptionPane.WARNING_MESSAGE );
			return null;
		}

		final int choice = JOptionPane.showConfirmDialog( this,
				preamble + "\n\nConvert it to a palette-based converter?\n"
						+ "Its display range is kept and mapped linearly; its color\n"
						+ "becomes the closest sequential palette, so the image will\n"
						+ "look similar but not identical.",
				"Unsupported Converter", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
		if ( choice != JOptionPane.YES_OPTION )
			return null;

		final PaletteConverter< ? > converted = PaletteConverterFactory.approximateInPlace( soc );
		if ( converted == null )
			return null;

		// Editable from here on, so no longer a source to stop asking about.
		declinedConversion.remove( soc );
		repointConverterSetup( soc );
		if ( repaintAction != null )
			repaintAction.run();
		return converted;
	}

	/**
	 * Re-point {@code soc}'s {@link ConverterSetup} at the converters now
	 * rendering it, after {@link PaletteConverterFactory} swapped them: it
	 * would otherwise go on reading and writing the display range of a
	 * converter that renders nothing, and the brightness/contrast controls
	 * would appear to do nothing.
	 * <p>
	 * Done in place where the setup allows it, because a {@code ConverterSetup}
	 * is an identity that {@link SetupAssignments}, the brightness dialog and
	 * {@code ConverterSetupBounds} all hold on to and would not follow to a
	 * substitute. A setup of some other implementation has to be replaced
	 * instead, which those holders do not see -- brightness for that source
	 * keeps working through this dialog and the source table, but a group it
	 * was put in by {@code SetupAssignments} will not follow it.
	 */
	private void repointConverterSetup( final SourceAndConverter< ? > soc )
	{
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		if ( setup == null )
			return;
		final List< ColorConverter > converters = PaletteConverterFactory.colorConvertersOf( soc );
		if ( converters.isEmpty() )
			return;
		if ( setup instanceof RealARGBColorConverterSetup )
			( ( RealARGBColorConverterSetup ) setup ).setConverters( converters );
		else
			converterSetups.put( soc, new RealARGBColorConverterSetup( setup.getSetupId(), converters ) );
	}

	/**
	 * Adopt the viewer's current source as this window's. A no-op when it
	 * already is, so that a notification arriving after this dialog has
	 * already reacted -- the listener is dispatched asynchronously, see
	 * {@link #installViewerStateListener()} -- does not restart the session.
	 * Also a no-op once {@link #dispose()} has run, which is the same
	 * asynchrony arriving after the viewer itself has gone.
	 */
	private void syncToCurrentSource()
	{
		if ( disposed )
			return;
		final SourceAndConverter< ? > current = viewerState.getCurrentSource();
		if ( current != sessionSource )
			beginSession( current );
	}

	/** The source's own name, or its setup id if there is no source to ask. */
	private String sourceName( final SourceAndConverter< ? > soc )
	{
		if ( soc.getSpimSource() != null )
			return soc.getSpimSource().getName();
		final ConverterSetup setup = converterSetups.getConverterSetup( soc );
		return setup != null ? Integer.toString( setup.getSetupId() ) : "?";
	}

	/**
	 * Name the source being edited in the window title. This dialog is not
	 * modal and is meant to be left open beside the viewer, where it can
	 * easily end up looking at a source other than the one the user has their
	 * eye on -- the title is what says which.
	 */
	private void updateTitle()
	{
		setTitle( sessionSource == null ? "LUT Editor" : "LUT Editor - " + sourceName( sessionSource ) );
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
		loadIntoEditor( Palette.DEFAULT, null, defaultMapping(), 0, 255 );
		snapshotBaseline();
	}

	/** A neutral mapping (linear, both ends clamped, interpolated) -- the editor's starting point for a source with no remembered state. */
	private static LutEditorMapping defaultMapping()
	{
		final LutEditorMapping defaults = new LutEditorMapping();
		defaults.setLeftBoundaryCondition( BoundaryCondition.CLAMP );
		defaults.setRightBoundaryCondition( BoundaryCondition.CLAMP );
		defaults.setDiscrete( false );
		defaults.applyPreset( PresetShape.LINEAR );
		return defaults;
	}

	/**
	 * Load a palette/mapping/range into the editor's own controls, without
	 * touching {@link #activeLutConv} itself (callers decide separately
	 * whether/what to push there). Used both for a newly selected source's
	 * actually-applied state, and to reset the editor back to
	 * {@link #baselinePalette} etc. when reverting.
	 */
	private void loadIntoEditor( final Palette palette, final String paletteName, final LutEditorMapping mapping, final double min, final double max )
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

			comboLeftBoundary.setSelectedItem( mappingModel.getLeftBoundaryCondition() );
			comboRightBoundary.setSelectedItem( mappingModel.getRightBoundaryCondition() );
			buttonLeftSpecialColor.setBackground( new Color( mappingModel.getLeftSpecialColor(), false ) );
			buttonRightSpecialColor.setBackground( new Color( mappingModel.getRightSpecialColor(), false ) );
			updateSpecialColorButtonStates();
			updateShapeControls();
			syncEditorPresetSelection();
		}
		finally
		{
			loadingControls = false;
		}
	}

	/**
	 * Sync the shape controls to {@link #mappingModel}: which card is showing
	 * -- a continuous palette is shaped by a preset curve, a discrete one by a
	 * step size (see {@link LutEditorMapping}) -- and that card's own value.
	 */
	private void updateShapeControls()
	{
		layoutShape.show( panelShape, mappingModel.isDiscrete() ? SHAPE_CARD_DISCRETE : SHAPE_CARD_CONTINUOUS );
		comboMappingPreset.setSelectedItem( mappingModel.getPreset() );
		updateStepSizeField();
		labelPaletteKind.setText( ( mappingModel.isDiscrete() ? "Discrete" : "Continuous" )
				+ " \u00b7 " + currentPalette.getLength() + " colors" );
		updateCurveHint();
	}

	/**
	 * Say what the graph is currently offering: how to edit the transfer
	 * function while that is switched on, or -- for a discrete palette, where
	 * the pencil is disabled -- why there is nothing there to edit. Silent
	 * otherwise, since a hint that is always on screen stops being read.
	 */
	private void updateCurveHint()
	{
		if ( mappingModel.isDiscrete() )
			labelCurveHint.setText( "A discrete palette's shape comes from its step size" );
		else if ( panelMappingCurve.isEditMode() )
			labelCurveHint.setText( "Left-click adds or drags a point, right-click removes one" );
		else
			labelCurveHint.setText( "" );
	}

	/**
	 * A boundary's color swatch is only live while that end is set to
	 * {@link BoundaryCondition#SPECIAL}; under the other conditions the color
	 * comes from the palette, so there is nothing to pick.
	 */
	private void updateSpecialColorButtonStates()
	{
		buttonLeftSpecialColor.setEnabled( mappingModel.getLeftBoundaryCondition() == BoundaryCondition.SPECIAL );
		buttonRightSpecialColor.setEnabled( mappingModel.getRightBoundaryCondition() == BoundaryCondition.SPECIAL );
	}

	/**
	 * Ask for one end's {@link BoundaryCondition#SPECIAL} color and store it.
	 * Forced opaque: {@link JColorChooser} has no alpha channel to offer here.
	 */
	private void chooseSpecialColor( final boolean left )
	{
		final JButton button = left ? buttonLeftSpecialColor : buttonRightSpecialColor;
		final Color chosen = JColorChooser.showDialog( this,
				left ? "Color Below Range" : "Color Above Range", button.getBackground() );
		if ( chosen == null )
			return;
		final int argb = 0xff000000 | ( chosen.getRGB() & 0xffffff );
		button.setBackground( new Color( argb, false ) );
		if ( left )
			mappingModel.setLeftSpecialColor( argb );
		else
			mappingModel.setRightSpecialColor( argb );
	}

	/**
	 * Show the step size actually in effect -- the chosen one, or whatever
	 * {@link PaletteWrapperBuilder} resolves {@link LutEditorMapping#AUTO_STEP_SIZE}
	 * to for the current palette and range. Showing the resolved number rather
	 * than an empty field means the user always starts editing from the value
	 * they are actually looking at.
	 */
	private void updateStepSizeField()
	{
		final double stepSize = effectiveStepSize();
		lastShownStepSize = formatValue( stepSize );
		fieldStepSize.setText( lastShownStepSize );
		updateStepCoverageLabel( stepSize );
	}

	/**
	 * Say, under the step size, how far the palette actually reaches: its N
	 * colors at that width cover {@code [min, min + stepSize * N]}, and that
	 * is where the palette runs out and the "above range" condition takes over
	 * -- the same edge the graph marks (see {@link MappingCurvePanel}). It is
	 * deliberately not the display range's maximum: the two part company as
	 * soon as a step size is typed in, and which of them the colors follow is
	 * exactly what is easy to get wrong here.
	 */
	private void updateStepCoverageLabel( final double stepSize )
	{
		final int colors = new DiscreteColorScheme( currentPalette ).getPaletteRangeLength();
		final double end = editedRangeMin + stepSize * colors;
		labelStepCoverage.setText( colors + " colors \u00d7 " + formatValue( stepSize )
				+ " covers " + formatValue( editedRangeMin ) + " \u2013 " + formatValue( end ) );
	}

	/** The step size {@link #mappingModel} currently maps through; see {@link #updateStepSizeField()}. */
	private double effectiveStepSize()
	{
		final double chosen = mappingModel.getStepSize();
		if ( chosen > 0.0 )
			return chosen;
		final double lo = editedRangeMin;
		final double hi = editedRangeMax > editedRangeMin ? editedRangeMax : editedRangeMin + 1;
		return StepPresetFunc.defaultStepSize( lo, hi, new DiscreteColorScheme( currentPalette ).getPaletteRangeLength() );
	}

	/**
	 * Take a hand-typed step size, keeping the last good value if it does not
	 * parse or is not positive. Text we put there ourselves is ignored: the
	 * field shows the <em>resolved</em> value of an automatic step size, so
	 * committing it back on a mere focus traversal would silently pin it down
	 * as an explicit choice -- and leave the editor looking dirty.
	 */
	private void commitStepSizeField()
	{
		if ( loadingControls )
			return;
		final String text = fieldStepSize.getText().trim();
		if ( text.equals( lastShownStepSize ) )
			return;
		try
		{
			final double v = Double.parseDouble( text );
			if ( v > 0.0 )
				mappingModel.setStepSize( v );
		}
		catch ( final NumberFormatException ignored )
		{
		}
		updateStepSizeField();
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
		final Palette palette = LutPalettes.load( preset.getPaletteName() );
		if ( palette == null )
		{
			labelStatus.setText( "Configuration's palette not found: " + preset.getPaletteName() );
			return;
		}

		final LutEditorMapping presetMapping = mappingFromPreset( preset, !palette.isInterpolated() );

		loadIntoEditor( palette, preset.getPaletteName(), presetMapping, editedRangeMin, editedRangeMax );
		pushLiveEdits();
		labelStatus.setText( "" );
	}

	/**
	 * The {@link LutEditorMapping} a saved {@link EditorPreset} describes, given
	 * whether the palette it names is discrete -- shared by
	 * {@link #applyEditorPreset} (which resolves {@code discrete} from the
	 * palette it just loaded) and {@link #matchesEditorPreset} (which resolves
	 * it from {@link #mappingModel}, without re-loading the palette).
	 */
	private static LutEditorMapping mappingFromPreset( final EditorPreset preset, final boolean discrete )
	{
		final LutEditorMapping mapping = new LutEditorMapping();
		mapping.setLeftBoundaryCondition( preset.getLeftBoundaryCondition() );
		mapping.setRightBoundaryCondition( preset.getRightBoundaryCondition() );
		mapping.setLeftSpecialColor( preset.getLeftSpecialColor() );
		mapping.setRightSpecialColor( preset.getRightSpecialColor() );
		mapping.setDiscrete( discrete );
		// Only the half of the saved shape the palette can actually use: a
		// discrete palette maps through the step size and ignores the curve,
		// a continuous one the other way round (see LutEditorMapping).
		if ( discrete )
			mapping.setStepSize( preset.getStepSize() );
		else
			mapping.getCurve().setPoints( preset.getCurveXs(), preset.getCurveYs() );
		return mapping;
	}

	/**
	 * Deselect the configuration combo when it no longer describes what is
	 * actually loaded -- e.g. after switching to a source whose applied
	 * palette/mapping is not the one the previously selected configuration
	 * saves. Called after every {@link #loadIntoEditor}, so the combo cannot
	 * go on claiming a configuration is in effect once the editor state has
	 * moved on from it.
	 */
	private void syncEditorPresetSelection()
	{
		final Object selected = comboEditorPreset.getSelectedItem();
		if ( selected instanceof String && !matchesEditorPreset( ( String ) selected ) )
			comboEditorPreset.setSelectedItem( null );
	}

	/** Whether {@link #currentPalette}/{@link #currentPaletteName} and {@link #mappingModel} are exactly what {@code presetName} saves. */
	private boolean matchesEditorPreset( final String presetName )
	{
		if ( currentPaletteName == null )
			return false;
		final EditorPreset preset = EditorPresets.load( presetName );
		if ( preset == null || !currentPaletteName.equals( preset.getPaletteName() ) )
			return false;
		return mappingModel.hasSameState( mappingFromPreset( preset, mappingModel.isDiscrete() ) );
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
			labelStatus.setText( "Select a named palette before saving a configuration." );
			return;
		}

		final Object selected = comboEditorPreset.getSelectedItem();
		final Object input = JOptionPane.showInputDialog( this, "Configuration name:", "Save Configuration",
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
			labelStatus.setText( "Configuration name cannot be empty." );
			return;
		}
		if ( EditorPresets.discoverNames().contains( name ) )
		{
			final int choice = JOptionPane.showConfirmDialog( this,
					"A configuration named \"" + name + "\" already exists. Overwrite it?", "Overwrite Configuration",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
			if ( choice != JOptionPane.YES_OPTION )
				return;
		}

		try
		{
			EditorPresets.save( new EditorPreset( name, currentPaletteName,
					mappingModel.getLeftBoundaryCondition(), mappingModel.getRightBoundaryCondition(),
					mappingModel.getLeftSpecialColor(), mappingModel.getRightSpecialColor(),
					mappingModel.getStepSize(),
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
		labelStatus.setText( "Saved configuration \"" + name + "\"." );
	}

	/**
	 * Put the session's backup back: whatever the source looked like when
	 * {@link #beginSession} bound it to this window, or when "Apply" last
	 * moved that baseline forward. Unlike "Cancel" this leaves the window
	 * open, which is the point of it -- the dialog is not modal and is meant
	 * to be kept around while trying things out.
	 */
	private void resetToSessionBaseline()
	{
		revertLiveEdits();
		labelStatus.setText( activeLutConv == null ? "" : "Reset." );
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
		pushToActiveConverter( currentPalette, currentPaletteName, mappingModel, editedRangeMin, editedRangeMax );
	}

	/** Push {@link #baselinePalette}/{@link #baselineMapping}/{@link #baselineRangeMin}/{@link #baselineRangeMax} to {@link #activeLutConv}, discarding any live-pushed edits made since. */
	private void revertActiveConverterToBaseline()
	{
		pushToActiveConverter( baselinePalette, baselinePaletteName, baselineMapping, baselineRangeMin, baselineRangeMax );
	}

	/**
	 * Translate the editor's palette + mapping + range into a
	 * {@link PaletteWrapper} and hand it to {@link #activeLutConv} to render
	 * through, remembering the editor-facing terms in {@link #converterStates}
	 * so re-selecting this source can restore them. The display range still
	 * goes to the setup (which also drives brightness/contrast), so it stays
	 * the single owner of that range.
	 * <p>
	 * The same wrapper instance also goes to
	 * {@link #activeVolatileLutConv}, which is what renders the source until
	 * its data has finished loading; it can be shared because the two
	 * converters describe the same mapping of the same pixels.
	 */
	private void pushToActiveConverter( final Palette palette, final String paletteName, final LutEditorMapping mapping, final double min, final double max )
	{
		if ( activeLutConv == null )
			return;
		final PaletteWrapper wrapper = PaletteWrapperBuilder.build( palette, mapping, min, max );
		activeLutConv.setWrapper( wrapper );
		if ( activeVolatileLutConv != null )
			activeVolatileLutConv.setWrapper( wrapper );

		final LutEditorMapping remembered = new LutEditorMapping();
		remembered.copyFrom( mapping );
		converterStates.put( activeLutConv, new EditorState( palette, paletteName, remembered ) );

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

	/**
	 * The help text, in a box of its own rather than handed straight to a
	 * {@link JOptionPane}: passed as a string it becomes a stack of labels as
	 * tall as the text, which by now is taller than the screen. A fixed height
	 * with a scroll bar keeps the window a sensible size however much the text
	 * grows.
	 */
	private void showHelp()
	{
		final String message = String.join( "\n",
				"LUT Editor help:",
				"",
				"Configuration (the strip across the top):",
				"- Applies a saved combination of palette, boundary handling and transfer",
				"  function (see EditorPreset) -- built-in ones ship with the app, and",
				"  \"Save as...\" stores the current combination (under a name you choose)",
				"  for reuse later, next to the built-in ones under \"My Settings\".",
				"  Applying one leaves the current input value range alone, since that is",
				"  specific to whatever source's data you are editing, not part of the",
				"  saved look.",
				"",
				"Data:",
				"- Color palette selects the LUT colors the mapped value is looked up in.",
				"- Whether those colors blend smoothly or are used as individually chosen",
				"  colors follows the chosen palette file's own declared kind, not a",
				"  setting here. The line under the swatch says which one it is, and it",
				"  decides which shape control you get.",
				"",
				"Function:",
				"- For a smooth (continuous) palette, Preset replaces the transfer",
				"  function with a predefined shape (Linear, Percentile Stretch, Log, Exp,",
				"  Sigmoid, \u03b1-Sigmoid, Tan, Atan). It can still be adjusted afterwards,",
				"  and Invert flips it vertically on top of whatever shape/edits it has.",
				"- For a discrete (categorical) palette, Step size replaces it: it is how",
				"  many input values one color covers. Set it to 1 to give every integer",
				"  label its own color. The line below it says how far the palette reaches",
				"  at that step size -- past there, the \"above range\" condition takes over,",
				"  so a Cycle there is what repeats the palette across the rest of the",
				"  range. The field starts out showing the step size that spreads the",
				"  palette exactly once.",
				"",
				"Mapping:",
				"- Below range / Above range choose what happens to input values past that",
				"  end of the range, independently:",
				"    Clamp       holds that end's palette color.",
				"    Cycle       wraps back around, so the palette repeats.",
				"    Fixed color paints one chosen color instead of any palette color --",
				"                e.g. a dedicated background for a label image's 0. Click",
				"                the swatch beside the dropdown to pick it.",
				"",
				"Transfer function (the graph):",
				"- The color bar to the left previews the palette itself; the one below the",
				"  graph (\"after transform\") previews the color actually produced for each",
				"  input value.",
				"- The boxes at the left/right ends of the x axis set the input value range.",
				"- Click the pencil beside the graph's top-right corner to show and edit",
				"  the control points: left-click to add or drag a point, right-click a",
				"  point to remove it. Hovering or dragging one shows its input value and",
				"  the palette color it maps to, with guides running out to both color",
				"  bars. The pencil is disabled for a discrete palette, whose shape comes",
				"  from its step size rather than from a curve.",
				"- For a discrete palette the line is drawn as the straight ramp it really",
				"  is -- snapping to a color is the palette's doing, not the function's.",
				"  The grid it crosses is the palette's own stops, a tick under the color",
				"  bar marks every color change, and the dashed vertical marks where the",
				"  palette runs out.",
				"- Past either end of the palette's domain the line is dashed, because",
				"  there its shape is the boundary condition's doing.",
				"",
				"- Edits here take effect in the viewer immediately, as you make them.",
				"- Apply keeps the current edits as the new fallback to revert to.",
				"- Cancel (or closing the dialog, or switching source without Apply)",
				"  reverts to that fallback, discarding edits made since.",
				"",
				"Shortcut:",
				"- Press F1 anywhere in this dialog to open this help." );

		final JTextArea text = new JTextArea( message );
		text.setEditable( false );
		// Monospaced on purpose: the text is hand-wrapped, and the boundary
		// conditions are laid out in columns that only line up in a fixed-width
		// font. Sized from the look and feel's own label font, so it still
		// follows a UI scale change.
		final Font labelFont = UIManager.getFont( "Label.font" );
		text.setFont( new Font( Font.MONOSPACED, Font.PLAIN, labelFont != null ? labelFont.getSize() : 12 ) );
		text.setBackground( UIManager.getColor( "Panel.background" ) );
		text.setBorder( new EmptyBorder( 4, 6, 4, 6 ) );
		text.setCaretPosition( 0 );

		final JScrollPane scroll = new JScrollPane( text );
		// Wide enough for the longest line, so that only the vertical bar is
		// ever needed -- but capped, since one stray long line should not push
		// the window off the side of the screen.
		final int width = Math.min( text.getPreferredSize().width + 24, MAX_HELP_WIDTH );
		scroll.setPreferredSize( new Dimension( width, HELP_HEIGHT ) );
		scroll.getVerticalScrollBar().setUnitIncrement( 16 );

		JOptionPane.showMessageDialog( this, scroll, "LUT Editor Help", JOptionPane.INFORMATION_MESSAGE );
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
	 * A preview panel showing a color table as a horizontal bar, rendered
	 * through the {@link ColorScheme} it maps to: a categorical (non-interpolated)
	 * palette shows discrete color bands, a continuous one a smooth gradient.
	 */
	private static class GradientPreviewPanel extends JPanel
	{
		private Palette palette = Palette.DEFAULT;

		public GradientPreviewPanel()
		{
			setPreferredSize( new Dimension( 300, 16 ) );
		}

		public void update( final Palette palette )
		{
			this.palette = palette;
			repaint();
		}

		@Override
		protected void paintComponent( final Graphics g )
		{
			super.paintComponent( g );

			final int w = getWidth();
			final int h = getHeight();

			if ( palette != null )
			{
				final ColorScheme scheme = palette.isInterpolated()
						? new ContinuousColorScheme( palette )
						: new DiscreteColorScheme( palette );
				final int paletteRangeLength = scheme.getPaletteRangeLength();
				for ( int i = 0; i < w; i++ )
				{
					final double t = w > 1 ? i / ( double ) ( w - 1 ) : 0.0;
					g.setColor( new Color( scheme.getRGB( t * paletteRangeLength ) ) );
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
