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

import java.util.ArrayList;
import java.util.List;

import bdv.tools.brightness.colorscheme.ContinuousColorScheme;
import bdv.tools.brightness.colorscheme.Palette;
import bdv.tools.brightness.palette.PresetPaletteWrapper;
import bdv.tools.brightness.presetfunc.LinearPresetFunc;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.converter.Converter;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;

/**
 * Re-renders a source that was set up with a single-color converter (the
 * "legacy" {@code RealARGBColorConverter} and friends) through the color
 * mapping architecture in {@code bdv.tools.brightness.palette} instead, so
 * that {@link LutEditorDialog} can edit it.
 * <p>
 * The translation is an <em>approximation</em>, not a faithful port -- the two
 * representations do not describe the same family of mappings:
 * <ul>
 * <li>The display range is carried over as the new mapping's raw domain --
 * exactly, unless it was collapsed to a single value, which the new
 * representation cannot express and which is widened by one raw unit.</li>
 * <li>The transfer function becomes {@link LinearPresetFunc linear}, which is
 * what a single-color converter always is: it scales the raw value into the
 * display range and multiplies its color by the result.</li>
 * <li>The single color becomes the bundled sequential palette that ramps
 * towards it -- see {@link #paletteNameForColor(int)}. This is the lossy step:
 * an arbitrary color is answered with one of four palettes.</li>
 * </ul>
 * So the result looks similar but is not identical: the old converter scales
 * the color's own channels linearly down to black, whereas a palette like
 * {@code Reds} is a designed perceptual ramp that starts near white.
 *
 * @author Jakub Bartek
 */
public final class PaletteConverterFactory
{
	private PaletteConverterFactory()
	{}

	/**
	 * Whether {@link #approximateInPlace} can do anything with {@code soc}:
	 * its converter has to be a single-color one for there to be a color and a
	 * range to read off, its samples have to be real-valued for
	 * {@link PaletteConverter} to accept them, and the same has to hold for
	 * its {@link SourceAndConverter#asVolatile() volatile} counterpart if it
	 * has one -- converting only one of the two would leave the source
	 * rendering under one color scheme while data is still loading and another
	 * once it has arrived.
	 */
	public static boolean canApproximate( final SourceAndConverter< ? > soc )
	{
		if ( soc == null )
			return false;
		final Converter< ?, ARGBType > converter = soc.getConverter();
		if ( !( converter instanceof ColorConverter ) || converter instanceof PaletteConverter )
			return false;
		if ( !isRealTyped( soc ) )
			return false;
		final SourceAndConverter< ? > volatileSoc = soc.asVolatile();
		return volatileSoc == null || isRealTyped( volatileSoc );
	}

	/**
	 * Swap {@code soc}'s converter (and its volatile counterpart's) for
	 * {@link PaletteConverter}s approximating the current one, and return the
	 * new converter -- or {@code null} if {@link #canApproximate} says there is
	 * nothing to convert.
	 * <p>
	 * Both converters are handed the <em>same</em> {@link PresetPaletteWrapper}
	 * instance, so an edit reaching one reaches the other: they render the same
	 * pixels, differing only in whether the data has arrived yet, and there is
	 * no mapping state either of them needs to hold separately.
	 * <p>
	 * This leaves the source's {@code ConverterSetup} pointing at the old
	 * converter; the caller has to re-point it (see
	 * {@link RealARGBColorConverterSetup#setConverters}), for which
	 * {@link #colorConvertersOf} collects what it should now drive.
	 */
	public static PaletteConverter< ? > approximateInPlace( final SourceAndConverter< ? > soc )
	{
		if ( !canApproximate( soc ) )
			return null;

		final ColorConverter legacy = ( ColorConverter ) soc.getConverter();
		final double min = legacy.getMin();
		final double max = legacy.getMax();

		final ContinuousColorScheme scheme = new ContinuousColorScheme( paletteFor( legacy ) );
		// A collapsed display range leaves the ramp nothing to stretch across,
		// which PresetPaletteWrapper rejects outright. The legacy converter
		// tolerates it (it renders a single flat color), so the range is
		// widened by one raw unit rather than the conversion failing -- the
		// user gets an editable source and can set a sensible range from the
		// brightness controls.
		final double hi = max > min ? max : min + 1;
		final PresetPaletteWrapper wrapper = new PresetPaletteWrapper( scheme,
				new LinearPresetFunc( min, hi, scheme.getPaletteRangeLength() ) );

		final PaletteConverter< ? > converted = install( soc, wrapper, min, hi );
		if ( soc.asVolatile() != null )
			install( soc.asVolatile(), wrapper, min, hi );
		return converted;
	}

