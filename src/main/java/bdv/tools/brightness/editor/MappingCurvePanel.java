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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import bdv.tools.brightness.colorscheme.IColorScheme;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.DiscreteColorScheme;
import bdv.tools.brightness.converter.PresetColorSchemeWrapper;
import bdv.tools.brightness.presetfunc.IPresetFunc;

/**
 * Interactive graph of the transfer function, with the color scheme as a color bar
 * along the y axis and the resulting colors along the x axis. Hosts the pencil
 * toggle ({@link #setEditMode(boolean)}) and the range boxes at the ends of the
 * x axis.
 * <p>
 * In edit mode, left-click adds or drags a {@link Curve} control point and
 * right-click removes one.
 */
public class MappingCurvePanel extends JPanel implements MouseListener, MouseMotionListener {
    private static final int POINT_RADIUS = 5;

    /**
     * Fits a three-digit fix index.
     */
    private static final int LABEL_WIDTH = 40;

    /**
     * Holds the pencil toggle and half the max box.
     */
    private static final int RIGHT_MARGIN = 36;

    private static final int TOP_MARGIN = 10;

    private static final int LABEL_HEIGHT = 26;

    private static final int RANGE_FIELD_WIDTH = 64;

    private static final int RANGE_FIELD_HEIGHT = 16;

    private static final int COLORBAR_WIDTH = 16;

    private static final int COLORBAR_GAP = 10;

    private static final int BASE_PREFERRED_HEIGHT = 206;

    /**
     * Plot width excluding gutters.
     */
    private static final int MIN_PLOT_WIDTH = 300;

    private static final int EDIT_TOGGLE_SIZE = 22;

    private static final int PENCIL_MARGIN_X = 6;

    private static final int PENCIL_MARGIN_Y = 0;

    /**
     * Below this, the discrete fix grid is not drawn.
     */
    private static final int MIN_FIX_SPACING = 4;

    private static final Color CURVE_COLOR = Color.BLACK;

    private static final Color OUT_OF_DOMAIN_CURVE_COLOR = new Color(0, 0, 0, 140);

    private static final Color POINT_FILL = Color.WHITE;

    private static final Color POINT_BORDER = new Color(230, 160, 20);

    private static final Color GRID_COLOR = new Color(225, 225, 225);

    private static final Color FIX_GRID_COLOR = new Color(239, 239, 239);

    private static final Color FRAME_COLOR = new Color(120, 120, 120);

    private static final Color DOMAIN_EDGE_COLOR = new Color(176, 176, 176);

    private static final Color CHIP_BACKGROUND = new Color(60, 60, 60);

    private static final Stroke SOLID_CURVE_STROKE = new BasicStroke(2);

