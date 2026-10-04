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

import javax.swing.JComboBox;

import org.junit.Test;

import bdv.tools.brightness.PaletteConverterFactoryTest.TypeOnlySource;
import bdv.tools.brightness.colorscheme.LegacyBdvColorPalette;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.listeners.Listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * Lifecycle of the editor window and the contents of its palette chooser, as
 * opposed to the colour mapping it edits (which is covered by
 * {@link LutEditorMappingTest} and the converter tests).
 * <p>
 * The dialog follows the viewer's current-source selection by listening to the
 * {@code ViewerState}, which usually outlives it; what is asserted here is
 * that {@code dispose()} lets go of that state again.
 */
public class LutEditorDialogTest
{
	private static final Palette RED_TO_BLUE = new Palette( new int[] { ARGBType.rgba( 255, 0, 0, 255 ), ARGBType.rgba( 0, 0, 255, 255 ) }, true );

	private static final Palette GREEN_TO_RED = new Palette( new int[] { ARGBType.rgba( 0, 255, 0, 255 ), ARGBType.rgba( 255, 0, 0, 255 ) }, true );

	/**
	 * A disposed dialog left registered on a long-lived {@code ViewerState}
	 * would keep itself and everything it edits alive for as long as the
	 * viewer runs.
	 */
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

	// -- converting a legacy source ------------------------------------------

	/**
	 * Showing the editor on a source with a single-color converter converts it
	 * and opens on the palette it is now rendered with, listed and selected in
	 * the chooser -- not on the neutral gray, which the first edit would push
	 * over the source's color.
	 */
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

	/**
	 * imglib2's {@code RealARGBColorConverter} is a class copy in a class
	 * loader of its own, which {@code Class.getSimpleName()} throws on -- the
	 * status line has to name it without asking.
	 */
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
