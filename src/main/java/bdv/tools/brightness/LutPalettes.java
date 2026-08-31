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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import bdv.tools.brightness.colorscheme.Palette;
import net.imglib2.type.numeric.ARGBType;

/**
 * Discovers and loads the built-in LUT (color palette) resources. This is the
 * data-access side of the LUT editor, deliberately kept free of any UI so the
 * discovery/parsing computation lives outside the (visual) dialog. The dialog
 * only asks this for names and colors.
 * <p>
 * A LUT resource is a JSON file with a {@code fixes_RGBA} array of
 * {@code [red, green, blue, alpha]} components (all in [0, 1]), one per
 * color, in order -- a color's index is simply its position in the array, so
 * colors are always evenly spaced (see {@link Palette}). The number of
 * colors is arbitrary (not tied to 256); colors between them are obtained by
 * linear interpolation. A top-level {@code color_interpolation} boolean declares whether the
 * palette is meant to be smoothly interpolated or used as discrete colors
 * (see {@link Palette#isInterpolated()}, set on the {@link Palette}
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
			final TreeSet< String > sorted = new TreeSet<>( String.CASE_INSENSITIVE_ORDER );
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
			names.addAll( sorted );
		} catch ( final Exception e )
		{
			e.printStackTrace();
		}
		return names;
	}

	private static void collectNames( final Stream< Path > paths, final Set< String > sorted )
	{
		paths.filter( p -> p.toString().endsWith( LUT_RESOURCE_EXTENSION ) )
				.forEach( p ->
				{
					final String fn = p.getFileName().toString();
					sorted.add( fn.substring( 0, fn.length() - LUT_RESOURCE_EXTENSION.length() ) );
				} );
	}

	/**
	 * Load the named LUT resource as a {@link Palette}, or {@code null} if
	 * it cannot be found or parsed. The returned palette's
	 * {@link Palette#isInterpolated()} reflects the resource's
	 * {@code color_interpolation} field (defaulting to {@code true} if the
	 * resource does not declare it).
	 *
	 * @param name
	 * 		a name as returned by {@link #discoverNames()}.
	 */
	public static Palette load( final String name )
	{
		final JsonObject root = readRoot( name );
		if ( root == null )
			return null;
		final JsonArray colors = root.getAsJsonArray( "fixes_RGBA" );
		if ( colors == null )
			return null;

		final int n = colors.size();
		if ( n < 2 )
			return null;

		final int[] stops = new int[ n ];
		for ( int i = 0; i < n; i++ )
		{
			final JsonArray rgba = colors.get( i ).getAsJsonArray();
			stops[ i ] = ARGBType.rgba(
					to8( rgba.get( 0 ).getAsDouble() ),
					to8( rgba.get( 1 ).getAsDouble() ),
					to8( rgba.get( 2 ).getAsDouble() ),
					to8( rgba.get( 3 ).getAsDouble() ) );
		}
		final boolean interpolated = !root.has( "color_interpolation" ) || root.get( "color_interpolation" ).getAsBoolean();
		return new Palette( stops, interpolated );
	}

	/** A [0, 1] color component as an 8-bit channel value. */
	private static int to8( final double v )
	{
		return Math.max( 0, Math.min( 255, ( int ) Math.round( v * 255.0 ) ) );
	}

	/**
	 * Reverse of {@link #load(String)}: the name of the discovered LUT
	 * resource whose colors exactly match {@code palette}, or {@code null} if
	 * none do (e.g. {@code palette} isn't one of these resources at all, such
	 * as {@link Palette#DEFAULT} or a palette set up some other way).
	 * Used to recover a display name for a bare {@link Palette} read back
	 * from a converter, which doesn't otherwise remember which resource (if
	 * any) it was originally loaded from.
	 */
	public static synchronized String findName( final Palette palette )
	{
		if ( palette == null )
			return null;
		for ( final Map.Entry< String, Palette > candidate : cachedPalettes().entrySet() )
			if ( palette.equals( candidate.getValue() ) )
				return candidate.getKey();
		return null;
	}

	/**
	 * Every bundled palette, parsed once and kept for the life of the
	 * process. Only {@link #findName} uses this: it would otherwise re-read
	 * and re-parse all ~90 resources on every call, and it is called on the
	 * EDT each time the LUT editor's selected source changes.
	 * <p>
	 * Safe to cache, since the bundled resources cannot change while the
	 * process runs and a {@link Palette} is immutable -- so, unlike the
	 * mutable-in-principle {@code ColorTable} this used to hand out, sharing
	 * one instance between callers cannot alias unrelated sources together.
	 */
	private static Map< String, Palette > cachedPalettes()
	{
		if ( cachedPalettes == null )
		{
			final Map< String, Palette > palettes = new LinkedHashMap<>();
			for ( final String name : discoverNames() )
			{
				final Palette palette = load( name );
				if ( palette != null )
					palettes.put( name, palette );
			}
			cachedPalettes = palettes;
		}
		return cachedPalettes;
	}

	/** Guarded by {@code LutPalettes.class}, via {@link #findName}. */
	private static Map< String, Palette > cachedPalettes = null;

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
