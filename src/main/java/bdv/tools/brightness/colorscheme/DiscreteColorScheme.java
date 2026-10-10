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

import java.util.Arrays;

/**
 * {@code N} color fixes without blending (e.g. label ids). The domain is the
 * half-open interval {@code [0, N)}; a scheme value is floored to its fix.
 */
public class DiscreteColorScheme extends AbstractColorScheme {
    public DiscreteColorScheme(final int[] argbFixes) {
        super(argbFixes);
    }

    @Override
    public int getRange() {
        return fixes.length;
    }

    @Override
    int colorAt(final double schemeValue) {
        final int lastIndex = fixes.length - 1;
        final int index = Math.max(0, Math.min(lastIndex, (int) Math.floor(schemeValue)));
        return fixes[index];
    }

    /**
     * Never equal to a {@link ContinuousColorScheme}, even with the same fixes.
     */
    @Override
    public final boolean equals(final Object obj) {
        return obj instanceof DiscreteColorScheme && hasSameFixes((DiscreteColorScheme) obj);
    }

    @Override
    public final int hashCode() {
        return Arrays.hashCode(fixes);
    }
}
