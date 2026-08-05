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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups LUT palette names into the same categories matplotlib uses for its
 * own built-in colormaps ("Choosing Colormaps in Matplotlib"). Pure data/
 * lookup, no UI, so the categorization logic doesn't live in the (visual)
 * LUT editor dialog.
 * <p>
 * A {@code "_r"} suffix (a reversed palette) is stripped before lookup, so a
 * reversed palette is always grouped with its base palette. Names not
 * recognized here (e.g. a user-added resource) fall back to
 * {@link #MISCELLANEOUS}, matplotlib's own catch-all category.
 */
public final class LutCategories
{
	public static final String PERCEPTUALLY_UNIFORM_SEQUENTIAL = "Perceptually Uniform Sequential";

	public static final String SEQUENTIAL = "Sequential";

	public static final String DIVERGING = "Diverging";

	public static final String CYCLIC = "Cyclic";

	public static final String QUALITATIVE = "Qualitative";

	public static final String MISCELLANEOUS = "Miscellaneous";

	/** Matplotlib's own display order for these categories. */
	private static final List< String > CATEGORY_ORDER = List.of(
			PERCEPTUALLY_UNIFORM_SEQUENTIAL, SEQUENTIAL, DIVERGING, CYCLIC, QUALITATIVE, MISCELLANEOUS );

	private static final Map< String, String > CATEGORY_BY_NAME = buildCategoryByName();

	private LutCategories()
	{
	}

	/**
	 * The category of {@code paletteName} (after stripping a trailing
	 * {@code "_r"}, if any), or {@link #MISCELLANEOUS} if not recognized.
	 */
	public static String categoryOf( final String paletteName )
	{
		final String base = paletteName.endsWith( "_r" ) ? paletteName.substring( 0, paletteName.length() - 2 ) : paletteName;
		return CATEGORY_BY_NAME.getOrDefault( base, MISCELLANEOUS );
	}

	/**
	 * Group {@code names} by {@link #categoryOf(String)}, in matplotlib's own
	 * category order, each category's names sorted case-insensitively.
	 * Categories with no matching name are omitted.
	 */
	public static Map< String, List< String > > groupByCategory( final Collection< String > names )
	{
		final Map< String, List< String > > grouped = new LinkedHashMap<>();
		for ( final String category : CATEGORY_ORDER )
			grouped.put( category, new ArrayList<>() );
		for ( final String name : names )
			grouped.computeIfAbsent( categoryOf( name ), c -> new ArrayList<>() ).add( name );
		grouped.values().forEach( list -> list.sort( String.CASE_INSENSITIVE_ORDER ) );
		grouped.entrySet().removeIf( e -> e.getValue().isEmpty() );
		return grouped;
	}

	private static Map< String, String > buildCategoryByName()
	{
		final Map< String, String > m = new HashMap<>();
		put( m, PERCEPTUALLY_UNIFORM_SEQUENTIAL,
				"viridis", "plasma", "inferno", "magma", "cividis" );
		put( m, SEQUENTIAL,
				"Greys", "Grays", "Purples", "Blues", "Greens", "Oranges", "Reds",
				"YlOrBr", "YlOrRd", "OrRd", "PuRd", "RdPu", "BuPu", "GnBu", "PuBu",
				"YlGnBu", "PuBuGn", "BuGn", "YlGn",
				// matplotlib's separate "Sequential (2)" category, merged in here.
				"binary", "gist_yarg", "gist_yerg", "gist_gray", "gist_grey", "gray", "grey",
				"bone", "pink", "spring", "summer", "autumn", "winter", "cool", "Wistia",
				"hot", "afmhot", "gist_heat", "copper" );
		put( m, DIVERGING,
				"PiYG", "PRGn", "BrBG", "PuOr", "RdGy", "RdBu", "RdYlBu", "RdYlGn",
				"Spectral", "coolwarm", "bwr", "seismic", "berlin", "managua", "vanimo" );
		put( m, CYCLIC,
				"twilight", "twilight_shifted", "hsv" );
		put( m, QUALITATIVE,
				"Pastel1", "Pastel2", "Paired", "Accent", "Dark2", "Set1", "Set2", "Set3",
				"tab10", "tab20", "tab20b", "tab20c", "okabe_ito" );
		put( m, MISCELLANEOUS,
				"flag", "prism", "ocean", "gist_earth", "terrain", "gist_stern", "gnuplot",
				"gnuplot2", "CMRmap", "cubehelix", "brg", "gist_rainbow", "rainbow", "jet",
				"turbo", "nipy_spectral", "gist_ncar" );
		return m;
	}

	private static void put( final Map< String, String > m, final String category, final String... names )
	{
		for ( final String name : names )
			m.put( name, category );
	}
}
