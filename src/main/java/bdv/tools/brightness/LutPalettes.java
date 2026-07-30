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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import net.imglib2.display.ColorTable;

/**
 * Discovers and loads the built-in LUT (color palette) resources. This is the
 * data-access side of the LUT editor, deliberately kept free of any UI so the
 * discovery/parsing computation lives outside the (visual) dialog. The dialog
 * only asks this for names and colors.
 * <p>
 * A LUT resource is a text file, one control point per line, with 5
 * space-separated values {@code position red green blue alpha}, all in
 * [0, 1]. The number of control points is arbitrary (not tied to 256); colors
 * between control points are obtained by linear interpolation (see
 * {@link ColorTableLut}).
 */
public final class LutPalettes
{
	private static final String LUT_RESOURCE_DIR = "bdv/ui/luts";

	private LutPalettes()
	{
	}

	/**
	 * The names of all available LUT resources, sorted case-insensitively.
	 * A name is the resource file name without its {@code .txt} extension,
	 * and is what {@link #load(String)} expects.
	 */
	public static List< String > discoverNames()
	{
		final List< String > names = new ArrayList<>();
		try
		{
			final URL dirUrl = LutPalettes.class.getClassLoader().getResource( LUT_RESOURCE_DIR );
			if ( dirUrl == null )
				return names;
			final URI uri = dirUrl.toURI();
			final TreeMap< String, String > sorted = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
			if ( "jar".equals( uri.getScheme() ) )
			{
				try ( final FileSystem fs = FileSystems.newFileSystem( uri, Collections.emptyMap() );
				      final Stream< Path > paths = Files.walk( fs.getPath( LUT_RESOURCE_DIR ), 1 ) )
				{
					collectNames( paths, sorted );
				}
			} else
			{
				try ( final Stream< Path > paths = Files.walk( Paths.get( uri ), 1 ) )
				{
					collectNames( paths, sorted );
				}
			}
			names.addAll( sorted.keySet() );
		} catch ( final Exception e )
		{
			e.printStackTrace();
		}
		return names;
	}

	private static void collectNames( final Stream< Path > paths, final TreeMap< String, String > sorted )
	{
		paths.filter( p -> p.toString().endsWith( ".txt" ) )
				.forEach( p ->
				{
					final String fn = p.getFileName().toString();
					sorted.put( fn.substring( 0, fn.length() - 4 ), fn );
				} );
	}

	/**
	 * Load the named LUT resource as a {@link ColorTable}, or {@code null} if
	 * it cannot be found or parsed.
	 *
	 * @param name
	 * 		a name as returned by {@link #discoverNames()}.
	 */
	public static ColorTable load( final String name )
	{
		final String path = LUT_RESOURCE_DIR + "/" + name + ".txt";
		try ( final InputStream is = LutPalettes.class.getClassLoader().getResourceAsStream( path ) )
		{
			if ( is == null )
				return null;
			final BufferedReader reader = new BufferedReader( new InputStreamReader( is ) );
			final List< double[] > rows = new ArrayList<>();
			String line;
			while ( ( line = reader.readLine() ) != null )
			{
				line = line.trim();
				if ( line.isEmpty() )
					continue;
				final String[] parts = line.split( "\\s+" );
				if ( parts.length < 4 )
					continue;
				final double position = Double.parseDouble( parts[ 0 ] );
				final double r = Double.parseDouble( parts[ 1 ] );
				final double g = Double.parseDouble( parts[ 2 ] );
				final double b = Double.parseDouble( parts[ 3 ] );
				final double a = parts.length >= 5 ? Double.parseDouble( parts[ 4 ] ) : 1.0;
				rows.add( new double[] { position, r, g, b, a } );
			}
			if ( rows.size() < 2 )
				return null;
			rows.sort( Comparator.comparingDouble( row -> row[ 0 ] ) );

			final int n = rows.size();
			final double[] positions = new double[ n ];
			final double[] red = new double[ n ];
			final double[] green = new double[ n ];
			final double[] blue = new double[ n ];
			final double[] alpha = new double[ n ];
			for ( int i = 0; i < n; i++ )
			{
				final double[] row = rows.get( i );
				positions[ i ] = row[ 0 ];
				red[ i ] = row[ 1 ];
				green[ i ] = row[ 2 ];
				blue[ i ] = row[ 3 ];
				alpha[ i ] = row[ 4 ];
			}
			return new ColorTableLut( positions, red, green, blue, alpha );
		} catch ( final IOException | NumberFormatException e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
