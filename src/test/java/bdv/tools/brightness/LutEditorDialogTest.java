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

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

import org.junit.Test;

import bdv.tools.brightness.ConverterSetup.SetupChangeListener;
import bdv.tools.brightness.PaletteConverterFactoryTest.TypeOnlySource;
import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.CustomColorsPalette;
import bdv.tools.brightness.colorscheme.LegacyBdvColorPalette;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Window lifecycle and palette chooser contents; the mapping itself is
 * covered by {@link LutEditorMappingTest} and the converter tests.
 */
public class LutEditorDialogTest
{
	private static final Palette RED_TO_BLUE = new Palette( new int[] { ARGBType.rgba( 255, 0, 0, 255 ), ARGBType.rgba( 0, 0, 255, 255 ) }, true );

	private static final Palette GREEN_TO_RED = new Palette( new int[] { ARGBType.rgba( 0, 255, 0, 255 ), ARGBType.rgba( 255, 0, 0, 255 ) }, true );

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

		final SourceAndConverter< DoubleType > soc = paletteSource( 10, 210 );
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

			dialog.getPaletteCombo().setSelectedItem( "viridis" );
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

		final SourceAndConverter< DoubleType > edited = paletteSource( 10, 210 );
		final SourceAndConverter< DoubleType > other = paletteSource( 10, 210 );
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

	// -- discrete palette defaults ------------------------------------------

	/** Continuous to discrete resets to min 1, step 1; discrete to discrete keeps them. */
	@Test
	public void testDiscretePaletteStartsAtMinOneStepOne() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = paletteSource( 10, 210 );
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
			dialog.getPaletteCombo().setSelectedItem( "tab10" );
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
			assertEquals( 210, dialog.getEditedRangeMax(), 0 );
			assertEquals( 1, dialog.getStepSize(), 0 );
			assertEquals( 1, setup.getDisplayRangeMin(), 0 );
			assertEquals( 210, setup.getDisplayRangeMax(), 0 );

			setup.setDisplayRange( 5, 100 );
			SwingUtilities.invokeAndWait( () -> {} );
			dialog.getPaletteCombo().setSelectedItem( "tab20" );
			assertEquals( 5, dialog.getEditedRangeMin(), 0 );

