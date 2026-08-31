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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;

import org.scijava.listeners.Listeners;

public class RealARGBColorConverterSetup implements ConverterSetup
{
	private final int id;

	/**
	 * The converters this setup drives. A mutable copy of what the caller
	 * passed, not the caller's own list, because {@link #setConverters} needs
	 * to be able to replace its contents (and a varargs constructor's
	 * {@link Arrays#asList} would not allow that).
	 */
	private final List< ColorConverter > converters = new ArrayList<>();

	private final Listeners.List< SetupChangeListener > listeners;

	public RealARGBColorConverterSetup( final int setupId, final ColorConverter... converters )
	{
		this( setupId, Arrays.asList( converters ) );
	}

	public RealARGBColorConverterSetup( final int setupId, final List< ColorConverter > converters )
	{
		this.id = setupId;
		this.converters.addAll( converters );
		this.listeners = new Listeners.SynchronizedList<>();
	}

	/**
	 * Point this setup at a different set of converters, keeping its identity.
	 * <p>
	 * Needed when a source's converter is swapped in place (see
	 * {@code SourceAndConverter#setConverter}): this setup would otherwise go
	 * on reading and writing the display range of the converter that is no
	 * longer rendering anything. Replacing the converters rather than building
	 * a fresh setup matters because a {@code ConverterSetup} is an identity
	 * everything else holds on to -- {@code SetupAssignments}, the
	 * brightness dialog's slider groups, {@code ConverterSetupBounds} -- none
	 * of which would follow a substitute.
	 * <p>
	 * The new converters keep whatever display range they already carry; this
	 * does not push the old one onto them.
	 */
	public void setConverters( final List< ColorConverter > converters )
	{
		this.converters.clear();
		this.converters.addAll( converters );
		listeners.list.forEach( l -> l.setupParametersChanged( this ) );
	}

	@Override
	public Listeners< SetupChangeListener > setupChangeListeners()
	{
		return listeners;
	}

	@Override
	public void setDisplayRange( final double min, final double max )
	{
		boolean changed = false;
		for ( final ColorConverter converter : converters )
		{
			if ( converter.getMin() != min )
			{
				converter.setMin( min );
				changed = true;
			}
			if ( converter.getMax() != max )
			{
				converter.setMax( max );
				changed = true;
			}
		}
		if ( changed )
			listeners.list.forEach( l -> l.setupParametersChanged( this ) );
	}

	@Override
	public void setColor( final ARGBType color )
	{
		if ( !supportsColor() )
			return;

		boolean changed = false;
		for ( final ColorConverter converter : converters )
		{
			if ( converter.getColor().get() != color.get() )
			{
				converter.setColor( color );
				changed = true;
			}
		}
		if ( changed )
			listeners.list.forEach( l -> l.setupParametersChanged( this ) );
	}

	@Override
	public boolean supportsColor()
	{
		return converters.get( 0 ).supportsColor();
	}

	@Override
	public int getSetupId()
	{
		return id;
	}

	@Override
	public double getDisplayRangeMin()
	{
		return converters.get( 0 ).getMin();
	}

	@Override
	public double getDisplayRangeMax()
	{
		return converters.get( 0 ).getMax();
	}

	@Override
	public ARGBType getColor()
	{
		return converters.get( 0 ).getColor();
	}
}
