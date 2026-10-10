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
package bdv.tools.brightness.colorscheme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test cases for {@link ColorSchemeCategoryHelper}.
 */
public class ColorSchemeCategoryHelperTest
{
	@Test
	public void testCategoryOfKnownSchemes()
	{
		Assert.assertEquals( ColorSchemeCategoryHelper.PERCEPTUALLY_UNIFORM_SEQUENTIAL, ColorSchemeCategoryHelper.categoryOf( "viridis" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.SEQUENTIAL, ColorSchemeCategoryHelper.categoryOf( "Blues" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.SEQUENTIAL, ColorSchemeCategoryHelper.categoryOf( "gray" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.DIVERGING, ColorSchemeCategoryHelper.categoryOf( "coolwarm" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.CYCLIC, ColorSchemeCategoryHelper.categoryOf( "hsv" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.QUALITATIVE, ColorSchemeCategoryHelper.categoryOf( "tab10" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.MISCELLANEOUS, ColorSchemeCategoryHelper.categoryOf( "jet" ) );
	}

	@Test
	public void testCategoryOfFallsBackToMiscellaneousForUnknownName()
	{
		Assert.assertEquals( ColorSchemeCategoryHelper.MISCELLANEOUS, ColorSchemeCategoryHelper.categoryOf( "some_custom_scheme" ) );
	}

	@Test
	public void testCategoryOfStripsReversedSuffix()
	{
		Assert.assertEquals( ColorSchemeCategoryHelper.categoryOf( "viridis" ), ColorSchemeCategoryHelper.categoryOf( "viridis_r" ) );
		Assert.assertEquals( ColorSchemeCategoryHelper.categoryOf( "tab10" ), ColorSchemeCategoryHelper.categoryOf( "tab10_r" ) );
	}

	@Test
	public void testGroupByCategoryOrdersCategoriesLikeMatplotlib()
	{
		final List< String > names = Arrays.asList( "jet", "viridis", "tab10", "Blues" );
		final List< String > categories = new ArrayList<>( ColorSchemeCategoryHelper.groupByCategory( names ).keySet() );

		// Perceptually Uniform Sequential, then Sequential, ..., Miscellaneous --
		// in that relative order, regardless of the input order.
		Assert.assertTrue( categories.indexOf( ColorSchemeCategoryHelper.PERCEPTUALLY_UNIFORM_SEQUENTIAL ) < categories.indexOf( ColorSchemeCategoryHelper.SEQUENTIAL ) );
		Assert.assertTrue( categories.indexOf( ColorSchemeCategoryHelper.SEQUENTIAL ) < categories.indexOf( ColorSchemeCategoryHelper.QUALITATIVE ) );
		Assert.assertTrue( categories.indexOf( ColorSchemeCategoryHelper.QUALITATIVE ) < categories.indexOf( ColorSchemeCategoryHelper.MISCELLANEOUS ) );
	}

	@Test
	public void testGroupByCategoryOmitsEmptyCategories()
	{
		final Map< String, List< String > > grouped = ColorSchemeCategoryHelper.groupByCategory( Arrays.asList( "viridis" ) );

		Assert.assertEquals( 1, grouped.size() );
		Assert.assertEquals( List.of( "viridis" ), grouped.get( ColorSchemeCategoryHelper.PERCEPTUALLY_UNIFORM_SEQUENTIAL ) );
	}

	@Test
	public void testGroupByCategorySortsWithinCategoryCaseInsensitively()
	{
		final Map< String, List< String > > grouped = ColorSchemeCategoryHelper.groupByCategory( Arrays.asList( "Reds", "Blues", "Greens" ) );

		Assert.assertEquals( List.of( "Blues", "Greens", "Reds" ), grouped.get( ColorSchemeCategoryHelper.SEQUENTIAL ) );
	}

	/** Catches a resource that was misspelled or forgotten in the category table. */
	@Test
	public void testAllDiscoveredSchemesAreCategorized()
	{
		final Set< String > knownMiscellaneous = new HashSet<>( Arrays.asList(
				"flag", "prism", "ocean", "gist_earth", "terrain", "gist_stern", "gnuplot",
				"gnuplot2", "CMRmap", "cubehelix", "brg", "gist_rainbow", "rainbow", "jet",
				"turbo", "nipy_spectral", "gist_ncar" ) );

		for ( final String name : ColorSchemeFactory.discoverNames() )
		{
			if ( !ColorSchemeCategoryHelper.MISCELLANEOUS.equals( ColorSchemeCategoryHelper.categoryOf( name ) ) )
				continue;
			final String base = name.endsWith( "_r" ) ? name.substring( 0, name.length() - 2 ) : name;
			Assert.assertTrue( "unexpectedly Miscellaneous: " + name, knownMiscellaneous.contains( base ) );
		}
	}
}
