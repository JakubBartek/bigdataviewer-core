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
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.tools.brightness;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import static java.lang.Math.abs;

/**
 * Interactive panel for editing the range remapping.
 * Displays intervals as colored blocks on a horizontal bar.
 * Users can:
 * - Click on a block to select it
 * - Drag boundaries between blocks to resize intervals
 * - Use buttons to split, merge, and shuffle intervals
 */
public class RangeRemapPanel extends JPanel
{
	private final RangeRemapModel model;
	private final IntervalBar intervalBar;
	private int selectedInterval = 0;

	private final JSpinner splitSpinner = new JSpinner( new SpinnerNumberModel( 127, 0, 255, 1 ) );

	private static final Color[] INTERVAL_COLORS = {
			new Color( 70, 130, 180 ),
			new Color( 180, 100, 70 ),
			new Color( 70, 180, 100 ),
			new Color( 180, 70, 160 ),
			new Color( 180, 180, 70 ),
			new Color( 70, 180, 180 ),
			new Color( 130, 70, 180 ),
			new Color( 180, 130, 70 ),
	};

	public RangeRemapPanel( final RangeRemapModel model )
	{
		this.model = model;
		setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );

		// Interval bar visualization
		intervalBar = new IntervalBar();
		intervalBar.setPreferredSize( new Dimension( 300, 40 ) );
		intervalBar.setMinimumSize( new Dimension( 100, 40 ) );
		intervalBar.setBorder( BorderFactory.createTitledBorder( BorderFactory.createEmptyBorder(), "Range Remap" ) );
		add( intervalBar );

		// Controls
		final JPanel controls = new JPanel( new FlowLayout( FlowLayout.LEFT, 4, 8 ) );

		final JButton btnSplit = new JButton( "Split" );
		btnSplit.setToolTipText( "Split selected interval at the given position" );
		splitSpinner.setPreferredSize( new Dimension( 60, 25 ) );

		final JButton btnMoveLeft = new JButton( "\u25C0" );
		btnMoveLeft.setToolTipText( "Move selected interval left in the ordering" );
		final JButton btnMoveRight = new JButton( "\u25B6" );
		btnMoveRight.setToolTipText( "Move selected interval right in the ordering" );
		final JButton btnInvert = new JButton( "Invert" );
		btnInvert.setToolTipText( "Invert selected interval (reverse its direction)" );
		final JButton btnMerge = new JButton( "Delete" );
		btnMerge.setToolTipText( "Merge selected interval with the next one" );
		final JButton btnReset = new JButton( "Reset" );
		btnReset.setToolTipText( "Reset to single [0, 255] interval" );

		final JLabel infoLabel = new JLabel(); // Not being used at the moment due to visual information overflow

		controls.add( btnSplit );
		controls.add( new JLabel( "at:" ) );
		controls.add( splitSpinner );
		controls.add( btnMoveLeft );
		controls.add( btnMoveRight );
		controls.add( btnMerge );
		controls.add( btnInvert );
		controls.add( btnReset );
		add( controls );

		final JPanel infoPanel = new JPanel( new FlowLayout( FlowLayout.LEFT, 4, 0 ) );
		infoPanel.add( infoLabel );
		add( infoPanel );

