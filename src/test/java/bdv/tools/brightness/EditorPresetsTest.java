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

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test cases for {@link EditorPresets}. Each test points
 * {@link EditorPresets#USER_DIR_OVERRIDE_PROPERTY} at a fresh
 * {@link TemporaryFolder} rather than the real per-machine config directory,
 * so saving never touches the developer's actual settings.
 */
public class EditorPresetsTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void redirectUserDir()
	{
		System.setProperty( EditorPresets.USER_DIR_OVERRIDE_PROPERTY, tmp.getRoot().getAbsolutePath() );
	}

	@After
	public void clearUserDirOverride()
	{
		System.clearProperty( EditorPresets.USER_DIR_OVERRIDE_PROPERTY );
	}

	private static EditorPreset sample( final String name )
	{
		return new EditorPreset( name, "tab10", RangeMode.CYCLIC, true, 0xff112233,
				new double[] { 0.0, 0.5, 1.0 }, new int[] { 0, 100, 255 } );
	}

	@Test
	public void testDiscoverNamesIncludesBuiltins()
	{
		final List< String > names = EditorPresets.discoverNames();

		Assert.assertTrue( names.contains( "Labels (Cyclic, tab10)" ) );
		Assert.assertTrue( names.contains( "Percentile Stretch (Viridis)" ) );
	}

	@Test
	public void testLoadBuiltinPresetParsesFields()
	{
		final EditorPreset preset = EditorPresets.load( "Labels (Cyclic, tab10)" );

		Assert.assertNotNull( preset );
		Assert.assertEquals( "tab10", preset.getPaletteName() );
		Assert.assertEquals( RangeMode.CYCLIC, preset.getRangeMode() );
		Assert.assertTrue( preset.isTreatMinAsBackground() );
		Assert.assertEquals( 0xff000000, preset.getBackgroundColor() );
		Assert.assertArrayEquals( new double[] { 0.0, 1.0 }, preset.getCurveXs(), 1e-9 );
		Assert.assertArrayEquals( new int[] { 0, 255 }, preset.getCurveYs() );
	}

	@Test
	public void testBuiltinPresetIsNotUserDefined()
	{
		Assert.assertFalse( EditorPresets.isUserDefined( "Labels (Cyclic, tab10)" ) );
	}

	@Test
	public void testSaveAndLoadRoundTrip()
	{
		final EditorPreset saved = sample( "My Setting" );
		EditorPresets.save( saved );

		final EditorPreset loaded = EditorPresets.load( "My Setting" );

		Assert.assertNotNull( loaded );
		Assert.assertEquals( saved.getName(), loaded.getName() );
		Assert.assertEquals( saved.getPaletteName(), loaded.getPaletteName() );
		Assert.assertEquals( saved.getRangeMode(), loaded.getRangeMode() );
		Assert.assertEquals( saved.isTreatMinAsBackground(), loaded.isTreatMinAsBackground() );
		Assert.assertEquals( saved.getBackgroundColor(), loaded.getBackgroundColor() );
		Assert.assertArrayEquals( saved.getCurveXs(), loaded.getCurveXs(), 1e-9 );
		Assert.assertArrayEquals( saved.getCurveYs(), loaded.getCurveYs() );
	}

	@Test
	public void testDiscoverNamesIncludesUserSavedPreset()
	{
		EditorPresets.save( sample( "My Setting" ) );

		Assert.assertTrue( EditorPresets.discoverNames().contains( "My Setting" ) );
	}

	@Test
	public void testIsUserDefinedTrueOnlyAfterSaving()
	{
		Assert.assertFalse( EditorPresets.isUserDefined( "My Setting" ) );

		EditorPresets.save( sample( "My Setting" ) );

		Assert.assertTrue( EditorPresets.isUserDefined( "My Setting" ) );
	}

	/**
	 * Saving under a built-in preset's exact name should shadow it: loading
	 * that name afterwards returns the user's version, not the built-in one.
	 */
	@Test
	public void testUserSavedPresetOverridesBuiltinOfSameName()
	{
		EditorPresets.save( sample( "Labels (Cyclic, tab10)" ) );

		Assert.assertTrue( EditorPresets.isUserDefined( "Labels (Cyclic, tab10)" ) );
		final EditorPreset loaded = EditorPresets.load( "Labels (Cyclic, tab10)" );
		Assert.assertEquals( 0xff112233, loaded.getBackgroundColor() );
		Assert.assertArrayEquals( new int[] { 0, 100, 255 }, loaded.getCurveYs() );
	}

	@Test
	public void testLoadReturnsNullForUnknownName()
	{
		Assert.assertNull( EditorPresets.load( "this-setting-does-not-exist" ) );
	}

	/**
	 * A preset name with filesystem-significant characters must not let a
	 * save escape the user directory (e.g. via {@code ../}) or otherwise
	 * fail -- it should just round-trip through {@link EditorPresets#load}
	 * like any other name.
	 */
	@Test
	public void testSaveSanitizesUnsafeCharactersInName()
	{
		final String unsafeName = "weird/name:with*chars?";
		EditorPresets.save( sample( unsafeName ) );

		Assert.assertEquals( unsafeName, EditorPresets.load( unsafeName ).getName() );
		Assert.assertTrue( Arrays.asList( tmp.getRoot().list() ).stream().allMatch( f -> f.endsWith( ".json" ) ) );
	}
}
