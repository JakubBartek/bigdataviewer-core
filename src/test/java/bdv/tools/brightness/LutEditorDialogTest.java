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
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.Test;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.palette.PaletteWrapper;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerStateChangeListener;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.DoubleType;
import org.scijava.listeners.Listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

/**
 * Lifecycle of the editor window, as opposed to the colour mapping it edits
 * (which is covered by {@link LutEditorMappingTest} and the converter tests).
 * <p>
 * The dialog follows the viewer's current-source selection by listening to the
 * {@code ViewerState}, and the notifications it gets are handed to the EDT
 * rather than acted on where they arrive. Both halves outlive the viewer if
 * nothing stops them, and the viewer is dropped before the dialog is collected
 * -- {@code BdvHandle.close()} nulls it out -- so "the two become unreachable
 * together" is not enough on its own. What is asserted here is the contract
 * that makes it safe: after {@code dispose()}, the dialog does not touch the
 * viewer again, no matter what the state goes on to announce.
 */
public class LutEditorDialogTest
{
	/** A source that answers only what {@link LutEditorDialog} asks of it; nothing here is ever rendered. */
	private static class TypeOnlySource implements Source< DoubleType >
	{
		@Override
		public boolean isPresent( final int t )
		{
			return false;
		}

		@Override
		public RandomAccessibleInterval< DoubleType > getSource( final int t, final int level )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public RealRandomAccessible< DoubleType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{}

		@Override
		public DoubleType getType()
		{
			return new DoubleType();
		}

		@Override
		public String getName()
		{
			return "test";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return null;
		}

		@Override
		public int getNumMipmapLevels()
		{
			return 1;
		}
	}

	/**
	 * A source the dialog can bind a session to: it has to be rendering
	 * through a {@link PaletteConverter} already, or the dialog would offer to
	 * convert it and bind to nothing.
	 */
	private static SourceAndConverter< DoubleType > paletteSoc()
	{
		final PaletteWrapper wrapper = new PresetPaletteWrapper(
				new ContinuousColorScheme( new int[] { 0xffff0000, 0xff00ff00, 0xff0000ff } ), new LinearPresetFunc( 0f, 1f, 2 ) );
		return new SourceAndConverter<>( new TypeOnlySource(), new PaletteConverter<>( wrapper, 0, 255 ) );
	}

	private static void flushEdt() throws Exception
	{
		// Twice: the listener's own invokeLater is queued from whatever thread
		// the state change came from, so one flush only guarantees that the
		// queueing has happened, not the queued work.
		SwingUtilities.invokeAndWait( () -> {} );
		SwingUtilities.invokeAndWait( () -> {} );
	}

	/**
	 * A state change announced after {@code dispose()} must not reach the
	 * viewer. Against a dialog that only calls {@code super.dispose()} this
	 * fails: the removal leaves no current source, the dialog starts a session
	 * on nothing, and reverting the converter it was last bound to drives a
	 * repaint of a viewer its owner has already discarded.
	 */
	@Test
	public void testDisposedDialogDoesNotTouchTheViewer() throws Exception
	{
		assumeFalse( GraphicsEnvironment.isHeadless() );

		final BasicViewerState state = new BasicViewerState();
		final ConverterSetups setups = new ConverterSetups( state );
		final AtomicInteger repaints = new AtomicInteger();

		final SourceAndConverter< DoubleType > soc = paletteSoc();
		state.addSource( soc );
		state.setCurrentSource( soc );

		final LutEditorDialog dialog = new LutEditorDialog( null, setups, state, repaints::incrementAndGet );
		flushEdt();
		final int repaintsWhileAlive = repaints.get();

		dialog.dispose();

		state.removeSource( soc );
		flushEdt();

		assertEquals( repaintsWhileAlive, repaints.get() );
	}

	/**
	 * The listener itself is gone, not merely ignored -- a disposed dialog
	 * left registered on a long-lived {@code ViewerState} would keep itself
	 * and everything it edits alive for as long as the viewer runs.
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

	/** {@code Listeners} has no size of its own; {@code Listeners.List}, which is what a {@code BasicViewerState} holds, can be asked for a copy. */
	private static int countChangeListeners( final BasicViewerState state )
	{
		return ( ( Listeners.List< ViewerStateChangeListener > ) state.changeListeners() ).listCopy().size();
	}
}
