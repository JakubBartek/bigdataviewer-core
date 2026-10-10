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
package bdv.tools.brightness.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import bdv.tools.brightness.converter.BoundaryCondition;

/** Each test points {@link EditorPresetHelper#USER_DIR_OVERRIDE_PROPERTY} at a {@link TemporaryFolder}. */
public class EditorPresetHelperTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void redirectUserDir()
	{
		System.setProperty( EditorPresetHelper.USER_DIR_OVERRIDE_PROPERTY, tmp.getRoot().getAbsolutePath() );
	}

	@After
	public void clearUserDirOverride()
	{
		System.clearProperty( EditorPresetHelper.USER_DIR_OVERRIDE_PROPERTY );
	}

	private static EditorPreset sample( final String name )
	{
		return new EditorPreset( name, "tab10", BoundaryCondition.SPECIAL, BoundaryCondition.CYCLE,
				0xff112233, 0xff445566, 2.5,
				new double[] { 0.0, 0.5, 1.0 }, new int[] { 0, 100, 255 } );
	}

	@Test
	public void testDiscoverNamesIncludesBuiltins()
	{
		final List< String > names = EditorPresetHelper.discoverNames();

		Assert.assertTrue( names.contains( "Labels (Cyclic, tab10)" ) );
		Assert.assertTrue( names.contains( "Percentile Stretch (Viridis)" ) );
	}

	@Test
	public void testLoadBuiltinPresetParsesFields()
	{
		final EditorPreset preset = EditorPresetHelper.load( "Labels (Cyclic, tab10)" );

		Assert.assertNotNull( preset );
		Assert.assertEquals( "tab10", preset.getColorSchemeName() );
		Assert.assertEquals( BoundaryCondition.SPECIAL, preset.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, preset.getRightBoundaryCondition() );
		Assert.assertEquals( 0xff000000, preset.getLeftSpecialColor() );
		Assert.assertArrayEquals( new double[] { 0.0, 1.0 }, preset.getCurveXs(), 1e-9 );
		Assert.assertArrayEquals( new int[] { 0, 255 }, preset.getCurveYs() );
	}

	@Test
	public void testBuiltinPresetIsNotUserDefined()
	{
		Assert.assertFalse( EditorPresetHelper.isUserDefined( "Labels (Cyclic, tab10)" ) );
	}

	@Test
	public void testSaveAndLoadRoundTrip()
	{
		final EditorPreset saved = sample( "My Setting" );
		EditorPresetHelper.save( saved );

		final EditorPreset loaded = EditorPresetHelper.load( "My Setting" );

		Assert.assertNotNull( loaded );
		Assert.assertEquals( saved.getName(), loaded.getName() );
		Assert.assertEquals( saved.getColorSchemeName(), loaded.getColorSchemeName() );
		Assert.assertEquals( saved.getLeftBoundaryCondition(), loaded.getLeftBoundaryCondition() );
		Assert.assertEquals( saved.getRightBoundaryCondition(), loaded.getRightBoundaryCondition() );
		Assert.assertEquals( saved.getLeftSpecialColor(), loaded.getLeftSpecialColor() );
		Assert.assertEquals( saved.getRightSpecialColor(), loaded.getRightSpecialColor() );
		Assert.assertEquals( saved.getStepSize(), loaded.getStepSize(), 0.0 );
		Assert.assertArrayEquals( saved.getCurveXs(), loaded.getCurveXs(), 1e-9 );
		Assert.assertArrayEquals( saved.getCurveYs(), loaded.getCurveYs() );
	}

	/** Legacy keys still load: treat-min-as-background as left SPECIAL, cyclic as CYCLE elsewhere. */
	@Test
	public void testLegacyPresetMigratesToBoundaryConditions() throws Exception
	{
		writeUserPreset( "Legacy", "{"
				+ "\"name\":\"Legacy\",\"colorSchemeName\":\"tab10\","
				+ "\"cyclic\":true,\"treatMinAsBackground\":true,\"backgroundColor\":-16777216,"
				+ "\"curveXs\":[0.0,1.0],\"curveYs\":[0,255]}" );

		final EditorPreset loaded = EditorPresetHelper.load( "Legacy" );

		Assert.assertNotNull( loaded );
		Assert.assertEquals( BoundaryCondition.SPECIAL, loaded.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CYCLE, loaded.getRightBoundaryCondition() );
		Assert.assertEquals( 0xff000000, loaded.getLeftSpecialColor() );
		// The legacy format had no above-range color or step size at all.
		Assert.assertEquals( LutEditorMapping.DEFAULT_RIGHT_SPECIAL_COLOR, loaded.getRightSpecialColor() );
		Assert.assertEquals( LutEditorMapping.AUTO_STEP_SIZE, loaded.getStepSize(), 0.0 );
	}

	/** A legacy preset that was neither cyclic nor background-flagged is plain clamping at both ends. */
	@Test
	public void testLegacyNonCyclicPresetMigratesToClamp() throws Exception
	{
		writeUserPreset( "LegacyPlain", "{"
				+ "\"name\":\"LegacyPlain\",\"colorSchemeName\":\"viridis\","
				+ "\"cyclic\":false,\"treatMinAsBackground\":false,\"backgroundColor\":-16777216,"
				+ "\"curveXs\":[0.0,1.0],\"curveYs\":[0,255]}" );

		final EditorPreset loaded = EditorPresetHelper.load( "LegacyPlain" );

		Assert.assertEquals( BoundaryCondition.CLAMP, loaded.getLeftBoundaryCondition() );
		Assert.assertEquals( BoundaryCondition.CLAMP, loaded.getRightBoundaryCondition() );
	}

	/** Saving must not write the legacy keys back out; they are a read-only compatibility path. */
	@Test
	public void testSavedPresetDoesNotWriteLegacyKeys() throws Exception
	{
		EditorPresetHelper.save( sample( "My Setting" ) );

		final String json = new String( Files.readAllBytes(
				new File( tmp.getRoot(), "My Setting.json" ).toPath() ), StandardCharsets.UTF_8 );

		Assert.assertFalse( json, json.contains( "cyclic" ) );
		Assert.assertFalse( json, json.contains( "treatMinAsBackground" ) );
		Assert.assertFalse( json, json.contains( "backgroundColor" ) );
		Assert.assertTrue( json, json.contains( "leftBoundaryCondition" ) );
		Assert.assertTrue( json, json.contains( "colorSchemeName" ) );
	}

	/** Write a raw preset file straight into the (redirected) user directory, bypassing {@link EditorPresetHelper#save} so an older format can be simulated. */
	private void writeUserPreset( final String name, final String json ) throws Exception
	{
		Files.write( new File( tmp.getRoot(), name + ".json" ).toPath(), json.getBytes( StandardCharsets.UTF_8 ) );
	}

	@Test
	public void testDiscoverNamesIncludesUserSavedPreset()
	{
		EditorPresetHelper.save( sample( "My Setting" ) );

		Assert.assertTrue( EditorPresetHelper.discoverNames().contains( "My Setting" ) );
	}

	@Test
	public void testIsUserDefinedTrueOnlyAfterSaving()
	{
		Assert.assertFalse( EditorPresetHelper.isUserDefined( "My Setting" ) );

		EditorPresetHelper.save( sample( "My Setting" ) );

		Assert.assertTrue( EditorPresetHelper.isUserDefined( "My Setting" ) );
	}

	@Test
	public void testUserSavedPresetOverridesBuiltinOfSameName()
	{
		EditorPresetHelper.save( sample( "Labels (Cyclic, tab10)" ) );

		Assert.assertTrue( EditorPresetHelper.isUserDefined( "Labels (Cyclic, tab10)" ) );
		final EditorPreset loaded = EditorPresetHelper.load( "Labels (Cyclic, tab10)" );
		Assert.assertEquals( 0xff112233, loaded.getLeftSpecialColor() );
		Assert.assertArrayEquals( new int[] { 0, 100, 255 }, loaded.getCurveYs() );
	}

	@Test
	public void testLoadReturnsNullForUnknownName()
	{
		Assert.assertNull( EditorPresetHelper.load( "this-setting-does-not-exist" ) );
	}

	/**
	 * Regression: with no user directory (as in a jar) reads used to throw,
	 * taking down the LUT editor. Simulated with a nonexistent path.
	 */
	@Test
	public void testReadPathsDegradeWhenNoUserDirectoryExists()
	{
		System.setProperty( EditorPresetHelper.USER_DIR_OVERRIDE_PROPERTY,
				new java.io.File( tmp.getRoot(), "does-not-exist" ).getAbsolutePath() );

		// Built-in presets still discoverable and loadable...
		Assert.assertTrue( EditorPresetHelper.discoverNames().contains( "Labels (Cyclic, tab10)" ) );
		Assert.assertNotNull( EditorPresetHelper.load( "Labels (Cyclic, tab10)" ) );
		// ...and nothing is reported as user-defined.
		Assert.assertFalse( EditorPresetHelper.isUserDefined( "Labels (Cyclic, tab10)" ) );
		Assert.assertFalse( EditorPresetHelper.isUserDefined( "anything" ) );
	}

	/** E.g. via {@code ../}. */
	@Test
	public void testCanonicalNameReplacesUnsafeCharacters()
	{
		Assert.assertEquals( "weird_name_with_chars_", EditorPresetHelper.canonicalName( "weird/name:with*chars?" ) );
		Assert.assertEquals( "trimmed", EditorPresetHelper.canonicalName( "  trimmed  " ) );
		// Already-canonical names are left exactly as they are.
		Assert.assertEquals( "Labels (Cyclic, tab10)", EditorPresetHelper.canonicalName( "Labels (Cyclic, tab10)" ) );
	}

	@Test
	public void testSaveRoundTripsCanonicalizedUnsafeName()
	{
		final String canonical = EditorPresetHelper.canonicalName( "weird/name:with*chars?" );
		EditorPresetHelper.save( sample( canonical ) );

		Assert.assertEquals( canonical, EditorPresetHelper.load( canonical ).getName() );
		Assert.assertTrue( Arrays.asList( tmp.getRoot().list() ).stream().allMatch( f -> f.endsWith( ".json" ) ) );
	}

	/**
	 * Regression: a non-canonical name was filed elsewhere, so the "already
	 * exists?" check missed and overwrote without asking.
	 */
	@Test
	public void testSaveRejectsNonCanonicalName()
	{
		try
		{
			EditorPresetHelper.save( sample( "weird/name" ) );
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
		final String canonical = EditorPresetHelper.canonicalName( "a/b:c" );
		EditorPresetHelper.save( sample( canonical ) );

		Assert.assertTrue( EditorPresetHelper.discoverNames().contains( canonical ) );
		Assert.assertEquals( canonical, EditorPresetHelper.load( canonical ).getName() );
	}
}
