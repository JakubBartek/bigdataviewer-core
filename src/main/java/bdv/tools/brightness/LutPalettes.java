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
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.imglib2.display.ColorTable;

/**
 * Discovers and loads the built-in LUT (color palette) resources. This is the
 * data-access side of the LUT editor, deliberately kept free of any UI so the
 * discovery/parsing computation lives outside the (visual) dialog. The dialog
 * only asks this for names and colors.
 * <p>
 * A LUT resource is a JSON file with a {@code fixes_RGBA} object mapping each
 * color's index (as a string, e.g. {@code "0"}, {@code "1"}, ...) to its
 * {@code [red, green, blue, alpha]} components, all in [0, 1]. The number of
 * colors is arbitrary (not tied to 256); colors between them are obtained by
 * linear interpolation, evenly spaced in index order (see {@link ColorTableLut}).
 * A top-level {@code color_interpolation} boolean declares whether the
 * palette is meant to be smoothly interpolated or used as discrete colors
 * (see {@link ColorTableLut#isInterpolated()}, set on the {@link ColorTable}
 * returned by {@link #load(String)}).
 */
public final class LutPalettes
{
	private static final String LUT_RESOURCE_DIR = "bdv/ui/luts";

	private static final String LUT_RESOURCE_EXTENSION = ".json";

	private LutPalettes()
	{
	}

	/**
	 * The names of all available LUT resources, sorted case-insensitively.
	 * A name is the resource file name without its {@code .json} extension,
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
		paths.filter( p -> p.toString().endsWith( LUT_RESOURCE_EXTENSION ) )
				.forEach( p ->
				{
					final String fn = p.getFileName().toString();
					sorted.put( fn.substring( 0, fn.length() - LUT_RESOURCE_EXTENSION.length() ), fn );
				} );
	}

	/**
	 * Load the named LUT resource as a {@link ColorTable}, or {@code null} if
	 * it cannot be found or parsed. The returned table's
	 * {@link ColorTableLut#isInterpolated()} reflects the resource's
	 * {@code color_interpolation} field (defaulting to {@code true} if the
	 * resource does not declare it).
	 *
	 * @param name
	 * 		a name as returned by {@link #discoverNames()}.
	 */
	public static ColorTable load( final String name )
	{
		final JsonObject root = readRoot( name );
		if ( root == null )
			return null;
		final JsonObject colors = root.getAsJsonObject( "fixes_RGBA" );
		if ( colors == null )
			return null;

		final List< Integer > indices = new ArrayList<>();
		for ( final String key : colors.keySet() )
			indices.add( Integer.parseInt( key ) );
		Collections.sort( indices );

		final int n = indices.size();
		if ( n < 2 )
			return null;

		final double[] positions = new double[ n ];
		final double[] red = new double[ n ];
		final double[] green = new double[ n ];
		final double[] blue = new double[ n ];
		final double[] alpha = new double[ n ];
		for ( int i = 0; i < n; i++ )
		{
			final JsonArray rgba = colors.getAsJsonArray( Integer.toString( indices.get( i ) ) );
			positions[ i ] = i / ( double ) ( n - 1 );
			red[ i ] = rgba.get( 0 ).getAsDouble();
			green[ i ] = rgba.get( 1 ).getAsDouble();
			blue[ i ] = rgba.get( 2 ).getAsDouble();
			alpha[ i ] = rgba.get( 3 ).getAsDouble();
		}
		final boolean interpolated = !root.has( "color_interpolation" ) || root.get( "color_interpolation" ).getAsBoolean();
		return new ColorTableLut( positions, red, green, blue, alpha, interpolated );
	}

	/**
	 * Reverse of {@link #load(String)}: the name of the discovered LUT
	 * resource whose colors exactly match {@code ct}, or {@code null} if none
	 * do (e.g. {@code ct} isn't one of these resources at all, such as
	 * {@link ColorTableLut#DEFAULT} or a palette set up some other way).
	 * Used to recover a display name for a bare {@link ColorTable} read back
	 * from a converter, which doesn't otherwise remember which resource (if
	 * any) it was originally loaded from.
	 */
	public static String findName( final ColorTable ct )
	{
		if ( !( ct instanceof ColorTableLut ) )
			return null;
		for ( final String name : discoverNames() )
		{
			final ColorTable candidate = load( name );
			if ( candidate != null && ( ( ColorTableLut ) ct ).hasSameColors( candidate ) )
				return name;
		}
		return null;
	}

	/**
	 * Read and parse the named LUT resource's root JSON object, or
	 * {@code null} if it cannot be found or parsed.
	 */
	private static JsonObject readRoot( final String name )
	{
		final String path = LUT_RESOURCE_DIR + "/" + name + LUT_RESOURCE_EXTENSION;
		try ( final InputStream is = LutPalettes.class.getClassLoader().getResourceAsStream( path ) )
		{
			if ( is == null )
				return null;
			return JsonParser.parseReader( new InputStreamReader( is ) ).getAsJsonObject();
		} catch ( final IOException | RuntimeException e )
		{
			e.printStackTrace();
			return null;
		}
	}
}