			dialog.getPaletteCombo().setSelectedItem( "viridis" );
			assertEquals( 5, dialog.getEditedRangeMin(), 0 );
			dialog.getPaletteCombo().setSelectedItem( "tab10" );
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** A range that min 1 would leave empty -- a float source's default {@code [0, 1]} -- is widened to where the palette runs out. */
	@Test
	public void testDiscreteDefaultsWidenAnEmptiedRange() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final SourceAndConverter< DoubleType > soc = paletteSource( 0, 1 );
		final BasicViewerState state = new BasicViewerState();
		state.addSource( soc );
		state.setCurrentSource( soc );
		final ConverterSetups setups = new ConverterSetups( state );
		setups.put( soc, new RealARGBColorConverterSetup( 0, ( ColorConverter ) soc.getConverter() ) );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, () -> {} );
		try
		{
			dialog.setVisible( true );
			dialog.getPaletteCombo().setSelectedItem( "tab10" ); // 10 colors
			assertEquals( 1, dialog.getEditedRangeMin(), 0 );
			assertEquals( 11, dialog.getEditedRangeMax(), 0 );
		}
		finally
		{
			dialog.dispose();
		}
	}

	// -- addPalette ----------------------------------------------------------

	@Test
	public void testAddedPalettesAreListedUnderTheirCategoryInOrder()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addPalette( "Mine", "red-blue", RED_TO_BLUE );
			dialog.addPalette( "Other", "green-red", GREEN_TO_RED );
			dialog.addPalette( "Mine", "blue-red", new Palette( new int[] { RED_TO_BLUE.getStop( 1 ), RED_TO_BLUE.getStop( 0 ) }, true ) );

			final List< String > items = itemsOf( dialog.getPaletteCombo() );
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

	/** A caller meeting the same palette again -- every source of one color -- must not have to check first. */
	@Test
	public void testAddingTheSamePaletteAgainListsItOnce()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addPalette( "Mine", "red-blue", RED_TO_BLUE );
			final int size = dialog.getPaletteCombo().getItemCount();
			dialog.addPalette( "Mine", "red-blue", new Palette( RED_TO_BLUE.getStops(), true ) );
			assertEquals( size, dialog.getPaletteCombo().getItemCount() );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** The chooser lists palettes by name alone, so a name already taken -- bundled or added -- cannot name another. */
	@Test
	public void testANameCannotStandForTwoPalettes()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addPalette( "Mine", "red-blue", RED_TO_BLUE );
			assertRejected( () -> dialog.addPalette( "Mine", "red-blue", GREEN_TO_RED ) );
			assertRejected( () -> dialog.addPalette( "Mine", "viridis", GREEN_TO_RED ) );
		}
		finally
		{
			dialog.dispose();
		}
	}

	/** Picking an added palette goes through the same listener as picking a bundled one, which has to find it. */
	@Test
	public void testPickingAnAddedPaletteEditsIt()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			dialog.addPalette( "Mine", "red-blue", RED_TO_BLUE );
			dialog.getPaletteCombo().setSelectedItem( "red-blue" );
			assertEquals( RED_TO_BLUE, dialog.getCurrentPalette() );
		}
		finally
		{
			dialog.dispose();
		}
	}

	@Test
	public void testCustomColorsAreListedInOrderAndCanBePicked()
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final LutEditorDialog dialog = new LutEditorDialog( null, new ConverterSetups( new BasicViewerState() ), new BasicViewerState(), () -> {} );
		try
		{
			final List< String > items = itemsOf( dialog.getPaletteCombo() );
			final int header = items.indexOf( "[Custom Colors]" );
			assertTrue( "category header missing: " + items, header >= 0 );
			final List< String > classics = new ArrayList<>( CustomColorsPalette.classics().keySet() );
			assertEquals( classics, items.subList( header + 1, header + 1 + classics.size() ) );

			for ( final Map.Entry< String, CustomColorsPalette > classic : CustomColorsPalette.classics().entrySet() )
			{
				dialog.getPaletteCombo().setSelectedItem( classic.getKey() );
				assertEquals( classic.getValue(), dialog.getCurrentPalette() );
			}
		}
		finally
		{
			dialog.dispose();
		}
	}

	// -- converting a legacy source ------------------------------------------

	/** Not the neutral gray, which the first edit would push over the source's color. */
	@Test
	public void testConvertedSourceOpensOnItsLegacyPalette()
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

			assertTrue( soc.getConverter() instanceof PaletteConverter );
			assertSame( setup, setups.getConverterSetup( soc ) );
			assertEquals( new LegacyBdvColorPalette( orange ), dialog.getCurrentPalette() );
			assertEquals( "BDV #FF8000", dialog.getPaletteCombo().getSelectedItem() );

			final List< String > items = itemsOf( dialog.getPaletteCombo() );
			assertEquals( "BDV #FF8000", items.get( items.indexOf( "[Legacy BDV Colors]" ) + 1 ) );
		}
		finally
		{
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
	public void testLegacyPaletteNamesItsColor()
	{
		assertEquals( "BDV #FF8000", LutEditorDialog.legacyPaletteName( new LegacyBdvColorPalette( ARGBType.rgba( 255, 128, 0, 255 ) ) ) );
		assertEquals( "BDV #FFFFFF", LutEditorDialog.legacyPaletteName( new LegacyBdvColorPalette( ARGBType.rgba( 255, 255, 255, 255 ) ) ) );
		assertEquals( "BDV #00994499", LutEditorDialog.legacyPaletteName( new LegacyBdvColorPalette( 0x00994499 ) ) );
	}

	// -- helpers -------------------------------------------------------------

	/** A source the editor can edit as it is, rendered over {@code [min, max]} -- the way {@code BigDataViewer} sets one up. */
	private static SourceAndConverter< DoubleType > paletteSource( final double min, final double max )
	{
		final ContinuousColorScheme scheme = new ContinuousColorScheme( RED_TO_BLUE );
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme, new LinearPresetFunc( min, max, scheme.getPaletteRangeLength() ) );
		return new SourceAndConverter<>( new TypeOnlySource<>( new DoubleType() ), new PaletteConverter<>( wrapper, min, max ) );
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

	/** The combo's items as text, a category header in brackets so it cannot be mistaken for a palette of the same name. */
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