	/**
	 * The {@link ColorConverter}s of {@code soc} and of its volatile
	 * counterpart -- everything a {@code ConverterSetup} for {@code soc} has to
	 * drive the display range of. Shared with
	 * {@code BigDataViewer#createConverterSetup} so that the set of converters
	 * a setup is built from and the set it is later re-pointed at are decided
	 * in one place.
	 */
	public static List< ColorConverter > colorConvertersOf( final SourceAndConverter< ? > soc )
	{
		final List< ColorConverter > converters = new ArrayList<>();
		if ( soc != null )
		{
			addIfColorConverter( converters, soc );
			addIfColorConverter( converters, soc.asVolatile() );
		}
		return converters;
	}

	/**
	 * The bundled palette a single-color converter's color is approximated by:
	 * {@code Greys} when the color is achromatic, otherwise the sequential
	 * palette named after its strongest channel.
	 * <p>
	 * Ties go to the earlier channel in R, G, B order, so yellow
	 * {@code (255, 255, 0)} is read as {@code Reds}. There is no better answer
	 * to be had: the bundled sequential palettes span one channel each, and any
	 * mixed hue has to be rounded onto one of them.
	 */
	public static String paletteNameForColor( final int argb )
	{
		final int r = ARGBType.red( argb );
		final int g = ARGBType.green( argb );
		final int b = ARGBType.blue( argb );
		if ( r == g && g == b )
			return "Greys";
		if ( r >= g && r >= b )
			return "Reds";
		if ( g >= b )
			return "Greens";
		return "Blues";
	}

	/**
	 * The palette {@code legacy}'s color maps to (see
	 * {@link #paletteNameForColor}), falling back to grayscale for a converter
	 * with no editable color to read, and to {@link Palette#DEFAULT} if the
	 * named resource cannot be loaded -- a missing palette file should degrade
	 * the result, not fail the conversion.
	 */
	static Palette paletteFor( final ColorConverter legacy )
	{
		final ARGBType color = legacy.supportsColor() ? legacy.getColor() : null;
		final Palette palette = LutPalettes.load( color == null ? "Greys" : paletteNameForColor( color.get() ) );
		return palette != null ? palette : Palette.DEFAULT;
	}

	private static boolean isRealTyped( final SourceAndConverter< ? > soc )
	{
		final Source< ? > source = soc.getSpimSource();
		return source != null && source.getType() instanceof RealType;
	}

	private static void addIfColorConverter( final List< ColorConverter > converters, final SourceAndConverter< ? > soc )
	{
		if ( soc == null )
			return;
		final Converter< ?, ARGBType > converter = soc.getConverter();
		if ( converter instanceof ColorConverter )
			converters.add( ( ColorConverter ) converter );
	}

	/**
	 * The source's pixel type is only known to be a {@code RealType} at
	 * runtime (see {@link #isRealTyped}), which no signature can express
	 * against a {@code SourceAndConverter<?>}; the raw types here are how that
	 * check is cashed in.
	 */
	@SuppressWarnings( { "unchecked", "rawtypes" } )
	private static PaletteConverter< ? > install( final SourceAndConverter< ? > soc, final PresetPaletteWrapper wrapper, final double min, final double max )
	{
		final PaletteConverter converter = new PaletteConverter<>( wrapper, min, max );
		( ( SourceAndConverter ) soc ).setConverter( converter );
		return converter;
	}
}