    private static final Stroke DASHED_CURVE_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 4f}, 0f);

    private static final Stroke POINT_STROKE = new BasicStroke(2);

    private static final Stroke THIN_STROKE = new BasicStroke(1);

    private static final Stroke DASHED_THIN_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f);

    private static final Stroke GUIDE_STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{3f, 3f}, 0f);

    /**
     * As a fraction of the tick spacing.
     */
    static final double TICK_LABEL_TOLERANCE = 0.05;

    private static final int MAX_TICK_DECIMALS = 12;

    private static final String EDIT_TOOLTIP = "Edit the transfer function";

    private static final String EDIT_TOOLTIP_DISCRETE = "Only editable for a continuous color scheme";

    private final LutEditorMapping model;

    private double rangeMin = 0;

    private double rangeMax = 255;

    private IColorScheme colorScheme = ContinuousColorScheme.DEFAULT;

    private Integer draggedPoint = null;

    /**
     * Separate from {@link #draggedPoint} so the hover hint shows before a drag starts.
     */
    private Integer hoveredPoint = null;

    private boolean editMode = false;

    private final JTextField minField = new JTextField();

    private final JTextField maxField = new JTextField();

    private final JToggleButton buttonEditCurve = new JToggleButton(new PencilIcon());

    private BiConsumer<Double, Double> rangeChangeListener = null;

    private Consumer<Boolean> editModeListener = null;

    public MappingCurvePanel(final LutEditorMapping model)
    {
        this.model = model;
        this.setPreferredSize(new Dimension(280, BASE_PREFERRED_HEIGHT));
        this.setBackground(Color.WHITE);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);

        model.addChangeListener(this::repaint);

        this.setLayout(null);
        for (final JTextField field : new JTextField[]{this.minField, this.maxField}) {
            field.setHorizontalAlignment(SwingConstants.CENTER);
            this.add(field);
        }
        this.minField.setText(formatValue(this.rangeMin));
        this.maxField.setText(formatValue(this.rangeMax));
        this.minField.addActionListener(e -> this.commitMinField());
        this.maxField.addActionListener(e -> this.commitMaxField());
        this.minField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent e)
            {
                MappingCurvePanel.this.commitMinField();
            }
        });
        this.maxField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(final FocusEvent e)
            {
                MappingCurvePanel.this.commitMaxField();
            }
        });

        this.buttonEditCurve.setToolTipText(EDIT_TOOLTIP);
        this.buttonEditCurve.setFocusable(false);
        this.buttonEditCurve.setMargin(new Insets(0, 0, 0, 0));
        this.buttonEditCurve.addActionListener(e -> this.setEditMode(this.buttonEditCurve.isSelected()));
        this.add(this.buttonEditCurve);
    }

    @Override
    public void doLayout()
    {
        if (this.getWidth() <= 0 || this.getHeight() <= 0) {
            return;
        }
        final int y = this.transformBarBottom() + 2;
        final int left = this.plotLeft();

        // centered on the plot edges, min clamped so it never overlaps max
        final int maxFieldX = this.curveXToPixelX(1) - RANGE_FIELD_WIDTH / 2;
        final int minFieldX = Math.min(left - RANGE_FIELD_WIDTH / 2, maxFieldX - RANGE_FIELD_WIDTH);

        this.minField.setBounds(minFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT);
        this.maxField.setBounds(maxFieldX, y, RANGE_FIELD_WIDTH, RANGE_FIELD_HEIGHT);

        // beside the plot, not over the curve
        this.buttonEditCurve.setBounds(this.plotRight() + PENCIL_MARGIN_X,
                this.plotTop() + PENCIL_MARGIN_Y, EDIT_TOGGLE_SIZE, EDIT_TOGGLE_SIZE);
    }

    /**
     * Set the actual data range represented by the horizontal axis.
     */
    public void setRange(final double min, final double max)
    {
        this.rangeMin = min;
        this.rangeMax = max;
        this.minField.setText(formatValue(min));
        this.maxField.setText(formatValue(max));
        this.revalidate();
        this.repaint();
    }

    /**
     * Notified when the user edits the range boxes.
     */
    public void setRangeChangeListener(final BiConsumer<Double, Double> listener)
    {
        this.rangeChangeListener = listener;
    }

    /**
     * Notified when the pencil toggle changes edit mode.
     */
    public void setEditModeListener(final Consumer<Boolean> listener)
    {
        this.editModeListener = listener;
    }

    public int minimumGraphWidth()
    {
        return this.plotLeft() + MIN_PLOT_WIDTH + RIGHT_MARGIN;
    }

    private void commitMinField()
    {
        try {
            final double v = Double.parseDouble(this.minField.getText().trim());
            if (v < this.rangeMax) {
                this.rangeMin = v;
                if (this.rangeChangeListener != null) {
                    this.rangeChangeListener.accept(this.rangeMin, this.rangeMax);
                }
            }
        } catch (final NumberFormatException ignored) {
        }
        this.minField.setText(formatValue(this.rangeMin));
        this.repaint();
    }

    private void commitMaxField()
    {
        try {
            final double v = Double.parseDouble(this.maxField.getText().trim());
            if (v > this.rangeMin) {
                this.rangeMax = v;
                if (this.rangeChangeListener != null) {
                    this.rangeChangeListener.accept(this.rangeMin, this.rangeMax);
                }
            }
        } catch (final NumberFormatException ignored) {
        }
        this.maxField.setText(formatValue(this.rangeMax));
        this.repaint();
    }

    /**
     * Set the color scheme used to render the color bar.
     */
    public void setColorScheme(final IColorScheme colorScheme)
    {
        this.colorScheme = colorScheme == null ? ContinuousColorScheme.DEFAULT : colorScheme;
        this.syncEditability();
    }

    /**
     * Whether curve control points are shown and editable.
     */
    public void setEditMode(final boolean editMode)
    {
        if (this.editMode == editMode) {
            return;
        }
        this.editMode = editMode;
        this.buttonEditCurve.setSelected(editMode);
        if (!editMode) {
            this.hoveredPoint = null;
        }
        if (this.editModeListener != null) {
            this.editModeListener.accept(editMode);
        }
        this.repaint();
    }

    public boolean isEditMode()
    {
        return this.editMode;
    }

    /**
     * A discrete scheme has no curve to edit, so the pencil is disabled.
     */
    private void syncEditability()
    {
        final boolean editable = !this.isDiscrete();
        if (!editable) {
            this.setEditMode(false);
        }
        this.buttonEditCurve.setEnabled(editable);
        this.buttonEditCurve.setToolTipText(editable ? EDIT_TOOLTIP : EDIT_TOOLTIP_DISCRETE);
        this.repaint();
    }

    private boolean isDiscrete()
    {
        return this.colorScheme instanceof DiscreteColorScheme;
    }

    private int plotLeft()
    {
        return LABEL_WIDTH + COLORBAR_WIDTH + COLORBAR_GAP;
    }

    private int plotRight()
    {
        return this.getWidth() - RIGHT_MARGIN;
    }

    private int plotTop()
    {
        return TOP_MARGIN;
    }

    private int plotBottom()
    {
        return this.getHeight() - LABEL_HEIGHT - COLORBAR_GAP - COLORBAR_WIDTH;
    }

    private int transformBarTop()
    {
        return this.plotBottom() + COLORBAR_GAP;
    }

    private int transformBarBottom()
    {
        return this.transformBarTop() + COLORBAR_WIDTH;
    }

    private int plotWidth()
    {
        return Math.max(1, this.plotRight() - this.plotLeft());
    }

    private int plotHeight()
    {
        return Math.max(1, this.plotBottom() - this.plotTop());
    }

    // -- Coordinate conversions, named <from>To<to> --------------------------
    // pixels, raw values [rangeMin, rangeMax], curve x and y [0, 1], drawing output [0, 255]

    private double pixelXToValue(final int pixelX)
    {
        final double normX = Math.max(0.0, Math.min(1.0, (pixelX - this.plotLeft()) / (double) this.plotWidth()));
        return this.rangeMin + normX * (this.rangeMax - this.rangeMin);
    }

    private double valueToCurveX(final double value)
    {
        final double span = this.rangeMax - this.rangeMin;
        final double frac = span > 0 ? (value - this.rangeMin) / span : 0.0;
        return Math.max(0.0, Math.min(1.0, frac));
    }

    private int curveXToPixelX(final double normX)
    {
        return this.plotLeft() + (int) Math.round(normX * this.plotWidth());
    }

    private double pixelYToCurveY(final int pixelY)
    {
        final double v = (this.plotBottom() - pixelY) / (double) this.plotHeight();
        return Math.max(0.0, Math.min(1.0, v));
    }

    private int curveYToPixelY(final double curveY)
    {
        return this.plotBottom() - (int) Math.round(curveY * this.plotHeight());
    }

    private int outputToPixelY(final int outputValue)
    {
        return this.plotBottom() - (int) Math.round(outputValue / 255.0 * this.plotHeight());
    }

    /**
     * The scale the y axis is labelled in.
     */
    private double curveYToColorIndex(final double curveY)
    {
        return curveY * this.colorScheme.getRange();
    }

    @Override
    protected void paintComponent(final Graphics g)
    {
        super.paintComponent(g);
        final Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // rebuilt per paint so the preview is always in sync
        final PresetColorSchemeWrapper wrapper = ColorSchemeWrapperFactory.build(this.colorScheme, this.model, this.rangeMin, this.rangeMax);
        final double schemeRange = wrapper.getColorScheme().getRange();
        final int[] fixBoundaries = this.isDiscrete() ? this.fixBoundaryColumns(wrapper) : null;

        this.drawGrid(g2, fixBoundaries, schemeRange);
        this.drawDomainEdge(g2, wrapper);
        this.drawCurve(g2, wrapper);
        if (this.editMode && !this.isDiscrete()) {
            this.drawControlPoints(g2);
        }
        this.drawOutputColorBar(g2, wrapper.getColorScheme());
        this.drawTransformColorBar(g2, wrapper, fixBoundaries);
        // last, so its guides draw over the color bars
        this.drawHoverHint(g2);
    }

    /**
     * Quarters, or for a discrete scheme one line per fix, which is what
     * shows the discreteness. {@code fixBoundaries} is {@code null} for a
     * continuous scheme.
     */
    private void drawGrid(final Graphics2D g, final int[] fixBoundaries, final double schemeRange)
    {
        final int left = this.plotLeft();
        final int right = this.plotRight();
        final int top = this.plotTop();
        final int bottom = this.plotBottom();

        g.setStroke(THIN_STROKE);
        if (this.drawsFixGrid(fixBoundaries, schemeRange)) {
            g.setColor(FIX_GRID_COLOR);
            for (int fix = 1; fix < schemeRange; fix++) {
                final int y = this.outputToPixelY((int) Math.round(fix / schemeRange * 255.0));
                g.drawLine(left, y, right, y);
            }
            for (final int x : fixBoundaries) {
                g.drawLine(x, top, x, bottom);
            }
        } else {
            g.setColor(GRID_COLOR);
            for (int i = 1; i < 4; i++) {
                final int x = left + i * (right - left) / 4;
                g.drawLine(x, top, x, bottom);
                final int y = top + i * (bottom - top) / 4;
                g.drawLine(left, y, right, y);
            }
        }

        g.setColor(FRAME_COLOR);
        g.drawRect(left, top, right - left, bottom - top);
    }

    private boolean drawsFixGrid(final int[] fixBoundaries, final double schemeRange)
    {
        if (fixBoundaries == null) {
            return false;
        }
        if (this.plotHeight() / schemeRange < MIN_FIX_SPACING) {
            return false;
        }
        return fixBoundaries.length == 0 || this.plotWidth() / (double) fixBoundaries.length >= MIN_FIX_SPACING;
    }

    /**
     * Pixel columns where a discrete scheme's color changes. Scanned from the
     * wrapper rather than computed, so CYCLE wraps are included and it matches
     * the color bar.
     */
    private int[] fixBoundaryColumns(final PresetColorSchemeWrapper wrapper)
    {
        final double schemeRange = wrapper.getColorScheme().getRange();
        final int left = this.plotLeft();
        final int right = this.plotRight();
        final int[] columns = new int[Math.max(1, right - left + 1)];
        int count = 0;
        int previousFix = Integer.MIN_VALUE;
        for (int px = left; px <= right; px++) {
            final int fix = fixIndex(wrapper.getSchemeValueForRaw(this.pixelXToValue(px)), schemeRange);
            if (previousFix != Integer.MIN_VALUE && fix != previousFix) {
                columns[count++] = px;
            }
            previousFix = fix;
        }
        final int[] result = new int[count];
        System.arraycopy(columns, 0, result, 0, count);
        return result;
    }

    /**
     * As {@link bdv.tools.brightness.colorscheme.DiscreteColorScheme} resolves it.
     */
    private static int fixIndex(final double schemeValue, final double schemeRange)
    {
        return (int) Math.max(0, Math.min(schemeRange - 1, Math.floor(schemeValue)));
    }

    /**
     * Marks where the domain ends inside the display range (only happens for a discrete scheme).
     */
    private void drawDomainEdge(final Graphics2D g, final PresetColorSchemeWrapper wrapper)
    {
        final double domainMax = wrapper.getPresetFunc().getMax();
        if (!(domainMax > this.rangeMin) || !(domainMax < this.rangeMax)) {
            return;
        }
        final int x = this.curveXToPixelX(this.valueToCurveX(domainMax));
        g.setColor(DOMAIN_EDGE_COLOR);
        g.setStroke(DASHED_THIN_STROKE);
        g.drawLine(x, this.plotTop(), x, this.plotBottom());
    }

    /**
     * Read off the wrapper, not the {@link Curve}, which a discrete scheme
     * ignores. Drawn continuous even then: snapping is the color scheme's job.
     * Dashed outside the domain; two whole paths so the dash pattern does not
     * restart at every pixel.
     */
    private void drawCurve(final Graphics2D g, final PresetColorSchemeWrapper wrapper)
    {
        final IPresetFunc presetFunc = wrapper.getPresetFunc();
        final double domainMin = presetFunc.getMin();
        final double domainMax = presetFunc.getMax();
        final double schemeRange = wrapper.getColorScheme().getRange();
        final int left = this.plotLeft();
        final int right = this.plotRight();

        final Path2D.Double inDomain = new Path2D.Double();
        final Path2D.Double outOfDomain = new Path2D.Double();

        int previousX = 0;
        int previousY = 0;
        boolean previousIn = false;
        boolean started = false;
        for (int px = left; px <= right; px++) {
            final double value = this.pixelXToValue(px);
            final boolean in = value >= domainMin && value <= domainMax;
            final int py = this.outputToPixelY(schemeValueToOutput(wrapper.getSchemeValueForRaw(value), schemeRange));
            final Path2D.Double path = in ? inDomain : outOfDomain;
            if (!started) {
                path.moveTo(px, py);
            } else if (in == previousIn) {
                path.lineTo(px, py);
            } else {
                // start at the previous point so the paths meet
                path.moveTo(previousX, previousY);
                path.lineTo(px, py);
            }
            previousX = px;
            previousY = py;
            previousIn = in;
            started = true;
        }

        g.setColor(CURVE_COLOR);
        g.setStroke(SOLID_CURVE_STROKE);
        g.draw(inDomain);
        g.setColor(OUT_OF_DOMAIN_CURVE_COLOR);
        g.setStroke(DASHED_CURVE_STROKE);
        g.draw(outOfDomain);
    }

    private static int schemeValueToOutput(final double schemeValue, final double schemeRange)
    {
        final double clamped = Math.max(0.0, Math.min(schemeRange, schemeValue));
        return (int) Math.round(clamped / schemeRange * 255.0);
    }

    private void drawControlPoints(final Graphics2D g)
    {
        final Curve curve = this.model.getCurve();
        final int highlighted = this.annotatedPoint();
        for (int i = 0; i < curve.getPointCount(); i++) {
            this.drawControlPointAt(g, this.curveXToPixelX(curve.getX(i)), this.curveYToPixelY(curve.getY(i)), i == highlighted);
        }
    }

    private void drawControlPointAt(final Graphics2D g, final int px, final int py, final boolean highlighted)
    {
        g.setColor(highlighted ? POINT_BORDER : POINT_FILL);
        g.fillOval(px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS);
        g.setColor(POINT_BORDER);
        g.setStroke(POINT_STROKE);
        g.drawOval(px - POINT_RADIUS, py - POINT_RADIUS, 2 * POINT_RADIUS, 2 * POINT_RADIUS);
    }

    /**
     * The color scheme itself, labelled in color indices rather than 0-255.
     */
    private void drawOutputColorBar(final Graphics2D g, final IColorScheme scheme)
    {
        final int barLeft = LABEL_WIDTH;
        final int top = this.plotTop();
        final int bottom = this.plotBottom();

        final double schemeRange = scheme.getRange();

        for (int py = top; py < bottom; py++) {
            final double t = (bottom - py) / (double) this.plotHeight();
            g.setColor(new Color(scheme.getRGB(t * schemeRange)));
            // fillRect: an antialiased drawLine with a leftover 2px stroke smears rows
            g.fillRect(barLeft, py, COLORBAR_WIDTH + 1, 1);
        }

        g.setColor(FRAME_COLOR);
        g.setStroke(THIN_STROKE);
        g.drawRect(barLeft, top, COLORBAR_WIDTH, bottom - top);

        g.setColor(Color.DARK_GRAY);
        if (this.labelsEveryBand(schemeRange, g.getFontMetrics())) {
            this.drawBandLabels(g, barLeft, schemeRange);
        } else {
            this.drawFractionLabels(g, barLeft);
        }
    }

    /**
     * Only for a discrete scheme whose bands are tall enough for text.
     */
    private boolean labelsEveryBand(final double schemeRange, final FontMetrics fm)
    {
        return this.isDiscrete() && this.plotHeight() / schemeRange >= fm.getHeight() + 2;
    }

    private void drawBandLabels(final Graphics2D g, final int barLeft, final double schemeRange)
    {
        final FontMetrics fm = g.getFontMetrics();
        for (int fix = 0; fix < schemeRange; fix++) {
            final String text = Integer.toString(fix);
            final int py = this.outputToPixelY((int) Math.round((fix + 0.5) / schemeRange * 255.0));
            g.drawString(text, barLeft - fm.stringWidth(text) - 6, py + fm.getAscent() / 2 - 1);
        }
    }

    /**
     * Whole indices for a discrete scheme, whose color index is always whole; a continuous scheme
     * blends between fixes, so its fractional indices are real.
     */
    private void drawFractionLabels(final Graphics2D g, final int barLeft)
    {
        final FontMetrics fm = g.getFontMetrics();
        final double lastColor = this.isDiscrete() ? this.colorScheme.getRange() - 1 : this.colorScheme.getRange();
        final String[] continuousLabels = quarterTickLabels(0, lastColor);
        for (int i = 0; i <= 4; i++) {
            final double frac = i / 4.0;
            final String text = this.isDiscrete() ? Long.toString(Math.round(frac * lastColor)) : continuousLabels[i];
            final int py = this.outputToPixelY((int) Math.round(frac * 255.0));
            g.drawString(text, barLeft - fm.stringWidth(text) - 6, py + fm.getAscent() / 2 - 1);
        }
    }

    /**
     * The rendered color for each raw value, with a tick under each discrete color change.
     */
    private void drawTransformColorBar(final Graphics2D g, final PresetColorSchemeWrapper wrapper, final int[] fixBoundaries)
    {
        final int left = this.plotLeft();
        final int right = this.plotRight();
        final int top = this.transformBarTop();
        final int bottom = this.transformBarBottom();

        for (int px = left; px < right; px++) {
            final double value = this.pixelXToValue(px);
            g.setColor(new Color(wrapper.getRGBForRaw(value)));
            g.fillRect(px, top, 1, bottom - top + 1);
        }

        g.setColor(FRAME_COLOR);
        g.setStroke(THIN_STROKE);
        g.drawRect(left, top, right - left, bottom - top);

        if (this.drawsFixGrid(fixBoundaries, wrapper.getColorScheme().getRange())) {
            for (final int x : fixBoundaries) {
                g.drawLine(x, bottom + 1, x, bottom + 4);
            }
        }

        // min and max are the range boxes
        g.setColor(Color.DARK_GRAY);
        final FontMetrics fm = g.getFontMetrics();
        final String[] labels = quarterTickLabels(this.rangeMin, this.rangeMax);
        for (int i = 1; i <= 3; i++) {
            final String text = labels[i];
            final int px = this.curveXToPixelX(i / 4.0);
            g.drawString(text, px - fm.stringWidth(text) / 2, bottom + fm.getAscent() + 4);
        }
    }

    /**
     * Labels for the ticks at 0, 1/4, ..., 4/4 of {@code [min, max]}, all with the fewest decimals
     * that move no tick by more than {@link #TICK_LABEL_TOLERANCE} of the tick spacing.
     */
    static String[] quarterTickLabels(final double min, final double max)
    {
        final double[] ticks = quarterTicks(min, max);
        final int decimals = tickDecimals(min, max);
        final String[] labels = new String[5];
        for (int i = 0; i < 5; i++) {
            labels[i] = roundTo(ticks[i], decimals).toPlainString();
        }
        return labels;
    }

    /**
     * An arbitrary value on the axis of {@link #quarterTickLabels}: at least the ticks' decimals,
     * and more where the ticks are exact but other values would round off by more than the tolerance.
     */
    static String axisValueLabel(final double value, final double min, final double max)
    {
        // slack: a spacing like (4.1 - 0.1) / 4 lands a hair under its exact boundary
        final double tolerance = tickLabelTolerance(min, max) * (1 + 1e-9);
        int decimals = tickDecimals(min, max);
        while (decimals < MAX_TICK_DECIMALS && 0.5 * Math.pow(10, -decimals) > tolerance) {
            decimals++;
        }
        return roundTo(value, decimals).toPlainString();
    }

    private static int tickDecimals(final double min, final double max)
    {
        final double[] ticks = quarterTicks(min, max);
        final double tolerance = tickLabelTolerance(min, max);
        int decimals = 0;
        while (decimals < MAX_TICK_DECIMALS && !roundsWithin(ticks, decimals, tolerance)) {
            decimals++;
        }
        return decimals;
    }

    private static double[] quarterTicks(final double min, final double max)
    {
        final double[] ticks = new double[5];
        for (int i = 0; i <= 4; i++) {
            ticks[i] = min + i / 4.0 * (max - min);
        }
        return ticks;
    }

    private static double tickLabelTolerance(final double min, final double max)
    {
        return TICK_LABEL_TOLERANCE * (max - min) / 4;
    }

    private static boolean roundsWithin(final double[] values, final int decimals, final double tolerance)
    {
        for (final double v : values) {
            if (Math.abs(roundTo(v, decimals).doubleValue() - v) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@link BigDecimal} rather than {@code String.format}: no locale decimal comma, no {@code "-0.0"}.
     */
    private static BigDecimal roundTo(final double value, final int decimals)
    {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
    }

    /**
     * The dragged point, else the hovered one, else {@code -1}.
     */
    private int annotatedPoint()
    {
        if (this.draggedPoint != null && this.draggedPoint >= 0) {
            return this.draggedPoint;
        }
        return this.hoveredPoint == null ? -1 : this.hoveredPoint;
    }

    /**
     * Labels the annotated point with its raw value and color index, with guides to both color bars.
     */
    private void drawHoverHint(final Graphics2D g)
    {
        if (!this.editMode || this.isDiscrete()) {
            return;
        }
        final int index = this.annotatedPoint();
        final Curve curve = this.model.getCurve();
        if (index < 0 || index >= curve.getPointCount()) {
            return;
        }

        final int px = this.curveXToPixelX(curve.getX(index));
        final int py = this.curveYToPixelY(curve.getY(index));

        g.setColor(POINT_BORDER);
        g.setStroke(GUIDE_STROKE);
        g.drawLine(LABEL_WIDTH, py, px, py);
        g.drawLine(px, py, px, this.transformBarBottom());

        final double value = this.rangeMin + curve.getX(index) * (this.rangeMax - this.rangeMin);
        // the fraction is the blend between neighbouring fixes, so its precision is independent of the fix count
        final String colorIndex = roundTo(this.curveYToColorIndex(curve.getY(index)), 2).toPlainString();
        this.drawChip(g, px, py, axisValueLabel(value, this.rangeMin, this.rangeMax) + "  →  " + colorIndex);
    }

    /**
     * Right of the point unless that would run off the plot.
     */
    private void drawChip(final Graphics2D g, final int px, final int py, final String text)
    {
        final FontMetrics fm = g.getFontMetrics();
        final int w = fm.stringWidth(text) + 14;
        final int h = fm.getHeight() + 4;
        final int gap = POINT_RADIUS + 5;
        final int x = px + gap + w <= this.plotRight() ? px + gap : px - gap - w;
        final int y = Math.max(this.plotTop(), Math.min(this.plotBottom() - h, py - h / 2));

        g.setColor(CHIP_BACKGROUND);
        g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(text, x + 7, y + fm.getAscent() + 2);
    }

    private static String formatValue(final double value)
    {
        if (Math.abs(value - Math.round(value)) < 1e-6) {
            return Long.toString(Math.round(value));
        }
        return String.format("%.2f", value);
    }

    @Override
    public void mousePressed(final MouseEvent e)
    {
        if (!this.editMode || this.isDiscrete()) {
            return;
        }

        final Curve curve = this.model.getCurve();
        final double x = this.valueToCurveX(this.pixelXToValue(e.getX()));
        final double y = this.pixelYToCurveY(e.getY());

        if (e.getButton() == MouseEvent.BUTTON1) {
            this.draggedPoint = curve.findNearestPoint(x, y, 0.05);
            if (this.draggedPoint < 0) {
                curve.addPoint(x, y);
                this.draggedPoint = curve.findNearestPoint(x, y, 0.01);
            }
            this.model.notifyCurveEdited();
            this.repaint();
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            final int idx = curve.findNearestPoint(x, y, 0.05);
            if (idx >= 0) {
                curve.removePoint(idx);
                this.hoveredPoint = null;
                this.model.notifyCurveEdited();
                this.repaint();
            }
        }
    }

    @Override
    public void mouseDragged(final MouseEvent e)
    {
        if (this.editMode && !this.isDiscrete() && this.draggedPoint != null && this.draggedPoint >= 0) {
            this.model.getCurve().setPoint(this.draggedPoint, this.pixelYToCurveY(e.getY()));
            this.model.notifyCurveEdited();
            this.repaint();
        }
    }

    @Override
    public void mouseReleased(final MouseEvent e)
    {
        this.draggedPoint = null;
        this.updateHoveredPoint(e);
    }

    @Override
    public void mouseClicked(final MouseEvent e)
    {
    }

    @Override
    public void mouseEntered(final MouseEvent e)
    {
    }

    @Override
    public void mouseExited(final MouseEvent e)
    {
        if (this.hoveredPoint != null) {
            this.hoveredPoint = null;
            this.repaint();
        }
    }

    @Override
    public void mouseMoved(final MouseEvent e)
    {
        this.updateHoveredPoint(e);
    }

    /**
     * Repaints only when the hovered point changes; repainting is expensive.
     */
    private void updateHoveredPoint(final MouseEvent e)
    {
        if (!this.editMode || this.isDiscrete()) {
            return;
        }
        final double x = this.valueToCurveX(this.pixelXToValue(e.getX()));
        final double y = this.pixelYToCurveY(e.getY());
        final int found = this.model.getCurve().findNearestPoint(x, y, 0.04);
        final Integer updated = found < 0 ? null : found;
        if (!Objects.equals(updated, this.hoveredPoint)) {
            this.hoveredPoint = updated;
            this.repaint();
        }
    }

    /**
     * Drawn rather than loaded, so it greys out when disabled.
     */
    private static class PencilIcon implements Icon {
        private static final int SIZE = 16;

        private static final Color ENABLED_COLOR = new Color(60, 60, 60);

        private static final Color DISABLED_COLOR = new Color(168, 168, 168);

        @Override
        public void paintIcon(final Component c, final Graphics g, final int x, final int y)
        {
            final Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(x, y);
                g2.setColor(c.isEnabled() ? ENABLED_COLOR : DISABLED_COLOR);
                g2.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                final Path2D.Double body = new Path2D.Double();
                body.moveTo(11.1, 2.2);
                body.lineTo(13.8, 4.9);
                body.lineTo(5.7, 13.0);
                body.lineTo(2.2, 13.8);
                body.lineTo(3.0, 10.3);
                body.closePath();
                g2.draw(body);
                g2.draw(new Line2D.Double(9.8, 3.5, 12.5, 6.2));
                g2.draw(new Line2D.Double(2.2, 13.8, 4.6, 12.6));
            } finally {
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
