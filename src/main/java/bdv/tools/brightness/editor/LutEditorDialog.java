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
package bdv.tools.brightness.editor;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.ConverterSetup.SetupChangeListener;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.converter.BoundaryCondition;
import bdv.tools.brightness.converter.ColorSchemeConverter;
import bdv.tools.brightness.converter.ColorSchemeConverterFactory;
import bdv.tools.brightness.converter.IColorSchemeWrapper;
import bdv.tools.brightness.colorscheme.IColorScheme;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.colorscheme.LegacyBdvColorScheme;
import bdv.tools.brightness.colorscheme.ColorSchemeCategoryHelper;
import bdv.tools.brightness.colorscheme.ColorSchemeFactory;
import bdv.tools.brightness.presetfunc.StepPresetFunc;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;

/**
 * A non-modal LUT editor: a configuration strip ({@link EditorPreset}) over a
 * settings column (color scheme, shape, {@link BoundaryCondition}s) and the
 * {@link MappingCurvePanel} graph.
 * <p>
 * It edits the viewer's current source (see {@link #beginSession}). Edits are
 * pushed live ({@link #pushLiveEdits()}) and kept on close; "Reset" restores
 * the session's baseline.
 */
public class LutEditorDialog extends JDialog {
    private final ConverterSetups converterSetups;
    private final ViewerState viewerState;
    /**
     * Never {@code null}.
     */
    private final Runnable repaintAction;

    /**
     * {@code CURRENT_SOURCE_CHANGED} also fires when sources are added,
     * removed or cleared. Unregistered in {@link #dispose()} because the
     * {@code ViewerState} outlives this dialog.
     */
    private final ViewerStateChangeListener viewerStateListener = change ->
    {
        if (change == ViewerStateChange.CURRENT_SOURCE_CHANGED) {
            SwingUtilities.invokeLater(this::syncToCurrentSource);
        }
    };

    /**
     * Listens to all setups, not just {@link #activeSetup}, so it need not move
     * between sessions or miss one put in place by {@link #repointConverterSetup}.
     */
    private final SetupChangeListener setupChangeListener = setup ->
            SwingUtilities.invokeLater(() -> followDisplayRange(setup));

    private final JComboBox<Object> comboColorScheme;
    private final JComboBox<Object> comboEditorPreset;
    private final JButton buttonSaveEditorPreset;
    private final JLabel labelStatus;

    private final JLabel labelSchemeKind;

    private final JLabel labelStepCoverage;

    private final JLabel labelCurveHint;

    private final GradientPreviewPanel panelSchemeSwatch;
    private final MappingCurvePanel panelMappingCurve;

    private final JComboBox<BoundaryCondition> comboLeftBoundary;
    private final JComboBox<BoundaryCondition> comboRightBoundary;
    private final JButton buttonLeftSpecialColor;
    private final JButton buttonRightSpecialColor;

    /**
     * Continuous schemes are shaped by a curve, discrete ones by a step size.
     */
    private final JPanel panelShape;
    private final CardLayout layoutShape = new CardLayout();
    private static final String SHAPE_CARD_CONTINUOUS = "continuous";
    private static final String SHAPE_CARD_DISCRETE = "discrete";

    private static final int HELP_HEIGHT = 420;

    private static final int MAX_HELP_WIDTH = 720;

    private final JComboBox<PresetShape> comboMappingPreset;
    private final JButton buttonInvertCurve;
    private final JTextField fieldStepSize;

    private IColorScheme currentScheme = ContinuousColorScheme.DEFAULT;

    /**
     * {@code null} if not known to be listed in {@link #comboColorScheme}.
     */
    private String currentSchemeName = null;

    /**
     * Listed schemes that are not bundled resources, so cannot be reloaded by name.
     */
    private final Map<String, IColorScheme> addedSchemes = new HashMap<>();

    private static final String LEGACY_SCHEME_CATEGORY = "Legacy BDV Colors";

    /**
     * Label images use 0 as background, so it falls below the range.
     */
    private static final double DISCRETE_DEFAULT_RANGE_MIN = 1;

    /**
     * One color per label id.
     */
    private static final double DISCRETE_DEFAULT_STEP_SIZE = 1;

    private final LutEditorMapping mappingModel = new LutEditorMapping();

    private double editedRangeMin = 0;
    private double editedRangeMax = 255;

    /**
     * {@code null} if there is no session.
     */
    private SourceAndConverter<?> sessionSource = null;

    /**
     * {@code null} if nothing is editable.
     */
    private ColorSchemeConverter<?> activeConverter = null;

    /**
     * Edited alongside {@link #activeConverter} so loading data shows the new colors too.
     */
    private ColorSchemeConverter<?> activeVolatileConverter = null;

    private ConverterSetup activeSetup = null;

    /**
     * The editor state last pushed to each converter, which only keeps the
     * derived wrapper. Weakly keyed. The stored range is never read back; the
     * setup owns it.
     */
    private final Map<ColorSchemeConverter<?>, EditorState> converterStates = new WeakHashMap<>();

    /**
     * Immutable snapshot; see {@link #captureEditorState()}.
     */
    private static final class EditorState {
        final IColorScheme scheme;
        final String schemeName;
        final LutEditorMapping mapping;
        final double rangeMin;
        final double rangeMax;

        EditorState(final IColorScheme scheme, final String schemeName, final LutEditorMapping mapping, final double rangeMin, final double rangeMax) {
            this.scheme = scheme;
            this.schemeName = schemeName;
            this.mapping = mapping;
            this.rangeMin = rangeMin;
            this.rangeMax = rangeMax;
        }
    }

    /**
     * Shown when there is nothing better; safe to share since states are only read.
     */
    private static final EditorState NEUTRAL_STATE =
            new EditorState(ContinuousColorScheme.DEFAULT, ColorSchemeFactory.findName(ContinuousColorScheme.DEFAULT), defaultMapping(), 0, 255);

    /**
     * What "Reset" restores; taken by {@link #beginSession}.
     */
    private EditorState baseline;

    /**
     * Set only by {@link #withoutFeedback}.
     */
    private boolean loadingControls = false;

    /**
     * See {@link #commitStepSizeField()}.
     */
    private String lastShownStepSize = "";

