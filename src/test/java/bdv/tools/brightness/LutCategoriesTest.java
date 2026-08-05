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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link LutCategories}.
 */
public class LutCategoriesTest
{
	@Test
	public void testCategoryOfKnownPalettes()
	{
		Assert.assertEquals( LutCategories.PERCEPTUALLY_UNIFORM_SEQUENTIAL, LutCategories.categoryOf( "viridis" ) );
		Assert.assertEquals( LutCategories.SEQUENTIAL, LutCategories.categoryOf( "Blues" ) );
		Assert.assertEquals( LutCategories.SEQUENTIAL, LutCategories.categoryOf( "gray" ) );
		Assert.assertEquals( LutCategories.DIVERGING, LutCategories.categoryOf( "coolwarm" ) );
		Assert.assertEquals( LutCategories.CYCLIC, LutCategories.categoryOf( "hsv" ) );
		Assert.assertEquals( LutCategories.QUALITATIVE, LutCategories.categoryOf( "tab10" ) );
		Assert.assertEquals( LutCategories.MISCELLANEOUS, LutCategories.categoryOf( "jet" ) );
	}

	@Test
	public void testCategoryOfFallsBackToMiscellaneousForUnknownName()
	{
		Assert.assertEquals( LutCategories.MISCELLANEOUS, LutCategories.categoryOf( "some_custom_palette" ) );
	}

	@Test
	public void testCategoryOfStripsReversedSuffix()
	{
		Assert.assertEquals( LutCategories.categoryOf( "viridis" ), LutCategories.categoryOf( "viridis_r" ) );
		Assert.assertEquals( LutCategories.categoryOf( "tab10" ), LutCategories.categoryOf( "tab10_r" ) );
	}

	@Test
	public void testGroupByCategoryOrdersCategoriesLikeMatplotlib()
	{
		final List< String > names = Arrays.asList( "jet", "viridis", "tab10", "Blues" );
		final List< String > categories = new ArrayList<>( LutCategories.groupByCategory( names ).keySet() );

		// Perceptually Uniform Sequential, then Sequential, ..., Miscellaneous --
		// in that relative order, regardless of the input order.
		Assert.assertTrue( categories.indexOf( LutCategories.PERCEPTUALLY_UNIFORM_SEQUENTIAL ) < categories.indexOf( LutCategories.SEQUENTIAL ) );
		Assert.assertTrue( categories.indexOf( LutCategories.SEQUENTIAL ) < categories.indexOf( LutCategories.QUALITATIVE ) );
		Assert.assertTrue( categories.indexOf( LutCategories.QUALITATIVE ) < categories.indexOf( LutCategories.MISCELLANEOUS ) );
	}

	@Test
	public void testGroupByCategoryOmitsEmptyCategories()
	{
		final Map< String, List< String > > grouped = LutCategories.groupByCategory( Arrays.asList( "viridis" ) );

		Assert.assertEquals( 1, grouped.size() );
		Assert.assertEquals( List.of( "viridis" ), grouped.get( LutCategories.PERCEPTUALLY_UNIFORM_SEQUENTIAL ) );
	}

	@Test
	public void testGroupByCategorySortsWithinCategoryCaseInsensitively()
	{
		final Map< String, List< String > > grouped = LutCategories.groupByCategory( Arrays.asList( "Reds", "Blues", "Greens" ) );

		Assert.assertEquals( List.of( "Blues", "Greens", "Reds" ), grouped.get( LutCategories.SEQUENTIAL ) );
	}

	/**
	 * Every actual bundled LUT resource must be recognized (i.e. not silently
	 * fall back to Miscellaneous just because it was misspelled/forgotten
	 * here) -- except the ones that are genuinely in matplotlib's own
	 * Miscellaneous category.
	 */
	@Test
	public void testAllDiscoveredPalettesAreCategorized()
	{
		final Set< String > knownMiscellaneous = new HashSet<>( Arrays.asList(
				"flag", "prism", "ocean", "gist_earth", "terrain", "gist_stern", "gnuplot",
				"gnuplot2", "CMRmap", "cubehelix", "brg", "gist_rainbow", "rainbow", "jet",
				"turbo", "nipy_spectral", "gist_ncar" ) );

		for ( final String name : LutPalettes.discoverNames() )
		{
			if ( !LutCategories.MISCELLANEOUS.equals( LutCategories.categoryOf( name ) ) )
				continue;
			final String base = name.endsWith( "_r" ) ? name.substring( 0, name.length() - 2 ) : name;
			Assert.assertTrue( "unexpectedly Miscellaneous: " + name, knownMiscellaneous.contains( base ) );
		}
	}
}
