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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Discovers, loads and saves {@link EditorPreset}s: the data-access side of
 * the LUT editor's "Setting" feature, deliberately kept free of any UI (see
 * {@link LutPalettes}, which this mirrors for the palette side of the
 * editor). There are two sources, merged by {@link #discoverNames()}:
 * <ul>
 * <li>Built-in presets, bundled read-only classpath resources directly under
 * {@value #BUILTIN_RESOURCE_DIR}.</li>
 * <li>User-saved presets, written by {@link #save(EditorPreset)} to a
 * {@value #USER_SUBDIR} subdirectory of that same resource directory --
 * kept out of version control via a {@code .gitignore} entry, rather than a
 * separate location entirely, so built-in and user-saved presets are easy to
 * find side by side on disk.</li>
 * </ul>
 * A user-saved preset takes precedence over a built-in one of the same name.
 * <p>
 * Since {@value #USER_SUBDIR} must be writable, this only works when
 * {@value #BUILTIN_RESOURCE_DIR} resolves to a real directory on the local
 * filesystem (e.g. running from an IDE or {@code target/classes}) -- not
 * when packaged inside a jar.
 */
public final class EditorPresets
{
	private static final String BUILTIN_RESOURCE_DIR = "bdv/ui/lut-editor-presets";

	private static final String USER_SUBDIR = "user";

	private static final String RESOURCE_EXTENSION = ".json";

	/**
	 * System property that, if set, overrides {@link #userDir()} entirely --
	 * lets tests point saves/loads at a throwaway directory instead of
	 * {@value #BUILTIN_RESOURCE_DIR}'s own {@value #USER_SUBDIR} subdirectory.
	 */
	static final String USER_DIR_OVERRIDE_PROPERTY = "bdv.tools.brightness.EditorPresets.userDir";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private EditorPresets()
	{}

	/**
	 * The writable directory {@link #save(EditorPreset)} writes to: the
	 * {@value #USER_SUBDIR} subdirectory of wherever {@value #BUILTIN_RESOURCE_DIR}
	 * actually resolves to on disk -- or {@link #USER_DIR_OVERRIDE_PROPERTY}
	 * verbatim, if set. {@code null} when there is no such directory (see
	 * {@link #resolveBuiltinResourceDir()}), in which case user-saved presets
	 * are simply unavailable; built-in ones still load normally.
	 */
	private static String userDir()
	{
		final String override = System.getProperty( USER_DIR_OVERRIDE_PROPERTY );
		if ( override != null )
			return override;
		final File builtinDir = resolveBuiltinResourceDir();
		return builtinDir == null ? null : new File( builtinDir, USER_SUBDIR ).getAbsolutePath();
	}

	/**
	 * The real filesystem directory {@value #BUILTIN_RESOURCE_DIR} resolves
	 * to, or {@code null} if it cannot be found or is not an actual
	 * directory on the local filesystem -- notably when running from a
	 * packaged jar, whose {@code jar:} URL is not a hierarchical URI and so
	 * cannot become a {@link File} at all.
	 * <p>
	 * Only {@link #userDir()} needs this. Built-in presets themselves are
	 * read through the classloader instead (see {@link #discoverNames()},
	 * {@link #load(String)}), which works either way -- so returning
	 * {@code null} here must degrade to "no user-saved presets", never break
	 * the built-in ones.
	 */
	private static File resolveBuiltinResourceDir()
	{
		final URL dirUrl = EditorPresets.class.getClassLoader().getResource( BUILTIN_RESOURCE_DIR );
		if ( dirUrl == null )
			return null;
		try
		{
			return new File( dirUrl.toURI() );
		}
		catch ( final URISyntaxException | IllegalArgumentException e )
		{
			return null;
		}
	}

	/**
	 * The file a user-saved preset of this name lives in, or {@code null} if
	 * there is no writable {@link #userDir()} at all.
	 */
	private static File userFile( final String name )
	{
		final String dir = userDir();
		return dir == null ? null : new File( dir, sanitizeFileName( name ) + RESOURCE_EXTENSION );
	}

	/**
	 * The names of all available presets (built-in and user-saved, merged
	 * and de-duplicated), sorted case-insensitively. A name is what
	 * {@link #load(String)} expects and {@link #isUserDefined(String)}
	 * classifies.
	 */
	public static List< String > discoverNames()
	{
		final TreeMap< String, String > sorted = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
		try
		{
			final URL dirUrl = EditorPresets.class.getClassLoader().getResource( BUILTIN_RESOURCE_DIR );
			if ( dirUrl != null )
			{
				final URI uri = dirUrl.toURI();
				if ( "jar".equals( uri.getScheme() ) )
				{
					try ( final FileSystem fs = FileSystems.newFileSystem( uri, Collections.emptyMap() );
					      final Stream< Path > paths = Files.walk( fs.getPath( BUILTIN_RESOURCE_DIR ), 1 ) )
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
			}
		} catch ( final Exception e )
		{
			e.printStackTrace();
		}

		final String userDirPath = userDir();
		final File[] userFiles = userDirPath == null ? null : new File( userDirPath ).listFiles();
		if ( userFiles != null )
		{
			for ( final File f : userFiles )
				if ( f.getName().endsWith( RESOURCE_EXTENSION ) )
					sorted.put( f.getName().substring( 0, f.getName().length() - RESOURCE_EXTENSION.length() ), f.getName() );
		}

		return new ArrayList<>( sorted.keySet() );
	}

	private static void collectNames( final Stream< Path > paths, final TreeMap< String, String > sorted )
	{
		paths.filter( p -> p.toString().endsWith( RESOURCE_EXTENSION ) )
				.forEach( p ->
				{
					final String fn = p.getFileName().toString();
					sorted.put( fn.substring( 0, fn.length() - RESOURCE_EXTENSION.length() ), fn );
				} );
	}

	/**
	 * Whether {@code name} is (currently) backed by a user-saved file rather
	 * than a built-in resource -- used by the UI to group the two kinds
	 * separately. If both exist, the user-saved one is what {@link #load}
	 * returns, so this reports {@code true}.
	 */
	public static boolean isUserDefined( final String name )
	{
		final File file = userFile( name );
		return file != null && file.isFile();
	}

	/**
	 * Load the named preset, preferring a user-saved file over a built-in
	 * resource of the same name, or {@code null} if neither exists or it
	 * cannot be parsed.
	 *
	 * @param name
	 * 		a name as returned by {@link #discoverNames()}.
	 */
	public static EditorPreset load( final String name )
	{
		final File userFile = userFile( name );
		if ( userFile != null && userFile.isFile() )
		{
			try ( final FileReader reader = new FileReader( userFile ) )
			{
				return GSON.fromJson( reader, EditorPreset.class );
			} catch ( final IOException | RuntimeException e )
			{
				e.printStackTrace();
				return null;
			}
		}

		final String path = BUILTIN_RESOURCE_DIR + "/" + name + RESOURCE_EXTENSION;
		try ( final InputStream is = EditorPresets.class.getClassLoader().getResourceAsStream( path ) )
		{
			if ( is == null )
				return null;
			return GSON.fromJson( new InputStreamReader( is ), EditorPreset.class );
		} catch ( final IOException | RuntimeException e )
		{
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Save {@code preset} (under {@link EditorPreset#getName()}) to
	 * {@link #userDir()}, creating the directory if needed and overwriting
	 * any existing file of the same name -- including a built-in preset's
	 * name, which this then takes precedence over (see {@link #load}).
	 *
	 * @throws IllegalStateException
	 * 		if there is no writable directory to save into at all (see
	 * 		{@link #resolveBuiltinResourceDir()}); unlike the read paths,
	 * 		which just degrade to "no user-saved presets", saving cannot
	 * 		silently do nothing.
	 */
	public static void save( final EditorPreset preset )
	{
		final File file = userFile( preset.getName() );
		if ( file == null )
			throw new IllegalStateException( "No writable settings directory available"
					+ " (running from a packaged jar?); cannot save \"" + preset.getName() + "\"" );
		file.getParentFile().mkdirs();
		try ( final FileWriter writer = new FileWriter( file ) )
		{
			GSON.toJson( preset, writer );
		} catch ( final IOException e )
		{
			throw new RuntimeException( "Failed to save LUT editor setting \"" + preset.getName() + "\" to " + file, e );
		}
	}

	/**
	 * Turn a user-chosen preset name into a safe file name: path separators
	 * and other filesystem-significant characters would otherwise let a
	 * preset name escape {@link #userDir()} or collide with it.
	 */
	private static String sanitizeFileName( final String name )
	{
		return name.trim().replaceAll( "[\\\\/:*?\"<>|]", "_" );
	}
}
