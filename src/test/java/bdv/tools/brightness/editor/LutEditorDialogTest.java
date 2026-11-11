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

import java.awt.GraphicsEnvironment;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.junit.Test;

import bdv.tools.brightness.ConverterSetup.SetupChangeListener;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.converter.BoundaryCondition;
import bdv.tools.brightness.converter.ColorSchemeConverter;
import bdv.tools.brightness.converter.ColorSchemeConverterFactoryTest.TypeOnlySource;
import bdv.tools.brightness.converter.PresetColorSchemeWrapper;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.LegacyBdvColorScheme;
import bdv.tools.brightness.colorscheme.ColorSchemeFactory;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.listeners.Listeners;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Window lifecycle and color scheme chooser contents; the mapping itself is
 * covered by {@link LutEditorMappingTest} and the converter tests.
 */
public class LutEditorDialogTest
{
	private static final ContinuousColorScheme RED_TO_BLUE = new ContinuousColorScheme( new int[] { ARGBType.rgba( 255, 0, 0, 255 ), ARGBType.rgba( 0, 0, 255, 255 ) } );

	private static final ContinuousColorScheme GREEN_TO_RED = new ContinuousColorScheme( new int[] { ARGBType.rgba( 0, 255, 0, 255 ), ARGBType.rgba( 255, 0, 0, 255 ) } );

	/** Otherwise the long-lived {@code ViewerState} keeps the dialog alive. */
	@Test
	public void testDisposeUnregistersTheStateListener()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final BasicViewerState state = new BasicViewerState();
		final ConverterSetups setups = new ConverterSetups( state );

		final int before = countChangeListeners( state );
		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		assertEquals( before + 1, countChangeListeners( state ) );

