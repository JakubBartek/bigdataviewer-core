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
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Discovers, loads and saves {@link EditorPreset}s: built-in ones under
 * {@value #BUILTIN_RESOURCE_DIR}, and user-saved ones in its
 * {@value #USER_SUBDIR} subdirectory (git-ignored), which take precedence.
 * Saving only works when the resource directory is on the filesystem, not in
 * a jar.
 */
public final class EditorPresetHelper {
    private static final String BUILTIN_RESOURCE_DIR = "bdv/ui/lut-editor-presets";

    private static final String USER_SUBDIR = "user";

    private static final String RESOURCE_EXTENSION = ".json";

    /**
     * Overrides {@link #userDir()}, for tests.
     */
    static final String USER_DIR_OVERRIDE_PROPERTY = "bdv.tools.brightness.editor.EditorPresetHelper.userDir";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EditorPresetHelper() {
    }

    /**
     * {@code null} if there is no writable directory; built-in presets still load.
     */
    private static String userDir() {
        final String override = System.getProperty(USER_DIR_OVERRIDE_PROPERTY);
        if (override != null) {
            return override;
        }
        final File builtinDir = resolveBuiltinResourceDir();
        return builtinDir == null ? null : new File(builtinDir, USER_SUBDIR).getAbsolutePath();
    }

    /**
     * {@code null} when not on the filesystem, e.g. in a jar.
     */
    private static File resolveBuiltinResourceDir() {
        final URL dirUrl = EditorPresetHelper.class.getClassLoader().getResource(BUILTIN_RESOURCE_DIR);
        if (dirUrl == null) {
            return null;
        }
        try {
            return new File(dirUrl.toURI());
        } catch (final URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static File userFile(final String name) {
        final String dir = userDir();
        return dir == null ? null : new File(dir, canonicalName(name) + RESOURCE_EXTENSION);
    }

    /**
     * Built-in and user-saved names, de-duplicated and sorted case-insensitively.
     */
    public static List<String> discoverNames() {
        final TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            final URL dirUrl = EditorPresetHelper.class.getClassLoader().getResource(BUILTIN_RESOURCE_DIR);
            if (dirUrl != null) {
                final URI uri = dirUrl.toURI();
                if ("jar".equals(uri.getScheme())) {
                    try (final FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                         final Stream<Path> paths = Files.walk(fs.getPath(BUILTIN_RESOURCE_DIR), 1)) {
                        collectNames(paths, sorted);
                    }
                } else {
                    try (final Stream<Path> paths = Files.walk(Paths.get(uri), 1)) {
                        collectNames(paths, sorted);
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        final String userDirPath = userDir();
        final File[] userFiles = userDirPath == null ? null : new File(userDirPath).listFiles();
        if (userFiles != null) {
            for (final File f : userFiles) {
                if (f.getName().endsWith(RESOURCE_EXTENSION)) {
                    sorted.add(stripExtension(f.getName()));
                }
            }
        }

        return new ArrayList<>(sorted);
    }

    private static void collectNames(final Stream<Path> paths, final Set<String> sorted) {
        paths.filter(p -> p.toString().endsWith(RESOURCE_EXTENSION))
                .forEach(p -> sorted.add(stripExtension(p.getFileName().toString())));
    }

    private static String stripExtension(final String fileName) {
        return fileName.substring(0, fileName.length() - RESOURCE_EXTENSION.length());
    }

    /**
     * {@code true} if a user-saved file exists, even when a built-in one does too.
     */
    public static boolean isUserDefined(final String name) {
        final File file = userFile(name);
        return file != null && file.isFile();
    }

    /**
     * Prefers the user-saved file; {@code null} if missing or not parseable.
     *
     * @param name a name as returned by {@link #discoverNames()}.
     */
    public static EditorPreset load(final String name) {
        final File userFile = userFile(name);
        if (userFile != null && userFile.isFile()) {
            try (final FileReader reader = new FileReader(userFile)) {
                return GSON.fromJson(reader, EditorPreset.class);
            } catch (final IOException | RuntimeException e) {
                e.printStackTrace();
                return null;
            }
        }

        final String path = BUILTIN_RESOURCE_DIR + "/" + canonicalName(name) + RESOURCE_EXTENSION;
        try (final InputStream is = EditorPresetHelper.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return GSON.fromJson(new InputStreamReader(is), EditorPreset.class);
        } catch (final IOException | RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Overwrites an existing file of the same name.
     *
     * @throws IllegalStateException    if there is no writable directory.
     * @throws IllegalArgumentException if the name is not {@link #canonicalName canonical}.
     */
    public static void save(final EditorPreset preset) {
        final String name = preset.getName();
        if (!canonicalName(name).equals(name)) {
            throw new IllegalArgumentException("Preset name \"" + name + "\" is not canonical; "
                    + "pass EditorPresetHelper.canonicalName(...) through first (would be \"" + canonicalName(name) + "\")");
        }

        final File file = userFile(name);
        if (file == null) {
            throw new IllegalStateException("No writable settings directory available"
                    + " (running from a packaged jar?); cannot save \"" + name + "\"");
        }
        file.getParentFile().mkdirs();
        try (final FileWriter writer = new FileWriter(file)) {
            GSON.toJson(preset, writer);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to save LUT editor setting \"" + preset.getName() + "\" to " + file, e);
        }
    }

    /**
     * Trimmed, with filesystem-significant characters replaced. Callers must
     * canonicalize a typed name before comparing it with {@link #discoverNames()}.
     */
    public static String canonicalName(final String name) {
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
