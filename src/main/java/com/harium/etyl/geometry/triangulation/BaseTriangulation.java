package com.harium.etyl.geometry.triangulation;

import com.badlogic.gdx.math.Vector3;

public abstract class BaseTriangulation implements Triangulation {

    // the triangle the found (search start from)
    protected Triangle startTriangle;

    /**
     * finds the triangle the query point falls in, note if out-side of this
     * triangulation a half plane triangle will be returned (see contains), the
     * search has expected time of O(n^0.5), and it starts form a fixed triangle
     * (this.startTriangle),
     *
     * @param vertex
     *            query point
     * @return the triangle that point p is in.
     */
    public Triangle find(Vector3 vertex) {

        // If triangulation has a spatial index try to use it as the starting triangle
        Triangle searchTriangle = startTriangle;

        // Search for the point's triangle starting from searchTriangle
        return find(searchTriangle, vertex);
    }

    protected static Triangle find(Triangle curr, Vector3 p) {
        if (p == null)
            return null;

        Triangle next_t;
        if (curr.halfplane) {
            next_t = findnext2(p, curr);
            if (next_t == null || next_t.halfplane)
                return curr;
            curr = next_t;
        }
        while (true) {
            next_t = findnext1(p, curr);
            if (next_t == null)
                return curr;
            if (next_t.halfplane)
                return next_t;
            curr = next_t;
        }
    }

    /**
     * finds the triangle the query point falls in, note if out-side of this
     * triangulation a half plane triangle will be returned (see contains). the
     * search starts from the the start triangle
     *
     * @param p
     *            query point
     * @param start
     *            the triangle the search starts at.
     * @return the triangle that point p is in..
     */
    public Triangle find(Vector3 p, Triangle start) {
        if (start == null)
            start = this.startTriangle;
        Triangle T = find(start, p);
        return T;
    }

    /*
     * assumes v is NOT an halfplane!
     * returns the next triangle for find.
     */
    protected static Triangle findnext1(Vector3 p, Triangle v) {
        if (!v.abnext.halfplane && PointLineTest.pointLineTest(v.a, v.b, p) == PointLinePosition.RIGHT)
            return v.abnext;
        if (!v.bcnext.halfplane && PointLineTest.pointLineTest(v.b, v.c, p) == PointLinePosition.RIGHT)
            return v.bcnext;
        if (!v.canext.halfplane && PointLineTest.pointLineTest(v.c, v.a, p) == PointLinePosition.RIGHT)
            return v.canext;
        if (PointLineTest.pointLineTest(v.a, v.b, p) == PointLinePosition.RIGHT)
            return v.abnext;
        if (PointLineTest.pointLineTest(v.b, v.c, p) == PointLinePosition.RIGHT)
            return v.bcnext;
        if (PointLineTest.pointLineTest(v.c, v.a, p) == PointLinePosition.RIGHT)
            return v.canext;
        return null;
    }

    /** assumes v is an halfplane! - returns another (none halfplane) triangle */
    protected static Triangle findnext2(Vector3 p, Triangle v) {
        if (v.abnext != null && !v.abnext.halfplane)
            return v.abnext;
        if (v.bcnext != null && !v.bcnext.halfplane)
            return v.bcnext;
        if (v.canext != null && !v.canext.halfplane)
            return v.canext;
        return null;
    }

}
