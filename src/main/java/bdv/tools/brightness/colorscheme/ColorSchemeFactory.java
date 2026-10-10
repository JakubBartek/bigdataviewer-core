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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.imglib2.type.numeric.ARGBType;

/**
 * Discovers and loads the built-in color scheme resources: JSON files with a
 * {@code fixes_RGBA} array of {@code [r, g, b, a]} in [0, 1], one per fix,
 * and an optional {@code color_interpolation} boolean (default {@code true}),
 * which picks {@link ContinuousColorScheme} over {@link DiscreteColorScheme}.
 */
public final class ColorSchemeFactory {
    private static final String RESOURCE_DIR = "bdv/ui/colorschemes";

    private static final String RESOURCE_EXTENSION = ".json";

    private ColorSchemeFactory() {
    }

    /**
     * Resource file names without {@code .json}, sorted case-insensitively.
     */
    public static List<String> discoverNames() {
        final List<String> names = new ArrayList<>();
        try {
            final URL dirUrl = ColorSchemeFactory.class.getClassLoader().getResource(RESOURCE_DIR);
            if (dirUrl == null) {
                return names;
            }
            final URI uri = dirUrl.toURI();
            final TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            if ("jar".equals(uri.getScheme())) {
                try (final FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                     final Stream<Path> paths = Files.walk(fs.getPath(RESOURCE_DIR), 1)) {
                    collectNames(paths, sorted);
                }
            } else {
                try (final Stream<Path> paths = Files.walk(Paths.get(uri), 1)) {
                    collectNames(paths, sorted);
                }
            }
            names.addAll(sorted);
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return names;
    }

    private static void collectNames(final Stream<Path> paths, final Set<String> sorted) {
        paths.filter(p -> p.toString().endsWith(RESOURCE_EXTENSION))
                .forEach(p ->
                {
                    final String fn = p.getFileName().toString();
                    sorted.add(fn.substring(0, fn.length() - RESOURCE_EXTENSION.length()));
                });
    }

    /**
     * {@code null} if not found or not parseable. The name must match a
     * discovered one exactly: on Windows a directory lookup ignores case, so
     * {@code "Gray"} would otherwise load {@code gray.json}.
     *
     * @param name a name as returned by {@link #discoverNames()}.
     */
    public static IColorScheme load(final String name) {
        if (!exactNames().contains(name)) {
            return null;
        }
        final JsonObject root = readRoot(name);
        if (root == null) {
            return null;
        }
        final JsonArray colors = root.getAsJsonArray("fixes_RGBA");
        if (colors == null) {
            return null;
        }

        final int n = colors.size();
        if (n < 2) {
            return null;
        }

        final int[] fixes = new int[n];
        for (int i = 0; i < n; i++) {
            final JsonArray rgba = colors.get(i).getAsJsonArray();
            fixes[i] = ARGBType.rgba(
                    ColorSchemeHelpers.unitToChannel(rgba.get(0).getAsDouble()),
                    ColorSchemeHelpers.unitToChannel(rgba.get(1).getAsDouble()),
                    ColorSchemeHelpers.unitToChannel(rgba.get(2).getAsDouble()),
                    ColorSchemeHelpers.unitToChannel(rgba.get(3).getAsDouble()));
        }
        final boolean interpolated = !root.has("color_interpolation") || root.get("color_interpolation").getAsBoolean();
        return interpolated ? new ContinuousColorScheme(fixes) : new DiscreteColorScheme(fixes);
    }

    /**
     * Reverse of {@link #load(String)}; {@code null} if no resource matches.
     */
    public static synchronized String findName(final IColorScheme scheme) {
        if (scheme == null) {
            return null;
        }
        for (final Map.Entry<String, IColorScheme> candidate : cachedSchemes().entrySet()) {
            if (scheme.equals(candidate.getValue())) {
                return candidate.getKey();
            }
        }
        return null;
    }

    /**
     * Parsed once: {@link #findName} runs on the EDT on every source change.
     */
    private static Map<String, IColorScheme> cachedSchemes() {
        if (cachedSchemes == null) {
            final Map<String, IColorScheme> schemes = new LinkedHashMap<>();
            for (final String name : discoverNames()) {
                final IColorScheme scheme = load(name);
                if (scheme != null) {
                    schemes.put(name, scheme);
                }
            }
            cachedSchemes = schemes;
        }
        return cachedSchemes;
    }

    /**
     * Guarded by {@code ColorSchemeFactory.class}, via {@link #findName}.
     */
    private static Map<String, IColorScheme> cachedSchemes = null;

    /**
     * Cached like {@link #cachedSchemes()}, so {@link #load} does not walk the resources each time.
     */
    private static synchronized Set<String> exactNames() {
        if (exactNames == null) {
            exactNames = new HashSet<>(discoverNames());
        }
        return exactNames;
    }

    /**
     * Guarded by {@code ColorSchemeFactory.class}, via {@link #exactNames()}.
     */
    private static Set<String> exactNames = null;

    private static JsonObject readRoot(final String name) {
        final String path = RESOURCE_DIR + "/" + name + RESOURCE_EXTENSION;
        try (final InputStream is = ColorSchemeFactory.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
        } catch (final IOException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }
}