		// Actions
		btnSplit.addActionListener( e ->
		{
			final int splitAt = ( int ) splitSpinner.getValue();
			model.splitInterval( selectedInterval, splitAt );
			splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );
			intervalBar.repaint();
			updateInfo( infoLabel );
		} );

		btnMoveLeft.addActionListener( e ->
		{
			if ( selectedInterval > 0 )
			{
				model.swapIntervals( selectedInterval, selectedInterval - 1 );
				selectedInterval--;
				splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );
				intervalBar.repaint();
				updateInfo( infoLabel );
			}
		} );

		btnMoveRight.addActionListener( e ->
		{
			if ( selectedInterval < model.getIntervals().size() - 1 )
			{
				model.swapIntervals( selectedInterval, selectedInterval + 1 );
				selectedInterval++;
				splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );
				intervalBar.repaint();
				updateInfo( infoLabel );
			}
		} );

		btnMerge.addActionListener( e ->
		{
			if ( selectedInterval < model.getIntervals().size() - 1 )
			{
				model.mergeWithNext( selectedInterval );
				splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );
				intervalBar.repaint();
				updateInfo( infoLabel );
			}
		} );

		btnInvert.addActionListener( e ->
		{
			model.invertInterval( selectedInterval );
			intervalBar.repaint();
			updateInfo( infoLabel );
		} );

		btnReset.addActionListener( e ->
		{
			model.reset();
			selectedInterval = 0;
			intervalBar.repaint();
			updateInfo( infoLabel );
		} );

		model.addChangeListener( () ->
		{
			splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );
			intervalBar.repaint();
			updateInfo( infoLabel );
		} );

		updateInfo( infoLabel );
	}

	private void updateInfo( final JLabel label )
	{
		final List< RangeRemapModel.Interval > intervals = model.getIntervals();
		if ( selectedInterval >= intervals.size() )
			selectedInterval = intervals.size() - 1;
		if ( selectedInterval < 0 )
			selectedInterval = 0;
	}

	/**
	 * The visual bar showing intervals as colored blocks.
	 */
	private class IntervalBar extends JPanel implements MouseListener, MouseMotionListener
	{
		private int dragBoundaryIndex = -1;
		private int hoverBoundaryIndex = -1;

		private int dragStartMouseX = 0;
		private int dragStartLowerCount = 0;

		IntervalBar()
		{
			addMouseListener( this );
			addMouseMotionListener( this );
			setBackground( new Color( 40, 40, 40 ) );
		}

		@Override
		protected void paintComponent( final Graphics g )
		{
			super.paintComponent( g );
			final Graphics2D g2 = ( Graphics2D ) g;
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

			final int w = getWidth();
			final int h = getHeight();
			final List< RangeRemapModel.Interval > intervals = model.getIntervals();

			// Compute total size (should be 256)
			int totalSize = 0;
			for ( final RangeRemapModel.Interval iv : intervals )
				totalSize += iv.size();
			if ( totalSize == 0 )
				totalSize = 1;

			// Draw each interval block proportionally
			int xPos = 0;
			for ( int i = 0; i < intervals.size(); i++ )
			{
				final RangeRemapModel.Interval iv = intervals.get( i );
				final int blockWidth = Math.max( 1, ( int ) Math.round( ( double ) iv.size() / totalSize * w ) );

				// Fill
				final Color baseColor = INTERVAL_COLORS[ i % INTERVAL_COLORS.length ];
				if ( i == selectedInterval )
				{
					g2.setColor( baseColor.brighter() );
				} else
				{
					g2.setColor( baseColor );
				}
				g2.fillRect( xPos, 0, blockWidth, h );

				// Label
				g2.setColor( Color.WHITE );
				g2.setFont( new Font( Font.SANS_SERIF, Font.PLAIN, 10 ) );
				final String label = iv.start + "-" + iv.end;
				final int labelWidth = g2.getFontMetrics().stringWidth( label );
				if ( blockWidth > labelWidth + 4 )
				{
					g2.drawString( label, xPos + ( blockWidth - labelWidth ) / 2, h / 2 + 4 );
				}

				// Boundary line
				if ( i > 0 )
				{
					g2.setColor( Color.BLACK );
					g2.setStroke( new BasicStroke( 2 ) );
					g2.drawLine( xPos, 0, xPos, h );
				}

				xPos += blockWidth;
			}

			// Selection indicator
			if ( selectedInterval >= 0 && selectedInterval < intervals.size() )
			{
				int selX = 0;
				for ( int i = 0; i < selectedInterval; i++ )
					selX += Math.max( 1, ( int ) Math.round( ( double ) intervals.get( i ).size() / totalSize * w ) );
				final int selW = Math.max( 1, ( int ) Math.round( ( double ) intervals.get( selectedInterval ).size() / totalSize * w ) );
				g2.setColor( Color.WHITE );
				g2.setStroke( new BasicStroke( 2 ) );
				g2.drawRect( selX, 0, selW - 1, h - 1 );
			}


			int shownBoundary = dragBoundaryIndex >= 0 ? dragBoundaryIndex : hoverBoundaryIndex;

			if ( shownBoundary >= 0 && shownBoundary < intervals.size() - 1 )
			{
				// Find x-position of boundary
				int boundaryX = 0;
				for ( int i = 0; i <= shownBoundary; i++ )
				{
					boundaryX += Math.max( 1,
							( int ) Math.round( ( double ) intervals.get( i ).size() / totalSize * w ) );
				}

				RangeRemapModel.Interval left = intervals.get( shownBoundary );
				RangeRemapModel.Interval right = intervals.get( shownBoundary + 1 );
				int boundaryValue = left.isInverted() ? left.low() : left.high();
				int boundaryValue2 = right.isInverted() ? right.high() : right.low();
				String text;

				// Check if the boundary is in the middle of the interval
				if ( abs( boundaryValue - boundaryValue2 ) <= 1 )
				{
					text = Integer.toString( boundaryValue );
				} else
				{
					text = "Can't adjust non-adjacent intervals";
				}


				FontMetrics fm = g2.getFontMetrics();
				int tw = fm.stringWidth( text );
				int th = fm.getHeight();

				int boxX = boundaryX - tw / 2 - 4;
				int boxY = 2;

				g2.setColor( new Color( 0, 0, 0 ) );
				g2.fillRoundRect( boxX, boxY, tw + 8, th + 4, 6, 6 );

				g2.setColor( Color.WHITE );
				g2.drawRoundRect( boxX, boxY, tw + 8, th + 4, 6, 6 );

				g2.drawString( text, boxX + 4, boxY + fm.getAscent() + 2 );
			}

			// Border
			g2.setColor( Color.BLACK );
			g2.setStroke( new BasicStroke( 1 ) );
			g2.drawRect( 0, 0, w - 1, h - 1 );
		}

		@Override
		public void mousePressed( final MouseEvent e )
		{
			final int w = getWidth();
			final List< RangeRemapModel.Interval > intervals = model.getIntervals();
			int totalSize = 0;
			for ( final RangeRemapModel.Interval iv : intervals )
				totalSize += iv.size();
			if ( totalSize == 0 )
				return;

			// Check if clicking near a boundary (for dragging)
			int xPos = 0;
			for ( int i = 0; i < intervals.size(); i++ )
			{
				final int blockWidth = Math.max( 1, ( int ) Math.round( ( double ) intervals.get( i ).size() / totalSize * w ) );
				xPos += blockWidth;
				if ( i < intervals.size() - 1 && abs( e.getX() - xPos ) < 5 )
				{
					dragBoundaryIndex = i;
					final RangeRemapModel.Interval left = intervals.get( dragBoundaryIndex );
					final RangeRemapModel.Interval right = intervals.get( dragBoundaryIndex + 1 );
					final boolean leftIsLower = left.low() <= right.low();
					dragStartLowerCount = leftIsLower ? left.size() : right.size();
					dragStartMouseX = e.getX();
					return;
				}
			}

			// Otherwise, select the clicked interval
			xPos = 0;
			for ( int i = 0; i < intervals.size(); i++ )
			{
				final int blockWidth = Math.max( 1, ( int ) Math.round( ( double ) intervals.get( i ).size() / totalSize * w ) );
				if ( e.getX() >= xPos && e.getX() < xPos + blockWidth )
				{
					selectedInterval = i;
					splitSpinner.setValue( ( model.getIntervals().get( selectedInterval ).end + model.getIntervals().get( selectedInterval ).start ) / 2 );

					repaint();
					return;
				}
				xPos += blockWidth;
			}
		}

		@Override
		public void mouseDragged( final MouseEvent e )
		{
			if ( dragBoundaryIndex >= 0 )
			{
				final int w = getWidth();
				final List< RangeRemapModel.Interval > intervals = model.getIntervals();
				if ( dragBoundaryIndex >= intervals.size() - 1 )
					return;

				final RangeRemapModel.Interval left = intervals.get( dragBoundaryIndex );
				final RangeRemapModel.Interval right = intervals.get( dragBoundaryIndex + 1 );
				int boundaryValueLeft = left.isInverted() ? left.low() : left.high();
				int boundaryValueRight = right.isInverted() ? right.high() : right.low();

				// Check if the boundary is in the middle of the interval
				if ( abs( boundaryValueLeft - boundaryValueRight ) > 1 ) return;

				int totalSize = 0;
				for ( final RangeRemapModel.Interval iv : intervals )
					totalSize += iv.size();

				final int combinedLow = Math.min( left.low(), right.low() );
				final int combinedHigh = Math.max( left.high(), right.high() );

				final int combinedSize = left.size() + right.size();
				final int deltaPixels = e.getX() - dragStartMouseX;
				final int deltaNumeric = ( int ) Math.round( ( double ) deltaPixels * totalSize / ( double ) w );

				final int newBoundaryPos = ( left.low() <= right.low() )
						? ( dragStartLowerCount + deltaNumeric )
						: ( dragStartLowerCount - deltaNumeric );

				if ( newBoundaryPos < 1 || newBoundaryPos >= combinedSize )
					return;

				final ArrayList< RangeRemapModel.Interval > newIntervals = new ArrayList<>( intervals );

				final boolean leftIsLower = left.low() <= right.low();

				int newLeftLow, newLeftHigh, newRightLow2, newRightHigh;
				if ( leftIsLower )
				{
					newLeftLow = combinedLow;
					newLeftHigh = combinedLow + newBoundaryPos - 1;
					newRightLow2 = newLeftHigh + 1;
					newRightHigh = combinedHigh;
				} else
				{
					newRightLow2 = combinedLow;
					newRightHigh = combinedLow + newBoundaryPos - 1;
					newLeftLow = newRightHigh + 1;
					newLeftHigh = combinedHigh;
				}

				final RangeRemapModel.Interval newLeft = left.isInverted()
						? new RangeRemapModel.Interval( newLeftHigh, newLeftLow )
						: new RangeRemapModel.Interval( newLeftLow, newLeftHigh );

				final RangeRemapModel.Interval newRight = right.isInverted()
						? new RangeRemapModel.Interval( newRightHigh, newRightLow2 )
						: new RangeRemapModel.Interval( newRightLow2, newRightHigh );

				newIntervals.set( dragBoundaryIndex, newLeft );
				newIntervals.set( dragBoundaryIndex + 1, newRight );

				model.setIntervals( newIntervals );
				repaint();
			}
		}

		@Override
		public void mouseReleased( final MouseEvent e )
		{
			dragBoundaryIndex = -1;
			dragStartMouseX = 0;
			dragStartLowerCount = 0;
		}

		@Override
		public void mouseMoved( final MouseEvent e )
		{
			final int w = getWidth();
			final List< RangeRemapModel.Interval > intervals = model.getIntervals();

			int totalSize = 0;
			for ( final RangeRemapModel.Interval iv : intervals )
				totalSize += iv.size();

			hoverBoundaryIndex = -1;

			int xPos = 0;
			for ( int i = 0; i < intervals.size() - 1; i++ )
			{
				final int blockWidth = Math.max( 1,
						( int ) Math.round( ( double ) intervals.get( i ).size() / totalSize * w ) );
				xPos += blockWidth;

				if ( abs( e.getX() - xPos ) < 5 )
				{
					hoverBoundaryIndex = i;
					setCursor( Cursor.getPredefinedCursor( Cursor.E_RESIZE_CURSOR ) );
					repaint();
					return;
				}
			}

			setCursor( Cursor.getDefaultCursor() );
			repaint();
		}

		@Override
		public void mouseClicked( final MouseEvent e )
		{
		}

		@Override
		public void mouseEntered( final MouseEvent e )
		{
		}

		@Override
		public void mouseExited( MouseEvent e )
		{
			hoverBoundaryIndex = -1;
			setCursor( Cursor.getDefaultCursor() );
			repaint();
		}

		private static final long serialVersionUID = 1L;
	}

	private static final long serialVersionUID = 1L;
}