		dialog.dispose();
		assertEquals( before, countChangeListeners( state ) );
	}

	/** Same as for the {@code ViewerState}: the {@code ConverterSetups} lives as long as the viewer. */
	@Test
	public void testDisposeUnregistersTheSetupListener()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final BasicViewerState state = new BasicViewerState();
		final ConverterSetups setups = new ConverterSetups( state );

		final int before = countSetupListeners( setups );
		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		assertEquals( before + 1, countSetupListeners( setups ) );

		dialog.dispose();
		assertEquals( before, countSetupListeners( setups ) );
	}

	// -- following the display range -----------------------------------------

	@Test
	public void testFollowsADisplayRangeChangedElsewhere() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = schemeSource( 10, 210 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( 0, ( ColorConverter ) soc.getConverter() );
		setups.put( soc, setup );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			assertEquals( 10, dialog.getEditedRangeMin(), 0 );
			assertEquals( 210, dialog.getEditedRangeMax(), 0 );

			setup.setDisplayRange( 50, 100 );
			SwingUtilities.invokeAndWait( () -> {} );
			assertEquals( 50, dialog.getEditedRangeMin(), 0 );
			assertEquals( 100, dialog.getEditedRangeMax(), 0 );

			dialog.getColorSchemeCombo().setSelectedItem( "viridis" );
			assertEquals( 50, setup.getDisplayRangeMin(), 0 );
			assertEquals( 100, setup.getDisplayRangeMax(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** Another source's range is not this editor's business. */
	@Test
	public void testIgnoresTheDisplayRangeOfAnotherSource() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > edited = schemeSource( 10, 210 );
		final SourceAndConverter< DoubleType > other = schemeSource( 10, 210 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( edited );
		state.addSource( other );
		state.setCurrentSource( edited );
		final ConverterSetups setups = new ConverterSetups( state );
		setups.put( edited, new RealARGBColorConverterSetup( 0, ( ColorConverter ) edited.getConverter() ) );
		final RealARGBColorConverterSetup otherSetup = new RealARGBColorConverterSetup( 1, ( ColorConverter ) other.getConverter() );
		setups.put( other, otherSetup );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			otherSetup.setDisplayRange( 50, 100 );
			SwingUtilities.invokeAndWait( () -> {} );
			assertEquals( 10, dialog.getEditedRangeMin(), 0 );
			assertEquals( 210, dialog.getEditedRangeMax(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	// -- discrete scheme defaults --------------------------------------------

	/** Continuous to discrete resets to min 1, step 1; discrete to discrete keeps them. */
	@Test
	public void testDiscreteSchemeStartsAtMinOneStepOne() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = schemeSource( 10, 210 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( 0, ( ColorConverter ) soc.getConverter() );
		setups.put( soc, setup );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			dialog.getColorSchemeCombo().setSelectedItem( "tab10" );
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
			assertEquals( 210, dialog.getEditedRangeMax(), 0 );
			assertEquals(1, dialog.getMapping().getStepSize(), 0);
			assertEquals( 1, setup.getDisplayRangeMin(), 0 );
			assertEquals( 210, setup.getDisplayRangeMax(), 0 );

			setup.setDisplayRange( 5, 100 );
			SwingUtilities.invokeAndWait( () -> {} );
			dialog.getColorSchemeCombo().setSelectedItem( "tab20" );
			assertEquals( 5, dialog.getEditedRangeMin(), 0 );

			dialog.getColorSchemeCombo().setSelectedItem( "viridis" );
			assertEquals( 5, dialog.getEditedRangeMin(), 0 );
			dialog.getColorSchemeCombo().setSelectedItem( "tab10" );
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	@Test
	public void testPickingADiscreteSchemeResetsTheCurveToLinear()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.getColorSchemeCombo().setSelectedItem( "viridis" );
			dialog.getMapping().applyPreset( PresetShape.SIGMOID );
			dialog.getMapping().getCurve().addPoint( 0.3, 0.8 );

			dialog.getColorSchemeCombo().setSelectedItem( "tab10" );

			final Curve curve = dialog.getMapping().getCurve();
			assertSame( PresetShape.LINEAR, PresetShape.matching( curve.xsArray(), curve.ysArray() ) );
			assertEquals( 2, dialog.getMapping().getCurve().getPointCount() );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** Regression: picking a scheme re-seeded the curve from its preset shape. */
	@Test
	public void testPickingAContinuousSchemeKeepsAHandEditedCurve()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.getColorSchemeCombo().setSelectedItem( "viridis" );
			dialog.getMapping().getCurve().addPoint( 0.3, 0.8 );
			dialog.getMapping().notifyCurveEdited();

			dialog.getColorSchemeCombo().setSelectedItem( "magma" );

			assertEquals( 3, dialog.getMapping().getCurve().getPointCount() );
			assertEquals( 0.8, dialog.getMapping().getCurve().getY( 1 ), 0.0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

    @Test
    public void testEditingTheCurveShowsCustomPreset()
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final LutEditorDialog dialog = new LutEditorDialog(null, new ConverterSetups(new BasicViewerState()), new BasicViewerState(), () -> {});
        try {
            final JComboBox<Object> combo = dialog.getMappingPresetCombo();
            dialog.getColorSchemeCombo().setSelectedItem("viridis");
            combo.setSelectedItem(PresetShape.LOG);
            assertSame(PresetShape.LOG, combo.getSelectedItem());

            dialog.getMapping().getCurve().addPoint(0.3, 0.8);
            dialog.getMapping().notifyCurveEdited();
            assertEquals("Custom", combo.getSelectedItem());

            final double[] editedYs = dialog.getMapping().getCurve().ysArray();
            combo.setSelectedItem("Custom");
            assertArrayEquals(editedYs, dialog.getMapping().getCurve().ysArray(), 0.0);

            combo.setSelectedItem(PresetShape.SIGMOID);
            assertSame(PresetShape.SIGMOID, combo.getSelectedItem());
            assertEquals(PresetShape.values().length, combo.getItemCount());

            dialog.getMapping().invertCurve();
            assertEquals("Custom", combo.getSelectedItem());
        } finally {
            dialog.dispose();
        }
    }

	/** Every kind of switch reaches the converter, not only the one into a discrete scheme. */
	@Test
	public void testPickingASchemePushesItToTheConverter() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = schemeSource( 10, 210 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		setups.put( soc, new RealARGBColorConverterSetup( 0, ( ColorConverter ) soc.getConverter() ) );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			for ( final String name : new String[] { "viridis", "magma", "tab10", "tab20", "viridis" } )
			{
				dialog.getColorSchemeCombo().setSelectedItem( name );
				final ColorSchemeConverter< ? > converter = ( ColorSchemeConverter< ? > ) soc.getConverter();
				assertEquals( name, ColorSchemeFactory.loadFromJson( name ), converter.getWrapper().getColorScheme() );
			}
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** A range that min 1 would leave empty -- a float source's default {@code [0, 1]} -- is widened to where the scheme runs out. */
	@Test
	public void testDiscreteDefaultsWidenAnEmptiedRange() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = schemeSource( 0, 1 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		setups.put( soc, new RealARGBColorConverterSetup( 0, ( ColorConverter ) soc.getConverter() ) );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			dialog.getColorSchemeCombo().setSelectedItem( "tab10" ); // 10 colors
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
			assertEquals( 11, dialog.getEditedRangeMax(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	// -- addColorScheme ------------------------------------------------------

	@Test
	public void testAddedSchemesAreListedUnderTheirCategoryInOrder()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addColorScheme( "Mine", "red-blue", RED_TO_BLUE );
			dialog.addColorScheme( "Other", "green-red", GREEN_TO_RED );
			dialog.addColorScheme( "Mine", "blue-red", new ContinuousColorScheme( new int[] { RED_TO_BLUE.getFixesRef()[ 1 ], RED_TO_BLUE.getFixesRef()[ 0 ] } ) );

			final List< String > items = itemsOf( dialog.getColorSchemeCombo() );
			final int mine = items.indexOf( "[Mine]" );
			assertTrue( "category header missing: " + items, mine >= 0 );
			assertEquals( "red-blue", items.get( mine + 1 ) );
			assertEquals( "blue-red", items.get( mine + 2 ) );
			assertEquals( "[Other]", items.get( mine + 3 ) );
			assertEquals( "green-red", items.get( mine + 4 ) );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** A caller meeting the same scheme again -- every source of one color -- must not have to check first. */
	@Test
	public void testAddingTheSameSchemeAgainListsItOnce()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addColorScheme( "Mine", "red-blue", RED_TO_BLUE );
			final int size = dialog.getColorSchemeCombo().getItemCount();
			dialog.addColorScheme( "Mine", "red-blue", new ContinuousColorScheme( RED_TO_BLUE.getFixesRef() ) );
			assertEquals( size, dialog.getColorSchemeCombo().getItemCount() );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** The chooser lists schemes by name alone, so a name already taken -- bundled or added -- cannot name another. */
	@Test
	public void testANameCannotStandForTwoSchemes()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addColorScheme( "Mine", "red-blue", RED_TO_BLUE );
			assertRejected( () -> dialog.addColorScheme( "Mine", "red-blue", GREEN_TO_RED ) );
			assertRejected( () -> dialog.addColorScheme( "Mine", "viridis", GREEN_TO_RED ) );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** Picking an added scheme goes through the same listener as picking a bundled one, which has to find it. */
	@Test
	public void testPickingAnAddedSchemeEditsIt()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addColorScheme( "Mine", "red-blue", RED_TO_BLUE );
			dialog.getColorSchemeCombo().setSelectedItem( "red-blue" );
			assertEquals( RED_TO_BLUE, dialog.getCurrentColorScheme() );
		}
		finally
		{
			dialog.dispose();
		}
	}

	// -- converting a legacy source ------------------------------------------

	/** Not the neutral gray, which the first edit would push over the source's color. */
	@Test
	public void testConvertedSourceOpensOnItsLegacyScheme()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final int orange = ARGBType.rgba( 255, 128, 0, 255 );
		final RealARGBColorConverter< DoubleType > legacy = RealARGBColorConverter.create( new DoubleType(), 10, 210 );
		legacy.setColor( new ARGBType( orange ) );
		final SourceAndConverter< DoubleType > soc = new SourceAndConverter<>( new TypeOnlySource<>( new DoubleType() ), legacy );

		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup( 0, legacy );
		setups.put( soc, setup );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );

			assertTrue( soc.getConverter() instanceof ColorSchemeConverter );
			assertSame( setup, setups.getConverterSetup( soc ) );
			assertEquals( new LegacyBdvColorScheme( orange ), dialog.getCurrentColorScheme() );
			assertEquals( "BDV #FF8000", dialog.getColorSchemeCombo().getSelectedItem() );

			final List< String > items = itemsOf( dialog.getColorSchemeCombo() );
			assertEquals( "BDV #FF8000", items.get( items.indexOf( "[Legacy BDV Colors]" ) + 1 ) );
		}
		finally
		{
			dialog.dispose();
		}
	}

    // -- a color set outside the editor ------------------------------------

    /**
     * Must also survive a later display range change, which re-pushes the editor's state.
     */
    @Test
    public void testShowsAColorSetElsewhereWhileOpen() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final SourceAndConverter<DoubleType> soc = schemeSource(10, 210);
        final BasicViewerState state = new BasicViewerState();
        state.addSource(soc);
        state.setCurrentSource(soc);
        final ConverterSetups setups = new ConverterSetups(state);
        final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup(0, (ColorConverter) soc.getConverter());
        setups.put(soc, setup);
        final int green = ARGBType.rgba(0, 255, 0, 255);

        final LutEditorDialog dialog = new LutEditorDialog(null, setups, state, () -> {});
        try {
            dialog.setVisible(true);
            dialog.getColorSchemeCombo().setSelectedItem("viridis");

            setup.setColor(new ARGBType(green));
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(new LegacyBdvColorScheme(green), dialog.getCurrentColorScheme());
            assertEquals("BDV #00FF00", dialog.getColorSchemeCombo().getSelectedItem());

            setup.setDisplayRange(50, 100);
            SwingUtilities.invokeAndWait(() -> {});
            final ColorSchemeConverter<?> converter = (ColorSchemeConverter<?>) soc.getConverter();
            assertEquals(new LegacyBdvColorScheme(green), converter.getWrapper().getColorScheme());
        } finally {
            dialog.dispose();
        }
    }

    /**
     * Regression: a hidden editor kept its session and pushed edits to that source.
     */
    @Test
    public void testHiddenEditorDoesNotEditTheSource()
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer viewer = new Viewer();
        viewer.add(schemeSource(10, 210));
        final ColorSchemeConverter<?> converter = (ColorSchemeConverter<?>) viewer.sources.get(0).getConverter();
        final LutEditorDialog dialog = new LutEditorDialog(null, viewer.setups, viewer.state, () -> {});
        try {
            dialog.setVisible(true);
            dialog.setVisible(false);
            final PresetColorSchemeWrapper whenHidden = converter.getWrapper();

            dialog.getColorSchemeCombo().setSelectedItem("viridis");

            assertSame(whenHidden, converter.getWrapper());
        } finally {
            dialog.dispose();
        }
    }

    /**
     * {@code Window.dispose()} hides without calling {@code setVisible(false)}.
     */
    @Test
    public void testDisposedEditorDoesNotEditTheSource()
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer viewer = new Viewer();
        viewer.add(schemeSource(10, 210));
        final ColorSchemeConverter<?> converter = (ColorSchemeConverter<?>) viewer.sources.get(0).getConverter();
        final LutEditorDialog dialog = new LutEditorDialog(null, viewer.setups, viewer.state, () -> {});
        dialog.setVisible(true);
        dialog.dispose();
        final PresetColorSchemeWrapper whenDisposed = converter.getWrapper();

        dialog.getColorSchemeCombo().setSelectedItem("viridis");

        assertSame(whenDisposed, converter.getWrapper());
    }

    @Test
    public void testHidingKeepsATypedButUncommittedStepSize()
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer viewer = new Viewer();
        viewer.add(schemeSource(10, 210));
        final LutEditorDialog dialog = new LutEditorDialog(null, viewer.setups, viewer.state, () -> {});
        try {
            dialog.setVisible(true);
            dialog.getColorSchemeCombo().setSelectedItem("tab10");
            dialog.getStepSizeField().setText("3");
            dialog.setVisible(false);
            dialog.setVisible(true);

            assertEquals(3, dialog.getMapping().getStepSize(), 0);
        } finally {
            dialog.dispose();
        }
    }

    @Test
    public void testOpensOnAColorSetElsewhereWhileClosed() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final SourceAndConverter<DoubleType> soc = schemeSource(10, 210);
        final BasicViewerState state = new BasicViewerState();
        state.addSource(soc);
        state.setCurrentSource(soc);
        final ConverterSetups setups = new ConverterSetups(state);
        final RealARGBColorConverterSetup setup = new RealARGBColorConverterSetup(0, (ColorConverter) soc.getConverter());
        setups.put(soc, setup);
        final int orange = ARGBType.rgba(255, 128, 0, 255);

        final LutEditorDialog dialog = new LutEditorDialog(null, setups, state, () -> {});
        try {
            dialog.setVisible(true);
            dialog.getColorSchemeCombo().setSelectedItem("viridis");
            dialog.setVisible(false);

            setup.setColor(new ARGBType(orange));
            SwingUtilities.invokeAndWait(() -> {});
            dialog.setVisible(true);

            assertEquals(new LegacyBdvColorScheme(orange), dialog.getCurrentColorScheme());
            assertEquals("BDV #FF8000", dialog.getColorSchemeCombo().getSelectedItem());
        } finally {
            dialog.dispose();
        }
    }

    // -- settings XML --------------------------------------------------------

    /**
     * Every kind of state the editor can leave on a source -- a named continuous
     * scheme with an edited curve and special boundaries, a discrete scheme, a
     * converted legacy color with an edited curve, and the unnamed neutral scheme
     * -- must render identically in a fresh viewer after a save and load.
     */
    @Test
    public void testSettingsRestoreRendersTheSameAsSaved() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer saved = new Viewer();
        saved.add(schemeSource(10, 210));
        saved.add(schemeSource(0, 40));
        saved.add(legacySource(10, 210, ARGBType.rgba(255, 128, 0, 255)));
        saved.add(schemeSource(10, 210));

        final LutEditorDialog editor = new LutEditorDialog(null, saved.setups, saved.state, () -> {});
        final Element root = new Element("Settings");
        try {
            editor.setVisible(true);
            saved.select(0);
            editor.getColorSchemeCombo().setSelectedItem("viridis");
            editCurve(editor);
            editor.getMapping().setLeftBoundaryCondition(BoundaryCondition.SPECIAL);
            editor.getMapping().setLeftSpecialColor(0x00994499);
            editor.getMapping().setRightBoundaryCondition(BoundaryCondition.CYCLE);

            saved.select(1);
            editor.getColorSchemeCombo().setSelectedItem("tab10");
            editor.getMapping().setRightBoundaryCondition(BoundaryCondition.CYCLE);

            saved.select(2);
            editCurve(editor);

            saved.select(3);
            editCurve(editor);

            root.addContent(throughText(editor.toXml()));
        } finally {
            editor.dispose();
        }

        final Viewer loaded = new Viewer();
        loaded.add(schemeSource(0, 255));
        loaded.add(schemeSource(0, 255));
        loaded.add(legacySource(0, 255, ARGBType.rgba(255, 255, 255, 255)));
        loaded.add(schemeSource(0, 255));
        for (int id = 0; id < saved.sources.size(); id++) {
            // what SetupAssignments.restoreFromXml does first
            loaded.setup(id).setDisplayRange(saved.setup(id).getDisplayRangeMin(), saved.setup(id).getDisplayRangeMax());
        }

        final LutEditorDialog restorer = new LutEditorDialog(null, loaded.setups, loaded.state, () -> {});
        try {
            restorer.setVisible(true);
            restorer.restoreFromXml(root);

            assertEquals(ColorSchemeFactory.loadFromJson("viridis"), restorer.getCurrentColorScheme());
            for (int id = 0; id < saved.sources.size(); id++) {
                final double min = saved.setup(id).getDisplayRangeMin();
                final double max = saved.setup(id).getDisplayRangeMax();
                for (double raw = min - 20; raw <= max + 20; raw += 0.25) {
                    assertEquals("setup " + id + ", raw " + raw, render(saved.sources.get(id), raw), render(loaded.sources.get(id), raw));
                }
            }
        } finally {
            restorer.dispose();
        }
    }

    /**
     * Regression: an entry for the editor's older state would override the color on load.
     */
    @Test
    public void testSettingsLeaveOutAColorSetWhileClosed() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer viewer = new Viewer();
        viewer.add(schemeSource(10, 210));
        final LutEditorDialog dialog = new LutEditorDialog(null, viewer.setups, viewer.state, () -> {});
        try {
            dialog.setVisible(true);
            dialog.getColorSchemeCombo().setSelectedItem("viridis");
            dialog.setVisible(false);

            viewer.setup(0).setColor(new ARGBType(ARGBType.rgba(0, 255, 0, 255)));
            SwingUtilities.invokeAndWait(() -> {});

            assertTrue(dialog.toXml().getChildren().isEmpty());
        } finally {
            dialog.dispose();
        }
    }

    @Test
    public void testSettingsSkipAnUnrestorableSetupAndRestoreTheRest() throws Exception
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer saved = new Viewer();
        saved.add(schemeSource(10, 210));
        saved.add(schemeSource(10, 210));
        final LutEditorDialog editor = new LutEditorDialog(null, saved.setups, saved.state, () -> {});
        final Element root = new Element("Settings");
        try {
            editor.setVisible(true);
            saved.select(0);
            editor.getColorSchemeCombo().setSelectedItem("viridis");
            saved.select(1);
            editor.getColorSchemeCombo().setSelectedItem("magma");
            root.addContent(editor.toXml());
        } finally {
            editor.dispose();
        }
        root.getChild("LutEditor").getChildren("ConverterSetup").get(0).getChild("colorSchemeName").setText("no-such-scheme");

        final Viewer loaded = new Viewer();
        loaded.add(schemeSource(10, 210));
        loaded.add(schemeSource(10, 210));
        final PresetColorSchemeWrapper untouched = ((ColorSchemeConverter<?>) loaded.sources.get(0).getConverter()).getWrapper();
        final LutEditorDialog restorer = new LutEditorDialog(null, loaded.setups, loaded.state, () -> {});
        try {
            restorer.restoreFromXml(root);

            assertSame(untouched, ((ColorSchemeConverter<?>) loaded.sources.get(0).getConverter()).getWrapper());
            assertEquals(ColorSchemeFactory.loadFromJson("magma"),
                    ((ColorSchemeConverter<?>) loaded.sources.get(1).getConverter()).getWrapper().getColorScheme());
        } finally {
            restorer.dispose();
        }
    }

    @Test
    public void testSettingsWithoutAnEditorElementChangeNothing()
    {
        assumeFalse(GraphicsEnvironment.isHeadless());

        final Viewer viewer = new Viewer();
        viewer.add(schemeSource(10, 210));
        final PresetColorSchemeWrapper before = ((ColorSchemeConverter<?>) viewer.sources.get(0).getConverter()).getWrapper();
        final LutEditorDialog dialog = new LutEditorDialog(null, viewer.setups, viewer.state, () -> {});
        try {
            dialog.restoreFromXml(new Element("Settings"));
            assertSame(before, ((ColorSchemeConverter<?>) viewer.sources.get(0).getConverter()).getWrapper());
        } finally {
            dialog.dispose();
        }
    }

	/** {@code Class.getSimpleName()} throws on imglib2's class copy. */
	@Test
	public void testConverterKindNamesAClassCopiedConverter()
	{
		assertEquals( "RealARGBColorConverter", LutEditorDialog.converterKind( RealARGBColorConverter.create( new DoubleType(), 0, 255 ) ) );
		assertEquals( "No converter", LutEditorDialog.converterKind( null ) );
	}

	@Test
	public void testLegacySchemeNamesItsColor()
	{
		assertEquals( "BDV #FF8000", LutEditorDialog.legacySchemeName( new LegacyBdvColorScheme( ARGBType.rgba( 255, 128, 0, 255 ) ) ) );
		assertEquals( "BDV #FFFFFF", LutEditorDialog.legacySchemeName( new LegacyBdvColorScheme( ARGBType.rgba( 255, 255, 255, 255 ) ) ) );
		assertEquals( "BDV #00994499", LutEditorDialog.legacySchemeName( new LegacyBdvColorScheme( 0x00994499 ) ) );
	}

	// -- helpers -------------------------------------------------------------

	/** A source the editor can edit as it is, rendered over {@code [min, max]} -- the way {@code BigDataViewer} sets one up. */
	private static SourceAndConverter< DoubleType > schemeSource( final double min, final double max )
	{
		final ContinuousColorScheme scheme = RED_TO_BLUE;
		final PresetColorSchemeWrapper wrapper = new PresetColorSchemeWrapper( scheme, new LinearPresetFunc( min, max, scheme.getRange() ) );
		return new SourceAndConverter<>( new TypeOnlySource<>( new DoubleType() ), new ColorSchemeConverter<>( wrapper, min, max ) );
	}

    private static SourceAndConverter<DoubleType> legacySource(final double min, final double max, final int argb)
    {
        final RealARGBColorConverter<DoubleType> legacy = RealARGBColorConverter.create(new DoubleType(), min, max);
        legacy.setColor(new ARGBType(argb));
        return new SourceAndConverter<>(new TypeOnlySource<>(new DoubleType()), legacy);
    }

    /**
     * Sources with setup ids 0, 1, ... in the order added.
     */
    private static final class Viewer {
        final BasicViewerState state = new BasicViewerState();
        final ConverterSetups setups = new ConverterSetups(this.state);
        final List<SourceAndConverter<DoubleType>> sources = new ArrayList<>();

        void add(final SourceAndConverter<DoubleType> soc)
        {
            this.setups.put(soc, new RealARGBColorConverterSetup(this.sources.size(), (ColorConverter) soc.getConverter()));
            this.state.addSource(soc);
            this.sources.add(soc);
        }

        RealARGBColorConverterSetup setup(final int id)
        {
            return (RealARGBColorConverterSetup) this.setups.getConverterSetup(this.sources.get(id));
        }

        void select(final int id) throws Exception
        {
            this.state.setCurrentSource(this.sources.get(id));
            SwingUtilities.invokeAndWait(() -> {});
        }
    }

    private static void editCurve(final LutEditorDialog dialog)
    {
        dialog.getMapping().getCurve().addPoint(0.3, 0.8);
        dialog.getMapping().notifyCurveEdited();
    }

    private static int render(final SourceAndConverter<DoubleType> soc, final double raw)
    {
        final ARGBType out = new ARGBType();
        soc.getConverter().convert(new DoubleType(raw), out);
        return out.get();
    }

    private static Element throughText(final Element elem) throws Exception
    {
        final String text = new XMLOutputter(Format.getPrettyFormat()).outputString(new Document(elem));
        return new SAXBuilder().build(new StringReader(text)).getRootElement().detach();
    }

	private static int countSetupListeners( final ConverterSetups setups )
	{
		return ( ( Listeners.List< SetupChangeListener > ) setups.listeners() ).listCopy().size();
	}

	/** {@code Listeners} has no size of its own; {@code Listeners.List}, which is what a {@code BasicViewerState} holds, can be asked for a copy. */
	private static int countChangeListeners( final BasicViewerState state )
	{
		return ( ( Listeners.List< ViewerStateChangeListener > ) state.changeListeners() ).listCopy().size();
	}

	/** The combo's items as text, a category header in brackets so it cannot be mistaken for a scheme of the same name. */
	private static List< String > itemsOf( final JComboBox< Object > combo )
	{
		final List< String > items = new ArrayList<>();
		for ( int i = 0; i < combo.getItemCount(); i++ )
		{
			final Object item = combo.getItemAt( i );
			items.add( item instanceof String ? ( String ) item : "[" + item + "]" );
		}
		return items;
	}

	private static void assertRejected( final Runnable add )
	{
		try
		{
			add.run();
		}
		catch ( final IllegalArgumentException expected )
		{
			return;
		}
		throw new AssertionError( "expected IllegalArgumentException" );
	}
}
