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

import org.junit.Test;

import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.ViewerStateChangeListener;
import org.scijava.listeners.Listeners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

/**
 * Lifecycle of the editor window, as opposed to the colour mapping it edits
 * (which is covered by {@link LutEditorMappingTest} and the converter tests).
 * <p>
 * The dialog follows the viewer's current-source selection by listening to the
 * {@code ViewerState}, which usually outlives it; what is asserted here is
 * that {@code dispose()} lets go of that state again.
 */
public class LutEditorDialogTest
{
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

	/** {@code Listeners} has no size of its own; {@code Listeners.List}, which is what a {@code BasicViewerState} holds, can be asked for a copy. */
	private static int countChangeListeners( final BasicViewerState state )
	{
		return ( ( Listeners.List< ViewerStateChangeListener > ) state.changeListeners() ).listCopy().size();
	}
}