    public LutEditorDialog(final Frame owner, final ConverterSetups converterSetups, final ViewerState viewerState, final Runnable repaintAction) {
        super(owner, "LUT Editor", false);
        this.converterSetups = converterSetups;
        this.viewerState = viewerState;
        this.repaintAction = repaintAction != null ? repaintAction : () -> {
        };

        // -- Widgets ---------------------------------------------------------
        comboColorScheme = createColorSchemeCombo();
        comboEditorPreset = createEditorPresetCombo();
        buttonSaveEditorPreset = new JButton("Save as...");
        buttonSaveEditorPreset.setFocusable(false);
        panelSchemeSwatch = new GradientPreviewPanel();
        panelSchemeSwatch.setPreferredSize(new Dimension(200, 16));
        panelSchemeSwatch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

        comboLeftBoundary = createBoundaryCombo();
        comboRightBoundary = createBoundaryCombo();
        buttonLeftSpecialColor = createSpecialColorButton("Color for values below the range", LutEditorMapping.DEFAULT_LEFT_SPECIAL_COLOR);
        buttonRightSpecialColor = createSpecialColorButton("Color for values above the range", LutEditorMapping.DEFAULT_RIGHT_SPECIAL_COLOR);
        comboMappingPreset = new JComboBox<>(PresetShape.values());
        buttonInvertCurve = new JButton("Invert");
        buttonInvertCurve.setFocusable(false);
        fieldStepSize = new JTextField();
        panelShape = heightCapped(layoutShape);

        panelMappingCurve = new MappingCurvePanel(mappingModel);
        panelMappingCurve.setRangeChangeListener((min, max) ->
        {
            editedRangeMin = min;
            editedRangeMax = max;
            // an automatic step size depends on the range
            updateStepSizeField();
            pushLiveEdits();
        });

        labelStatus = new JLabel("");
        labelSchemeKind = mutedLabel("");
        labelStepCoverage = mutedLabel("");
        labelCurveHint = mutedLabel("");

        panelMappingCurve.setEditModeListener(editing -> updateCurveHint());

        // -- Layout ----------------------------------------------------------
        setLayout(new BorderLayout(0, 4));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));

        final JPanel panelLeftColumn = createLeftColumn();
        final JPanel panelMappingCurveColumn = createMappingCurveColumn();

        final JPanel panelCenter = new JPanel(new BorderLayout(12, 0));
        panelCenter.add(panelLeftColumn, BorderLayout.WEST);
        panelCenter.add(hugContents(panelMappingCurveColumn), BorderLayout.CENTER);
        add(createConfigurationStrip(), BorderLayout.NORTH);
        add(panelCenter, BorderLayout.CENTER);
        add(createBottomBar(), BorderLayout.SOUTH);

        // -- Behavior --------------------------------------------------------
        installControlListeners();
        viewerState.changeListeners().add(viewerStateListener);
        converterSetups.listeners().add(setupChangeListener);
        // pack() needs filled-in controls; sessions only begin in setVisible
        loadIntoEditor(NEUTRAL_STATE);
        packAndMatchGraphWidth(panelLeftColumn, panelMappingCurveColumn);
    }

    // -- Window lifecycle and following the viewer -------------------------

    /**
     * Hiding keeps the live edits. Showing starts a new session with a fresh
     * baseline; only then, because starting one may convert the source (see
     * {@link #convertToColorScheme}), which should not happen unseen.
     */
    @Override
    public void setVisible(final boolean visible) {
        final boolean showing = visible && !isVisible();
        super.setVisible(visible);
        if (showing) {
            beginSession(viewerState.getCurrentSource());
        }

        // TODO: Set null params
    }

    /**
     * Teardown, not hiding. Already-queued {@link #syncToCurrentSource()} calls
     * are stopped by the window being hidden, which {@code Window.dispose()}
     * does synchronously.
     */
    @Override
    public void dispose() {
        viewerState.changeListeners().remove(viewerStateListener);
        converterSetups.listeners().remove(setupChangeListener);
        super.dispose();
    }

    /**
     * No-op if the source is unchanged or the window is hidden (including disposed).
     */
    private void syncToCurrentSource() {
        if (!isVisible()) {
            return;
        }
        final SourceAndConverter<?> current = viewerState.getCurrentSource();
        if (current != sessionSource) {
            beginSession(current);
        }
    }

    // -- Editing session ---------------------------------------------------

    /**
     * Bind to {@code soc}, load its state and take the {@link #baseline}. Other
     * converters are converted without asking (see {@link #convertToColorScheme});
     * if that fails the editor shows the neutral state, pushed nowhere. Only
     * called while showing.
     */
    private void beginSession(final SourceAndConverter<?> soc) {
        sessionSource = soc;
        updateTitle();

        final Converter<?, ?> original = soc == null ? null : soc.getConverter();
        activeConverter = soc == null ? null : editableConverterOf(soc);
        final boolean editable = activeConverter != null;
        activeVolatileConverter = editable ? volatileConverterOf(soc) : null;
        activeSetup = editable ? converterSetups.getConverterSetup(soc) : null;

        if (soc == null) {
            labelStatus.setText("no setup selected");
        } else if (!editable) {
            labelStatus.setText(converterKind(original) + " does not use a LUT and cannot be converted.");
        } else if (activeConverter != original) {
            labelStatus.setText("Converted " + converterKind(original) + " to color scheme \"" + converterStates.get(activeConverter).schemeName + "\".");
        } else {
            labelStatus.setText("");
        }

        loadIntoEditor(initialStateFor(activeConverter, activeSetup));
        baseline = captureEditorState();
    }

    /**
     * {@code null} if it is not and cannot be converted to a {@link ColorSchemeConverter}.
     */
    private ColorSchemeConverter<?> editableConverterOf(final SourceAndConverter<?> soc) {
        final ColorSchemeConverter<?> converter = asColorSchemeConverter(soc.getConverter());
        return converter != null ? converter : convertToColorScheme(soc);
    }

    private static ColorSchemeConverter<?> volatileConverterOf(final SourceAndConverter<?> soc) {
        return soc.asVolatile() != null ? asColorSchemeConverter(soc.asVolatile().getConverter()) : null;
    }

    /**
     * The remembered state (see {@link #converterStates}) or the neutral one,
     * with the range read fresh from {@code setup}.
     */
    private EditorState initialStateFor(final ColorSchemeConverter<?> converter, final ConverterSetup setup) {
        final EditorState remembered = converter != null ? converterStates.get(converter) : null;
        final EditorState look = remembered != null ? remembered : NEUTRAL_STATE;
        final double min = setup != null ? setup.getDisplayRangeMin() : NEUTRAL_STATE.rangeMin;
        final double max = setup != null ? setup.getDisplayRangeMax() : NEUTRAL_STATE.rangeMax;
        return new EditorState(look.scheme, look.schemeName, look.mapping, min, max);
    }

    private static ColorSchemeConverter<?> asColorSchemeConverter(final Converter<?, ?> converter) {
        return converter instanceof ColorSchemeConverter ? (ColorSchemeConverter<?>) converter : null;
    }

    /**
     * Converts via {@link ColorSchemeConverterFactory}; not asked first, since the
     * image looks the same. The scheme is listed and remembered so the editor
     * and Reset show the source's own color, with a clamped mapping (not
     * {@link #defaultMapping()}, whose fixed colors would paint saturated pixels
     * white). {@code null} if it cannot be converted.
     */
    private ColorSchemeConverter<?> convertToColorScheme(final SourceAndConverter<?> soc) {
        if (!ColorSchemeConverterFactory.canApproximate(soc)) {
            return null;
        }
        final LegacyBdvColorScheme scheme = ColorSchemeConverterFactory.schemeFor((ColorConverter) soc.getConverter());
        final ColorSchemeConverter<?> converted = ColorSchemeConverterFactory.approximateInPlace(soc);
        if (converted == null) {
            return null;
        }

        final String name = legacySchemeName(scheme);
        addColorScheme(LEGACY_SCHEME_CATEGORY, name, scheme);
        final LutEditorMapping mapping = defaultMapping();
        mapping.setLeftBoundaryCondition(BoundaryCondition.CLAMP);
        mapping.setRightBoundaryCondition(BoundaryCondition.CLAMP);
        converterStates.put(converted, new EditorState(scheme, name, mapping, converted.getMin(), converted.getMax()));

        repointConverterSetup(soc);
        repaintAction.run();
        return converted;
    }

    /**
     * {@code #RRGGBB}, or {@code #AARRGGBB} if not opaque; sources sharing a color share an entry.
     */
    static String legacySchemeName(final LegacyBdvColorScheme scheme) {
        final int color = scheme.getColor();
        return ARGBType.alpha(color) == 255
                ? String.format("BDV #%06X", color & 0xffffff)
                : String.format("BDV #%08X", color);
    }

    /**
     * Not {@code Class.getSimpleName()}: imglib2's {@code ClassCopyProvider}
     * copies of {@code RealARGBColorConverter} throw {@code IllegalAccessError}
     * for it.
     */
    static String converterKind(final Converter<?, ?> converter) {
        if (converter == null) {
            return "No converter";
        }
        if (converter instanceof RealARGBColorConverter) {
            return RealARGBColorConverter.class.getSimpleName();
        }
        final String name = converter.getClass().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * In place where possible, since {@link bdv.tools.brightness.SetupAssignments} and others hold
     * on to the setup. Otherwise it is replaced, and {@code SetupAssignments}
     * groups will not follow.
     */
    private void repointConverterSetup(final SourceAndConverter<?> soc) {
        final ConverterSetup setup = converterSetups.getConverterSetup(soc);
        if (setup == null) {
            return;
        }
        final List<ColorConverter> converters = ColorSchemeConverterFactory.colorConvertersOf(soc);
        if (converters.isEmpty()) {
            return;
        }
        if (setup instanceof RealARGBColorConverterSetup) {
            ((RealARGBColorConverterSetup) setup).setConverters(converters);
        } else {
            converterSetups.put(soc, new RealARGBColorConverterSetup(setup.getSetupId(), converters));
        }
    }

    private String sourceName(final SourceAndConverter<?> soc) {
        if (soc.getSpimSource() != null) {
            return soc.getSpimSource().getName();
        }
        final ConverterSetup setup = converterSetups.getConverterSetup(soc);
        return setup != null ? Integer.toString(setup.getSetupId()) : "?";
    }

    private void updateTitle() {
        setTitle(sessionSource == null ? "LUT Editor" : "LUT Editor - " + sourceName(sessionSource));
    }

    /**
     * Matches {@code BigDataViewer.createConverterToARGB}, so opening the editor changes nothing.
     */
    private static LutEditorMapping defaultMapping() {
        final LutEditorMapping defaults = new LutEditorMapping();
        defaults.setLeftBoundaryCondition(BoundaryCondition.SPECIAL);
        defaults.setRightBoundaryCondition(BoundaryCondition.SPECIAL);
        defaults.setLeftSpecialColor(LutEditorMapping.DEFAULT_LEFT_SPECIAL_COLOR);
        defaults.setRightSpecialColor(LutEditorMapping.DEFAULT_RIGHT_SPECIAL_COLOR);
        defaults.applyPreset(PresetShape.LINEAR);
        return defaults;
    }

    // -- Editor state and live edits ---------------------------------------

    /**
     * Inverse of {@link #captureEditorState()}; does not push. The mapping is copied.
     */
    private void loadIntoEditor(final EditorState state) {
        withoutFeedback(() ->
        {
            setColorScheme(state.scheme, state.schemeName);
            comboColorScheme.setSelectedItem(state.schemeName);
            editedRangeMin = state.rangeMin;
            editedRangeMax = state.rangeMax;
            panelMappingCurve.setRange(state.rangeMin, state.rangeMax);

            mappingModel.copyFrom(state.mapping);

            comboLeftBoundary.setSelectedItem(mappingModel.getLeftBoundaryCondition());
            comboRightBoundary.setSelectedItem(mappingModel.getRightBoundaryCondition());
            buttonLeftSpecialColor.setBackground(new Color(mappingModel.getLeftSpecialColor(), false));
            buttonRightSpecialColor.setBackground(new Color(mappingModel.getRightSpecialColor(), false));
            updateSpecialColorButtonStates();
            updateShapeControls();
            syncEditorPresetSelection();
        });
    }

    /**
     * Mutes control listeners, which cannot tell a programmatic change from a click. Nests.
     */
    private void withoutFeedback(final Runnable update) {
        final boolean outer = loadingControls;
        loadingControls = true;
        try {
            update.run();
        } finally {
            loadingControls = outer;
        }
    }

    /**
     * Leaves {@link #comboColorScheme} alone; {@link #selectColorScheme} calls this too.
     */
    private void setColorScheme(final IColorScheme scheme, final String schemeName) {
        currentScheme = scheme;
        currentSchemeName = schemeName;
        panelSchemeSwatch.update(scheme);
        panelMappingCurve.setColorScheme(scheme);
    }

    /**
     * Going from continuous to discrete resets the range minimum and step size
     * to the discrete defaults; between two discrete schemes they are kept.
     */
    private void selectColorScheme(final String name) {
        final IColorScheme scheme = resolveScheme(name);
        if (scheme == null) {
            labelStatus.setText("Failed to load LUT: " + name);
            return;
        }
        final boolean wasDiscrete = isDiscrete();
        setColorScheme(scheme, name);
        labelStatus.setText("");

        withoutFeedback(() ->
        {
            if (isDiscrete() && !wasDiscrete) {
                editedRangeMin = DISCRETE_DEFAULT_RANGE_MIN;
                // keep the range non-empty, ending where the scheme runs out
                if (editedRangeMax <= editedRangeMin) {
                    editedRangeMax = editedRangeMin + DISCRETE_DEFAULT_STEP_SIZE * scheme.getRange();
                }
                panelMappingCurve.setRange(editedRangeMin, editedRangeMax);
                mappingModel.setStepSize(DISCRETE_DEFAULT_STEP_SIZE);
            }
            // unused while discrete; kept linear so a saved configuration can match
            if (isDiscrete()) {
                mappingModel.applyPreset(PresetShape.LINEAR);
            }
        });
        pushLiveEdits();
        syncEditorPresetSelection();
        updateShapeControls();
    }

    private boolean isDiscrete() {
        return currentScheme instanceof DiscreteColorScheme;
    }

    /**
     * Append {@code scheme} to {@code category} in the chooser, creating the
     * category at the end if needed. Re-adding an equal scheme is a no-op.
     * Does not select it. Call on the EDT.
     *
     * @throws IllegalArgumentException if {@code name} already names a different scheme.
     */
    public void addColorScheme(final String category, final String name, final IColorScheme scheme) {
        final IColorScheme existing = resolveScheme(name);
        if (existing != null) {
            if (existing.equals(scheme)) {
                return;
            }
            throw new IllegalArgumentException("scheme name \"" + name + "\" already names a different scheme");
        }
        addedSchemes.put(name, scheme);

        final GroupedComboModel model = (GroupedComboModel) comboColorScheme.getModel();
        withoutFeedback(() ->
        {
            int header = indexOfCategory(model, category);
            if (header < 0) {
                model.addElement(new CategoryHeader(category));
                header = model.getSize() - 1;
            }
            int end = header + 1;
            while (end < model.getSize() && !(model.getElementAt(end) instanceof CategoryHeader)) {
                end++;
            }
            model.insertElementAt(name, end);
        });
    }

    private static int indexOfCategory(final GroupedComboModel model, final String category) {
        for (int i = 0; i < model.getSize(); i++) {
            final Object item = model.getElementAt(i);
            if (item instanceof CategoryHeader && item.toString().equals(category)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * An added scheme, else the bundled resource; {@code null} if neither.
     */
    private IColorScheme resolveScheme(final String name) {
        final IColorScheme added = addedSchemes.get(name);
        return added != null ? added : ColorSchemeFactory.load(name);
    }

    /**
     * For tests.
     */
    IColorScheme getCurrentColorScheme() {
        return currentScheme;
    }

    /**
     * For tests.
     */
    double getEditedRangeMin() {
        return editedRangeMin;
    }

    /**
     * For tests.
     */
    double getEditedRangeMax() {
        return editedRangeMax;
    }

    /**
     * For tests.
     */
    LutEditorMapping getMapping() {
        return mappingModel;
    }

    /**
     * For tests.
     */
    double getStepSize() {
        return mappingModel.getStepSize();
    }

    /**
     * For tests.
     */
    JComboBox<Object> getColorSchemeCombo() {
        return comboColorScheme;
    }

    private EditorState captureEditorState() {
        final LutEditorMapping mapping = new LutEditorMapping();
        mapping.copyFrom(mappingModel);
        return new EditorState(currentScheme, currentSchemeName, mapping, editedRangeMin, editedRangeMax);
    }

    private void resetToSessionBaseline() {
        loadIntoEditor(baseline);
        pushLiveEdits();
        labelStatus.setText(activeConverter == null ? "" : "Reset.");
    }

    /**
     * Build a wrapper from the editor state and give it to both converters,
     * remember the state in {@link #converterStates}, and set the display range
     * on the setup, which owns it. {@link #mappingModel}'s change listener; also
     * called on range changes, which the model does not track.
     */
    private void pushLiveEdits() {
        if (loadingControls || activeConverter == null) {
            return;
        }
        final IColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build(currentScheme, mappingModel, editedRangeMin, editedRangeMax);
        activeConverter.setWrapper(wrapper);
        if (activeVolatileConverter != null) {
            activeVolatileConverter.setWrapper(wrapper);
        }

        converterStates.put(activeConverter, captureEditorState());

        if (activeSetup != null) {
            activeSetup.setDisplayRange(editedRangeMin, editedRangeMax);
        }
        repaintAction.run();
    }

    /**
     * Adopt a display range changed outside this window. Re-pushed, not just
     * shown, because an automatic step size is only resolved when a wrapper is
     * built. Read from the setup now, so stale or self-caused notifications
     * stop at the equality check. Ignores {@code max <= min} and a hidden window.
     */
    private void followDisplayRange(final ConverterSetup setup) {
        if (!isVisible() || setup != activeSetup) {
            return;
        }
        final double min = setup.getDisplayRangeMin();
        final double max = setup.getDisplayRangeMax();
        if (max <= min || (min == editedRangeMin && max == editedRangeMax)) {
            return;
        }
        editedRangeMin = min;
        editedRangeMax = max;
        panelMappingCurve.setRange(min, max);
        updateStepSizeField();
        pushLiveEdits();
    }

    // -- Configurations (saved presets) ------------------------------------

    /**
     * Keeps the edited range; a preset is not tied to a source's data.
     */
    private void applyEditorPreset(final String name) {
        final EditorPreset preset = EditorPresetHelper.load(name);
        if (preset == null) {
            labelStatus.setText("Failed to load configuration: " + name);
            return;
        }
        final IColorScheme scheme = resolveScheme(preset.getColorSchemeName());
        if (scheme == null) {
            labelStatus.setText("Configuration's color scheme not found: " + preset.getColorSchemeName());
            return;
        }

        final LutEditorMapping presetMapping = mappingFromPreset(preset, scheme instanceof DiscreteColorScheme);

        loadIntoEditor(new EditorState(scheme, preset.getColorSchemeName(), presetMapping, editedRangeMin, editedRangeMax));
        pushLiveEdits();
        labelStatus.setText("");
    }

    private static LutEditorMapping mappingFromPreset(final EditorPreset preset, final boolean discrete) {
        final LutEditorMapping mapping = new LutEditorMapping();
        mapping.setLeftBoundaryCondition(preset.getLeftBoundaryCondition());
        mapping.setRightBoundaryCondition(preset.getRightBoundaryCondition());
        mapping.setLeftSpecialColor(preset.getLeftSpecialColor());
        mapping.setRightSpecialColor(preset.getRightSpecialColor());
        if (discrete) {
            mapping.setStepSize(preset.getStepSize());
        } else {
            mapping.getCurve().setPoints(preset.getCurveXs(), preset.getCurveYs());
        }
        return mapping;
    }

    /**
     * Deselect the configuration once the editor no longer matches it. Compared
     * rather than cleared, so a no-op pick keeps it; never re-selects.
     */
    private void syncEditorPresetSelection() {
        final Object selected = comboEditorPreset.getSelectedItem();
        if (selected instanceof String && !matchesEditorPreset((String) selected)) {
            comboEditorPreset.setSelectedItem(null);
        }
    }

    private boolean matchesEditorPreset(final String presetName) {
        if (currentSchemeName == null) {
            return false;
        }
        final EditorPreset preset = EditorPresetHelper.load(presetName);
        if (preset == null || !currentSchemeName.equals(preset.getColorSchemeName())) {
            return false;
        }
        return mappingModel.hasSameState(mappingFromPreset(preset, isDiscrete()));
    }

    private void promptAndSaveEditorPreset() {
        if (currentSchemeName == null) {
            labelStatus.setText("Select a named color scheme before saving a configuration.");
            return;
        }

        final Object selected = comboEditorPreset.getSelectedItem();
        final Object input = JOptionPane.showInputDialog(this, "Configuration name:", "Save Configuration",
                JOptionPane.PLAIN_MESSAGE, null, null, selected instanceof String ? selected : "");
        if (input == null) {
            return;
        }
        // canonical before comparing with discoverNames()
        final String name = EditorPresetHelper.canonicalName((String) input);
        if (name.isEmpty()) {
            labelStatus.setText("Configuration name cannot be empty.");
            return;
        }
        if (EditorPresetHelper.discoverNames().contains(name)) {
            final int choice = JOptionPane.showConfirmDialog(this,
                    "A configuration named \"" + name + "\" already exists. Overwrite it?", "Overwrite Configuration",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            EditorPresetHelper.save(new EditorPreset(name, currentSchemeName,
                    mappingModel.getLeftBoundaryCondition(), mappingModel.getRightBoundaryCondition(),
                    mappingModel.getLeftSpecialColor(), mappingModel.getRightSpecialColor(),
                    mappingModel.getStepSize(),
                    mappingModel.getCurve().xsArray(), mappingModel.getCurve().ysArray()));
        } catch (final RuntimeException e) {
            labelStatus.setText("Failed to save configuration: " + e.getMessage());
            return;
        }

        withoutFeedback(() ->
        {
            refreshEditorPresetCombo(comboEditorPreset);
            comboEditorPreset.setSelectedItem(name);
        });
        labelStatus.setText("Saved configuration \"" + name + "\".");
    }

    // -- Syncing controls to the model -------------------------------------

    /**
     * Muted: the shape combo's listener would re-seed the curve, discarding hand edits.
     */
    private void updateShapeControls() {
        withoutFeedback(() ->
        {
            layoutShape.show(panelShape, isDiscrete() ? SHAPE_CARD_DISCRETE : SHAPE_CARD_CONTINUOUS);
            comboMappingPreset.setSelectedItem(mappingModel.getPreset());
            updateStepSizeField();
            labelSchemeKind.setText((isDiscrete() ? "Discrete" : "Continuous")
                    + " \u00b7 " + currentScheme.getFixCount() + (isDiscrete() ? " colors" : " color fixes"));
            updateCurveHint();
        });
    }

    /**
     * Empty unless editing or discrete; an always-on hint stops being read.
     */
    private void updateCurveHint() {
        if (isDiscrete()) {
            labelCurveHint.setText("A discrete color scheme's shape comes from its step size");
        } else if (panelMappingCurve.isEditMode()) {
            labelCurveHint.setText("Left-click adds or drags a point, right-click removes one");
        } else {
            labelCurveHint.setText("");
        }
    }

    private void updateSpecialColorButtonStates() {
        buttonLeftSpecialColor.setEnabled(mappingModel.getLeftBoundaryCondition() == BoundaryCondition.SPECIAL);
        buttonRightSpecialColor.setEnabled(mappingModel.getRightBoundaryCondition() == BoundaryCondition.SPECIAL);
    }

    /**
     * Forced opaque: {@link JColorChooser} offers no alpha.
     */
    private void chooseSpecialColor(final boolean left) {
        final JButton button = left ? buttonLeftSpecialColor : buttonRightSpecialColor;
        final Color chosen = JColorChooser.showDialog(this,
                left ? "Color Below Range" : "Color Above Range", button.getBackground());
        if (chosen == null) {
            return;
        }
        final int argb = 0xff000000 | (chosen.getRGB() & 0xffffff);
        button.setBackground(new Color(argb, false));
        if (left) {
            mappingModel.setLeftSpecialColor(argb);
        } else {
            mappingModel.setRightSpecialColor(argb);
        }
    }

    /**
     * Shows the resolved value of {@link LutEditorMapping#AUTO_STEP_SIZE}, not an empty field.
     */
    private void updateStepSizeField() {
        final double stepSize = effectiveStepSize();
        lastShownStepSize = formatValue(stepSize);
        fieldStepSize.setText(lastShownStepSize);
        updateStepCoverageLabel(stepSize);
    }

    /**
     * {@code [min, min + stepSize * N]}, which differs from the display range once a step size is typed.
     */
    private void updateStepCoverageLabel(final double stepSize) {
        final int colors = currentScheme.getFixCount();
        final double end = editedRangeMin + stepSize * colors;
        labelStepCoverage.setText(colors + " colors \u00d7 " + formatValue(stepSize)
                + " covers " + formatValue(editedRangeMin) + " \u2013 " + formatValue(end));
    }

    private double effectiveStepSize() {
        final double chosen = mappingModel.getStepSize();
        if (chosen > 0.0) {
            return chosen;
        }
        final double lo = editedRangeMin;
        final double hi = editedRangeMax > editedRangeMin ? editedRangeMax : editedRangeMin + 1;
        return StepPresetFunc.defaultStepSize(lo, hi, currentScheme.getFixCount());
    }

    /**
     * Ignores unchanged text, so a focus traversal does not pin a resolved
     * automatic step size as an explicit one.
     */
    private void commitStepSizeField() {
        if (loadingControls) {
            return;
        }
        final String text = fieldStepSize.getText().trim();
        if (text.equals(lastShownStepSize)) {
            return;
        }
        try {
            final double v = Double.parseDouble(text);
            if (v > 0.0) {
                mappingModel.setStepSize(v);
            }
        } catch (final NumberFormatException ignored) {
        }
        updateStepSizeField();
    }

    /**
     * As {@link MappingCurvePanel} formats its range fields.
     */
    private static String formatValue(final double value) {
        if (Math.abs(value - Math.round(value)) < 1e-6) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    // -- Layout ------------------------------------------------------------

    /**
     * Full width since it governs everything below, but understated since it is rarely used.
     */
    private JPanel createConfigurationStrip() {
        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(mutedLabel("Configuration"), BorderLayout.WEST);
        row.add(comboEditorPreset, BorderLayout.CENTER);
        row.add(buttonSaveEditorPreset, BorderLayout.EAST);

        final JPanel strip = new JPanel(new BorderLayout(0, 6));
        strip.add(row, BorderLayout.CENTER);
        strip.add(new JSeparator(), BorderLayout.SOUTH);
        return strip;
    }

    /**
     * The "Data", "Function" and "Mapping" panels, stacked.
     */
    private JPanel createLeftColumn() {
        final JPanel panelData = heightCapped(null);
        panelData.setLayout(new BoxLayout(panelData, BoxLayout.PAGE_AXIS));
        panelData.setBorder(BorderFactory.createTitledBorder("Data"));
        panelData.add(labeledRow("Color scheme:", comboColorScheme));
        panelData.add(Box.createVerticalStrut(4));
        // mixed alignments make a BoxLayout column wider than its widest row
        panelSchemeSwatch.setAlignmentX(Component.LEFT_ALIGNMENT);
        panelData.add(panelSchemeSwatch);
        panelData.add(Box.createVerticalStrut(4));
        panelData.add(leftAligned(labelSchemeKind));
        panelData.setAlignmentX(Component.LEFT_ALIGNMENT);

        // CardLayout keeps the taller card's height, so the dialog does not resize
        panelShape.add(continuousShapeCard(), SHAPE_CARD_CONTINUOUS);
        panelShape.add(discreteShapeCard(), SHAPE_CARD_DISCRETE);
        panelShape.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel panelShapeGroup = heightCapped(null);
        panelShapeGroup.setLayout(new BoxLayout(panelShapeGroup, BoxLayout.PAGE_AXIS));
        panelShapeGroup.setBorder(BorderFactory.createTitledBorder("Function"));
        panelShapeGroup.add(panelShape);
        panelShapeGroup.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
        column.add(panelData);
        column.add(Box.createVerticalStrut(4));
        column.add(panelShapeGroup);
        column.add(Box.createVerticalStrut(4));
        column.add(createBoundaryGroup());
        return column;
    }

    private JPanel continuousShapeCard() {
        final JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.PAGE_AXIS));
        card.add(labeledRow("Preset:", comboMappingPreset));
        card.add(Box.createVerticalStrut(4));
        final JPanel invertRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        invertRow.add(buttonInvertCurve);
        invertRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        invertRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, invertRow.getPreferredSize().height));
        card.add(invertRow);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel discreteShapeCard() {
        final JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.PAGE_AXIS));
        card.add(labeledRow("Step size:", fieldStepSize));
        card.add(Box.createVerticalStrut(4));
        card.add(leftAligned(labelStepCoverage));
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel createBoundaryGroup() {
        final JPanel panel = heightCapped(null);
        panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Mapping"));
        panel.add(labeledRow("Below range:", comboLeftBoundary, buttonLeftSpecialColor));
        panel.add(Box.createVerticalStrut(4));
        panel.add(labeledRow("Above range:", comboRightBoundary, buttonRightSpecialColor));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel createMappingCurveColumn() {
        final JPanel column = new JPanel(new BorderLayout());
        column.setBorder(BorderFactory.createTitledBorder("Transfer function"));
        column.add(hugContents(panelMappingCurve), BorderLayout.CENTER);
        return column;
    }

    private JPanel createBottomBar() {
        final JButton buttonReset = new JButton("Reset");
        buttonReset.addActionListener(e -> resetToSessionBaseline());
        final JButton buttonClose = new JButton("Close");
        buttonClose.addActionListener(e -> setVisible(false));
        normalizeButtonSizes(buttonReset, buttonClose);

        final JButton buttonHelp = new JButton("?");
        buttonHelp.setToolTipText("Help (F1)");
        buttonHelp.setFocusable(false);
        buttonHelp.setMargin(new Insets(0, 0, 0, 0));
        buttonHelp.setPreferredSize(new Dimension(24, 24));
        buttonHelp.addActionListener(e -> showHelp());

        final JPanel panelLeftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelLeftBottom.add(buttonReset);
        panelLeftBottom.add(Box.createHorizontalStrut(8));
        panelLeftBottom.add(buttonHelp);
        panelLeftBottom.add(Box.createHorizontalStrut(8));
        panelLeftBottom.add(labelCurveHint);
        panelLeftBottom.add(Box.createHorizontalStrut(8));
        panelLeftBottom.add(labelStatus);

        final JPanel panelBottom = new JPanel(new BorderLayout());
        panelBottom.add(panelLeftBottom, BorderLayout.WEST);
        panelBottom.add(buttonClose, BorderLayout.EAST);
        return panelBottom;
    }

    /**
     * The bottom bar's buttons are wired in {@link #createBottomBar()}.
     */
    private void installControlListeners() {
        comboColorScheme.addActionListener(e ->
        {
            if (loadingControls) {
                return;
            }
            final Object selected = comboColorScheme.getSelectedItem();
            if (selected instanceof String) {
                selectColorScheme((String) selected);
            }
        });

        comboLeftBoundary.addActionListener(e ->
        {
            if (loadingControls) {
                return;
            }
            mappingModel.setLeftBoundaryCondition((BoundaryCondition) comboLeftBoundary.getSelectedItem());
            updateSpecialColorButtonStates();
        });

        comboRightBoundary.addActionListener(e ->
        {
            if (loadingControls) {
                return;
            }
            mappingModel.setRightBoundaryCondition((BoundaryCondition) comboRightBoundary.getSelectedItem());
            updateSpecialColorButtonStates();
        });

        buttonLeftSpecialColor.addActionListener(e -> chooseSpecialColor(true));
        buttonRightSpecialColor.addActionListener(e -> chooseSpecialColor(false));

        comboMappingPreset.addActionListener(e ->
        {
            if (loadingControls) {
                return;
            }
            mappingModel.applyPreset((PresetShape) comboMappingPreset.getSelectedItem());
        });

        fieldStepSize.addActionListener(e -> commitStepSizeField());
        fieldStepSize.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent e) {
                commitStepSizeField();
            }
        });

        buttonInvertCurve.addActionListener(e -> mappingModel.invertCurve());

        comboEditorPreset.addActionListener(e ->
        {
            if (loadingControls) {
                return;
            }
            final Object selected = comboEditorPreset.getSelectedItem();
            if (selected instanceof String) {
                applyEditorPreset((String) selected);
            }
        });

        buttonSaveEditorPreset.addActionListener(e -> promptAndSaveEditorPreset());

        getRootPane().registerKeyboardAction(e -> showHelp(), KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        mappingModel.addChangeListener(panelMappingCurve::repaint);
        mappingModel.addChangeListener(panelSchemeSwatch::repaint);
        mappingModel.addChangeListener(this::pushLiveEdits);
        mappingModel.addChangeListener(() ->
        {
            if (!loadingControls) {
                syncEditorPresetSelection();
            }
        });
    }

    /**
     * Size the window, with the graph as wide as the left column.
     */
    private void packAndMatchGraphWidth(final JPanel panelLeftColumn, final JPanel panelMappingCurveColumn) {
        // combo boxes under-measure until realized, so pack before measuring
        pack();

        final Insets insets = panelMappingCurveColumn.getBorder().getBorderInsets(panelMappingCurveColumn);
        final int targetGraphWidth = Math.max(panelLeftColumn.getWidth() - insets.left - insets.right,
                panelMappingCurve.minimumGraphWidth());
        panelMappingCurve.setPreferredSize(new Dimension(targetGraphWidth, panelMappingCurve.getPreferredSize().height));

        pack();
        setMinimumSize(getPreferredSize());
    }

    // -- Widget factories --------------------------------------------------

    /**
     * Grouped by {@link ColorSchemeCategoryHelper}.
     */
    private JComboBox<Object> createColorSchemeCombo() {
        final JComboBox<Object> combo = createGroupedCombo("Select Color Scheme");
        // otherwise the widest category header sets the settings column width
        combo.setPrototypeDisplayValue("twilight_shifted_r");
        final GroupedComboModel model = (GroupedComboModel) combo.getModel();
        for (final Map.Entry<String, List<String>> category : ColorSchemeCategoryHelper.groupByCategory(ColorSchemeFactory.discoverNames()).entrySet()) {
            model.addElement(new CategoryHeader(category.getKey()));
            for (final String name : category.getValue()) {
                model.addElement(name);
            }
        }
        return combo;
    }

    private JComboBox<Object> createEditorPresetCombo() {
        final JComboBox<Object> combo = createGroupedCombo("Select Configuration");
        combo.setPrototypeDisplayValue("Select Configuration");
        refreshEditorPresetCombo(combo);
        return combo;
    }

    /**
     * Keeps the selection; call with listeners muted, or it is re-applied as if picked.
     */
    private static void refreshEditorPresetCombo(final JComboBox<Object> combo) {
        final GroupedComboModel model = (GroupedComboModel) combo.getModel();
        final Object previouslySelected = combo.getSelectedItem();

        final List<String> userDefined = new ArrayList<>();
        final List<String> builtin = new ArrayList<>();
        for (final String name : EditorPresetHelper.discoverNames()) {
            (EditorPresetHelper.isUserDefined(name) ? userDefined : builtin).add(name);
        }

        model.removeAllElements();
        if (!userDefined.isEmpty()) {
            model.addElement(new CategoryHeader("My Configurations"));
            for (final String name : userDefined) {
                model.addElement(name);
            }
        }
        if (!builtin.isEmpty()) {
            model.addElement(new CategoryHeader("Built-in"));
            for (final String name : builtin) {
                model.addElement(name);
            }
        }

        combo.setSelectedItem(previouslySelected);
    }

    /**
     * Renders {@link CategoryHeader}s as bold, unselectable labels.
     */
    private static JComboBox<Object> createGroupedCombo(final String placeholderText) {
        final JComboBox<Object> combo = new JComboBox<>(new GroupedComboModel());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected, final boolean cellHasFocus) {
                final boolean header = value instanceof CategoryHeader;
                super.getListCellRendererComponent(list, value, index, isSelected && !header, cellHasFocus && !header);
                if (header) {
                    setFont(getFont().deriveFont(Font.BOLD));
                    setEnabled(false);
                    setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 4));
                } else {
                    setFont(getFont().deriveFont(Font.PLAIN));
                    setEnabled(true);
                    setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
                    if (index == -1 && value == null) {
                        setText(placeholderText);
                    }
                }
                return this;
            }
        });
        combo.setSelectedIndex(-1);
        return combo;
    }

    private static JComboBox<BoundaryCondition> createBoundaryCombo() {
        final JComboBox<BoundaryCondition> combo = new JComboBox<>(BoundaryCondition.values());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(final JList<?> list, final Object value,
                                                          final int index, final boolean isSelected, final boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BoundaryCondition) {
                    setText(boundaryLabel((BoundaryCondition) value));
                }
                return this;
            }
        });
        return combo;
    }

    private static String boundaryLabel(final BoundaryCondition condition) {
        switch (condition) {
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

    private static JButton createSpecialColorButton(final String toolTip, final int argb) {
        final JButton button = new JButton();
        button.setToolTipText(toolTip);
        final Dimension size = new Dimension(20, 20);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setBackground(new Color(argb, false));
        button.setEnabled(false);
        return button;
    }

    // -- Layout helpers ----------------------------------------------------

    private static JPanel leftAligned(final JComponent component) {
        final JPanel row = heightCapped(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(component);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    /**
     * Caps the height at the current preferred height, computed on demand: a
     * cap taken at build time would freeze an empty label at zero height.
     */
    private static JPanel heightCapped(final LayoutManager layout) {
        return new JPanel(layout) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            private static final long serialVersionUID = 1L;
        };
    }

    private static JLabel mutedLabel(final String text) {
        final JLabel label = new JLabel(text);
        final Color disabled = UIManager.getColor("Label.disabledForeground");
        label.setForeground(disabled != null ? disabled : Color.GRAY);
        return label;
    }

    /**
     * Keeps {@code component} at its preferred size so titled borders hug it.
     */
    private static JPanel hugContents(final JComponent component) {
        final JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.add(component);
        return wrapper;
    }

    private static JPanel labeledRow(final String label, final JComponent component) {
        return labeledRow(label, component, null);
    }

    private static JPanel labeledRow(final String label, final JComponent component, final JComponent trailing) {
        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(component, BorderLayout.CENTER);
        if (trailing != null) {
            row.add(trailing, BorderLayout.EAST);
        }

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        return row;
    }

    private static void normalizeButtonSizes(final JComponent... components) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (final JComponent component : components) {
            final Dimension preferredSize = component.getPreferredSize();
            maxWidth = Math.max(maxWidth, preferredSize.width);
            maxHeight = Math.max(maxHeight, preferredSize.height);
        }
        final Dimension fixedSize = new Dimension(maxWidth, maxHeight);
        for (final JComponent component : components) {
            component.setPreferredSize(fixedSize);
            component.setMinimumSize(fixedSize);
        }
    }

    // -- Help --------------------------------------------------------------

    /**
     * In a scroll pane: as a plain string the dialog would be taller than the screen.
     */
    private void showHelp() {
        final String message = String.join("\n",
                "LUT Editor help:",
                "",
                "Configuration (the strip across the top):",
                "- Applies a saved combination of color scheme, boundary handling and",
                "  transfer function (see EditorPreset) -- built-in ones ship with the app,",
                "  and \"Save as...\" stores the current combination (under a name you",
                "  choose) for reuse later, next to the built-in ones under",
                "  \"My Configurations\". Applying one leaves the current input value range",
                "  alone, since that is specific to whatever source's data you are editing,",
                "  not part of the saved look.",
                "",
                "Data:",
                "- Color scheme selects the LUT colors the mapped value is looked up in.",
                "- Whether those colors blend smoothly or are used as individually chosen",
                "  colors follows the chosen color scheme file's own declared kind, not a",
                "  setting here. The line under the swatch says which one it is, and it",
                "  decides which shape control you get.",
                "",
                "Function:",
                "- For a smooth (continuous) color scheme, Preset replaces the transfer",
                "  function with a predefined shape (Linear, Log, Exp, Sigmoid, \u03b1-Sigmoid,",
                "  Tan, Atan). It can still be adjusted afterwards,",
                "  and Invert flips it vertically on top of whatever shape/edits it has.",
                "- For a discrete (categorical) color scheme, Step size replaces it: it is",
                "  how many input values one color covers. Set it to 1 to give every",
                "  integer label its own color. The line below it says how far the color",
                "  scheme reaches at that step size -- past there, the \"above range\"",
                "  condition takes over, so a Cycle there is what repeats the color scheme",
                "  across the rest of the range. The field starts out showing the step",
                "  size that spreads the color scheme exactly once.",
                "",
                "Mapping:",
                "- Below range / Above range choose what happens to input values past that",
                "  end of the range, independently:",
                "    Clamp       holds the color scheme's color at that end.",
                "    Cycle       wraps back around, so the color scheme repeats.",
                "    Fixed color paints one chosen color instead of the color scheme's --",
                "                e.g. a dedicated background for a label image's 0. Click",
                "                the swatch beside the dropdown to pick it.",
                "",
                "Transfer function (the graph):",
                "- The color bar to the left previews the color scheme itself; the one",
                "  below the graph (\"after transform\") previews the color actually",
                "  produced for each input value.",
                "- The boxes at the left/right ends of the x axis set the input value range.",
                "- Click the pencil beside the graph's top-right corner to show and edit",
                "  the control points: left-click to add or drag a point, right-click a",
                "  point to remove it. Hovering or dragging one shows its input value and",
                "  the color it maps to, with guides running out to both color bars. The",
                "  pencil is disabled for a discrete color scheme, whose shape comes from",
                "  its step size rather than from a curve.",
                "- For a discrete color scheme the line is drawn as the straight ramp it",
                "  really is -- snapping to a color is the color scheme's doing, not the",
                "  function's. The grid it crosses is the color scheme's own color fixes,",
                "  a tick under the color bar marks every color change, and the dashed",
                "  vertical marks where the color scheme runs out.",
                "- Past either end of the color scheme's domain the line is dashed,",
                "  because there its shape is the boundary condition's doing.",
                "",
                "- Edits here take effect in the viewer immediately, as you make them.",
                "  They stay in effect when the dialog is closed.",
                "- Reset reverts to how the source looked when it was selected (or when",
                "  this dialog was opened on it), discarding edits made since.",
                "",
                "Shortcut:",
                "- Press F1 anywhere in this dialog to open this help.");

        final JTextArea text = new JTextArea(message);
        text.setEditable(false);
        // monospaced for the hand-aligned columns, sized to follow UI scaling
        final Font labelFont = UIManager.getFont("Label.font");
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, labelFont != null ? labelFont.getSize() : 12));
        text.setBackground(UIManager.getColor("Panel.background"));
        text.setBorder(new EmptyBorder(4, 6, 4, 6));
        text.setCaretPosition(0);

        final JScrollPane scroll = new JScrollPane(text);
        final int width = Math.min(text.getPreferredSize().width + 24, MAX_HELP_WIDTH);
        scroll.setPreferredSize(new Dimension(width, HELP_HEIGHT));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JOptionPane.showMessageDialog(this, scroll, "LUT Editor Help", JOptionPane.INFORMATION_MESSAGE);
    }

    // -- Nested classes ----------------------------------------------------

    /**
     * A non-selectable group label in a {@link #createGroupedCombo grouped combo}.
     */
    private static final class CategoryHeader {
        private final String label;

        CategoryHeader(final String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Never selects a {@link CategoryHeader}; the previous selection stays.
     */
    private static final class GroupedComboModel extends DefaultComboBoxModel<Object> {
        @Override
        public void setSelectedItem(final Object item) {
            if (item instanceof CategoryHeader) {
                return;
            }
            super.setSelectedItem(item);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * The color scheme as a horizontal bar.
     */
    private static class GradientPreviewPanel extends JPanel {
        private IColorScheme scheme = ContinuousColorScheme.DEFAULT;

        public GradientPreviewPanel() {
            setPreferredSize(new Dimension(300, 16));
        }

        public void update(final IColorScheme scheme) {
            this.scheme = scheme;
            repaint();
        }

        @Override
        protected void paintComponent(final Graphics g) {
            super.paintComponent(g);

            final int w = getWidth();
            final int h = getHeight();

            if (scheme != null) {
                final int schemeRange = scheme.getRange();
                for (int i = 0; i < w; i++) {
                    final double t = w > 1 ? i / (double) (w - 1) : 0.0;
                    g.setColor(new Color(scheme.getRGB(t * schemeRange)));
                    g.fillRect(i, 0, 1, h);
                }
            }

            g.setColor(Color.BLACK);
            g.drawRect(0, 0, w - 1, h - 1);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
