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
		return new EditorPreset( name, "tab10", true, true, 0xff112233,
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
		Assert.assertTrue( preset.isCyclic() );
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
		Assert.assertEquals( saved.isCyclic(), loaded.isCyclic() );
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
	 * Regression test: when there is no user-preset directory at all --
	 * which is what happens for real when running from a packaged jar, whose
	 * {@code jar:} resource URL cannot become a {@link java.io.File} -- the
	 * read paths must degrade to "no user-saved presets" rather than
	 * throwing. Previously they threw, which took down the whole LUT editor
	 * dialog (it builds the preset combo during construction), including the
	 * built-in presets that don't need a writable directory at all.
	 * <p>
	 * Simulated here by pointing the directory override at a path that does
	 * not exist; the jar case reaches the same {@code null} directory by a
	 * different route.
	 */
	@Test
	public void testReadPathsDegradeWhenNoUserDirectoryExists()
	{
		System.setProperty( EditorPresets.USER_DIR_OVERRIDE_PROPERTY,
				new java.io.File( tmp.getRoot(), "does-not-exist" ).getAbsolutePath() );

		// Built-in presets still discoverable and loadable...
		Assert.assertTrue( EditorPresets.discoverNames().contains( "Labels (Cyclic, tab10)" ) );
		Assert.assertNotNull( EditorPresets.load( "Labels (Cyclic, tab10)" ) );
		// ...and nothing is reported as user-defined.
		Assert.assertFalse( EditorPresets.isUserDefined( "Labels (Cyclic, tab10)" ) );
		Assert.assertFalse( EditorPresets.isUserDefined( "anything" ) );
	}

	/**
	 * A preset name with filesystem-significant characters must not let a
	 * save escape the user directory (e.g. via {@code ../}). Canonicalizing
	 * replaces those characters, and the canonical form is what the preset
	 * is stored, listed and loaded under.
	 */
	@Test
	public void testCanonicalNameReplacesUnsafeCharacters()
	{
		Assert.assertEquals( "weird_name_with_chars_", EditorPresets.canonicalName( "weird/name:with*chars?" ) );
		Assert.assertEquals( "trimmed", EditorPresets.canonicalName( "  trimmed  " ) );
		// Already-canonical names are left exactly as they are.
		Assert.assertEquals( "Labels (Cyclic, tab10)", EditorPresets.canonicalName( "Labels (Cyclic, tab10)" ) );
	}

	@Test
	public void testSaveRoundTripsCanonicalizedUnsafeName()
	{
		final String canonical = EditorPresets.canonicalName( "weird/name:with*chars?" );
		EditorPresets.save( sample( canonical ) );

		Assert.assertEquals( canonical, EditorPresets.load( canonical ).getName() );
		Assert.assertTrue( Arrays.asList( tmp.getRoot().list() ).stream().allMatch( f -> f.endsWith( ".json" ) ) );
	}

	/**
	 * Regression test: a preset's own name must match the name it is filed
	 * (and therefore listed) under, so {@link EditorPresets#discoverNames()}
	 * -- which reads names off file names -- can never disagree with
	 * {@link EditorPreset#getName()}. Saving a non-canonical name used to
	 * silently file it elsewhere, which also made the caller's
	 * "already exists?" check against discoverNames() miss and overwrite
	 * without asking.
	 */
	@Test
	public void testSaveRejectsNonCanonicalName()
	{
		try
		{
			EditorPresets.save( sample( "weird/name" ) );
			Assert.fail( "expected IllegalArgumentException for a non-canonical preset name" );
		}
		catch ( final IllegalArgumentException expected )
		{
			// message should point at the canonical form to use instead
			Assert.assertTrue( expected.getMessage(), expected.getMessage().contains( "weird_name" ) );
		}
	}

	@Test
	public void testDiscoverNamesAgreesWithSavedPresetsOwnName()
	{
		final String canonical = EditorPresets.canonicalName( "a/b:c" );
		EditorPresets.save( sample( canonical ) );

		Assert.assertTrue( EditorPresets.discoverNames().contains( canonical ) );
		Assert.assertEquals( canonical, EditorPresets.load( canonical ).getName() );
	}
}
