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
package bdv.tools.brightness.editor;

import java.util.ArrayList;
import java.util.List;

import bdv.tools.brightness.presetfunc.CustomInterpPresetFunc;

/**
 * The editable control points of the LUT editor's curve, kept valid as
 * {@link CustomInterpPresetFunc} knots, which evaluate them: x and y in
 * {@code [0, 1]}, x strictly ascending, at least two points.
 */
public class Curve {
    private final List<Point> points = new ArrayList<>(List.of(new Point(0.0, 0.0), new Point(1.0, 1.0)));

    /**
     * Adds a control point, or updates the y of one already at {@code x}.
     * Both are clamped to {@code [0, 1]}.
     */
    public void addPoint(final double x, final double y)
    {
        final double cx = clamp(x);
        final double cy = clamp(y);
        int insertIdx = this.points.size();
        for (int i = 0; i < this.points.size(); i++) {
            final double px = this.points.get(i).x;
            if (Math.abs(px - cx) < 1e-6) {
                this.points.set(i, new Point(px, cy));
                return;
            }
            if (px > cx) {
                insertIdx = i;
                break;
            }
        }
        this.points.add(insertIdx, new Point(cx, cy));
    }

    /**
     * Cannot remove the first or last point.
     */
    public void removePoint(final int index)
    {
        if (index <= 0 || index >= this.points.size() - 1) {
            return;
        }
        this.points.remove(index);
    }

    /**
     * The index of the point nearest to {@code (x, y)} within {@code tolerance}, or -1.
     */
    public int findNearestPoint(final double x, final double y, final double tolerance)
    {
        int bestIdx = -1;
        double bestDist = tolerance;

        for (int i = 0; i < this.points.size(); i++) {
            final double dx = x - this.points.get(i).x;
            final double dy = y - this.points.get(i).y;
            final double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    public double getX(final int index)
    {
        return this.points.get(index).x;
    }

    public double getY(final int index)
    {
        return this.points.get(index).y;
    }

    public int getPointCount()
    {
        return this.points.size();
    }

    /**
     * {@code y} is clamped to {@code [0, 1]}.
     */
    public void setPoint(final int index, final double y)
    {
        if (index >= 0 && index < this.points.size()) {
            this.points.set(index, new Point(this.points.get(index).x, clamp(y)));
        }
    }

    /**
     * Replaces all control points.
     *
     * @throws IllegalArgumentException if either array is missing or the points
     *                                  are not valid {@link CustomInterpPresetFunc#requireValidKnots knots}.
     */
    public void setPoints(final double[] xs, final double[] ys)
    {
        if (xs == null || ys == null) {
            throw new IllegalArgumentException("curve points are missing");
        }
        CustomInterpPresetFunc.requireValidKnots(xs, ys);
        this.points.clear();
        for (int i = 0; i < xs.length; i++) {
            this.points.add(new Point(xs[i], ys[i]));
        }
    }

    public double[] xsArray()
    {
        return this.points.stream().mapToDouble(p -> p.x).toArray();
    }

    public double[] ysArray()
    {
        return this.points.stream().mapToDouble(p -> p.y).toArray();
    }

    /**
     * Flips the curve vertically: each y becomes {@code 1 - y}.
     */
    public void invert()
    {
        this.points.replaceAll(p -> new Point(p.x, 1.0 - p.y));
    }

    /**
     * @throws IllegalArgumentException for NaN, which no clamp can place.
     */
    private static double clamp(final double v)
    {
        if (Double.isNaN(v)) {
            throw new IllegalArgumentException("curve coordinate is NaN");
        }
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static final class Point {
        final double x;

        final double y;

        Point(final double x, final double y)
        {
            this.x = x;
            this.y = y;
        }
    }
}
