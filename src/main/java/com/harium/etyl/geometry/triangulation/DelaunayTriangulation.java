package com.harium.etyl.geometry.triangulation;

import com.badlogic.gdx.math.Vector3;
import com.harium.etyl.geometry.BoundingBox;

import java.util.*;

import static com.harium.etyl.geometry.triangulation.PointLinePosition.ON_SEGMENT;


/**
 *
 * This class represents a Delaunay Triangulation. The class was written for a
 * large scale triangulation (1000 - 200,000 vertices). The application main use is 3D surface (terrain) presentation.
 * <br>
 * The class main properties are the following:<br>
 * - fast point location. (O(n^0.5)), practical runtime is often very fast. <br>
 * - handles degenerate cases and none general examples.position input (ignores duplicate points). <br>
 * - save & load from\to text file in TSIN format. <br>
 * - 3D support: including z value approximation. <br>
 * - standard java (1.5 generic) iterators for the vertices and triangles. <br>
 * - smart iterator to only the updated triangles - for terrain simplification <br>
 * <br>
 *
 * Testing (done in early 2005): Platform java 1.5.02 windows XP (SP2), AMD laptop 1.6G sempron CPU
 * 512MB RAM. Constructing a triangulation of 100,000 vertices takes ~ 10
 * seconds. point location of 100,000 points on a triangulation of 100,000
 * vertices takes ~ 5 seconds.
 *
 * Note: constructing a triangulation with 200,000 vertices and more requires
 * extending java heap size (otherwise an exception will be thrown).<br>
 *
 * Bugs: if U find a bug or U have an idea as for how to improve the code,
 * please send me an email to: benmo@ariel.ac.il
 *
 * @author Boaz Ben Moshe 5/11/05 <br>
 * The project uses some ideas presented in the VoroGuide project, written by Klasse f?r Kreise (1996-1997),
 * For the original applet see: http://www.pi6.fernuni-hagen.de/GeomLab/VoroGlide/ . <br>
 */

public class DelaunayTriangulation extends BaseTriangulation {

	// the first and last points (used only for first step construction)
	private Vector3 firstP;
	private Vector3 lastP;

	// for degenerate case!
	private boolean allCollinear;

	// the first and last triangles (used only for first step construction)
	private Triangle firstT, lastT;


	// the triangle the convex hull starts from
	public Triangle startTriangleHull;

	private int modCount = 0, modCount2 = 0;

	// the Bounding Box, {{x0,y0,z0} , {x1,y1,z1}}
	private Vector3 bbMin, bbMax;

	/**
	 * Index for faster point location searches
	 */
	//private GridIndex gridIndex = null;

	/**
	 * Constructor: creates a Delaunay Triangulation.
	 */
	public DelaunayTriangulation() {
		init(64);
	}

	private void init(int size) {
		modCount = 0;
		modCount2 = 0;
		allCollinear = true;
	}

	/**
	 * returns the changes counter for this triangulation
	 */
	public int getModeCounter() {
		return this.modCount;
	}

	/**
	 * insert the point to this Delaunay Triangulation. Note: if p is null or
	 * already exist in this triangulation p is ignored.
	 * @param vertices
	 * @param p new vertex to be inserted the triangulation.
	 */
	public void insertPoint(Set<Vector3> vertices, Vector3 p) {

		modCount++;
		updateBoundingBox(p);
		vertices.add(p);
		Triangle t = insertPointSimple(vertices, p);

		if (t == null) //
			return;

		Triangle tt = t;
		//currT = t; // recall the last point for - fast (last) update iterator.
		do {
			flip(tt, modCount);
			tt = tt.canext;
		} while (tt != t && !tt.halfplane);

	}

	/** return a point from the trangulation that is close to pointToDelete
	 * @param pointToDelete the point that the user wants to delete
	 * @return a point from the trangulation that is close to pointToDelete
	 * By Eyal Roth & Doron Ganel (2009).
	 */
	public Vector3 findClosePoint(Vector3 pointToDelete) {
		Triangle triangle = find(pointToDelete);
		Vector3 p1 = triangle.p1();
		Vector3 p2 = triangle.p2();
		double d1 = distanceXY(p1, pointToDelete);
		double d2 = distanceXY(p2, pointToDelete);
		if(triangle.isHalfplane()) {
			if(d1<=d2) {
				return p1;
			}
			else {
				return p2;
			}
		} else {
			Vector3 p3 = triangle.p3();

			double d3 = distanceXY(p3, pointToDelete);

			if(d1<=d2 && d1<=d3) {
				return p1;
			}
			else if(d2<=d1 && d2<=d3) {
				return p2;
			}
			else {
				return p3;
			}
		}
	}

	private float distanceXY(Vector3 p, Vector3 q) {
		return p.dst(q);
	}

	/**
	 * Calculates a Voronoi cell for a given neighborhood
	 * in this triangulation. A neighborhood is defined by a triangle
	 * and one of its corner points.
	 *
	 * By Udi Schneider
	 *
	 * @param triangle a triangle in the neighborhood
	 * @param p corner point whose surrounding neighbors will be checked
	 * @return set of Points representing the cell polygon
	 */
	public Vector3[] calcVoronoiCell(Triangle triangle, Vector3 p) {
		// handle any full triangle
		if (!triangle.isHalfplane()) {

			// get all neighbors of given corner point
			List<Triangle> neighbors = findTriangleNeighborhood(triangle, p);

			Iterator<Triangle> itn = neighbors.iterator();
			Vector3[] vertices = new Vector3[neighbors.size()];

			// for each neighbor, including the given triangle, add
			// center of circumscribed circle to cell polygon
			int index = 0;
			while (itn.hasNext()) {
				Triangle tmp = itn.next();
				vertices[index++] = tmp.circumcircle().getCenter();
			}

			return vertices;
		}

		// handle half plane
		// in this case, the cell is a single line
		// which is the perpendicular bisector of the half plane line
		else {
			// local friendly alias
			Triangle halfplane = triangle;
			// third point of triangle adjacent to this half plane
			// (the point not shared with the half plane)
			Vector3 third = null;
			// triangle adjacent to the half plane
			Triangle neighbor = null;

			// find the neighbor triangle
			if (!halfplane.next_12().isHalfplane()) {
				neighbor = halfplane.next_12();
			}
			else if (!halfplane.next_23().isHalfplane()) {
				neighbor = halfplane.next_23();
			}
			else if (!halfplane.next_23().isHalfplane()) {
				neighbor = halfplane.next_31();
			}

			// find third point of neighbor triangle
			// (the one which is not shared with current half plane)
			// this is used in determining half plane orientation
			if (!neighbor.p1().equals(halfplane.p1()) && !neighbor.p1().equals(halfplane.p2()) )
				third = neighbor.p1();
			if (!neighbor.p2().equals(halfplane.p1()) && !neighbor.p2().equals(halfplane.p2()) )
				third = neighbor.p2();
			if (!neighbor.p3().equals(halfplane.p1()) && !neighbor.p3().equals(halfplane.p2()) )
				third = neighbor.p3();

			// delta (slope) of half plane edge
			float halfplane_delta = (halfplane.p1().y - halfplane.p2().y) /
					(halfplane.p1().x - halfplane.p2().x);

			// delta of line perpendicular to current half plane edge
			float perp_delta = (1.0f / halfplane_delta) * (-1.0f);

			// determine orientation: find if the third point of the triangle
			// lies above or below the half plane
			// works by finding the matching y value on the half plane line equation
			// for the same x value as the third point
			float y_orient =  halfplane_delta * (third.x - halfplane.p1().x)+ halfplane.p1().y;
			boolean above = true;
			if (y_orient > third.y)
				above = false;

			// based on orientation, determine cell line direction
			// (towards right or left side of window)
			float sign = 1.0f;
			if ((perp_delta < 0 && !above) || (perp_delta > 0 && above)) {
				sign = -1.0f;
			}

			// the cell line is a line originating from the circumcircle to infinity
			// x = 500.0 is used as a large enough value
			Vector3 circumcircle = neighbor.circumcircle().getCenter();
			float x_cell_line = (circumcircle.x + (500.0f * sign));
			float y_cell_line = perp_delta * (x_cell_line - circumcircle.x) + circumcircle.y;

			Vector3[] result = new Vector3[2];
			result[0] = circumcircle;
			result[1] = new Vector3(x_cell_line, y_cell_line, 0);

			return result;
		}
	}

	private Triangle insertPointSimple(Set<Vector3> vertices, Vector3 p) {
		if (!allCollinear) {
			return insertNonColinear(p);
		} else {
			return insertColinear(vertices, p);
		}
	}

	private Triangle insertColinear(Set<Vector3> vertices, Vector3 p) {
		if (vertices.size() == 1) {
			firstP = p;
		} else if (vertices.size() == 2) {
			startTriangulation(firstP, p);
		} else {
			testPoint(p);
		}
		return null;
	}

	private Triangle insertNonColinear(Vector3 p) {
		Triangle t = find(startTriangle, p);
		if (t.halfplane)
			startTriangle = extendOutside(t, p);
		else
			startTriangle = extendInside(t, p);
		return startTriangle;
	}

	private void testPoint(Vector3 p) {
		PointLinePosition position = PointLineTest.pointLineTest(firstP, lastP, p);
		switch (position) {
		case LEFT:
			startTriangle = extendOutside(firstT.abnext, p);
			allCollinear = false;
			break;
		case RIGHT:
			startTriangle = extendOutside(firstT, p);
			allCollinear = false;
			break;
		case ON_SEGMENT:
			case INFRONT_OF_A:
			case BEHIND_B:
			insertCollinear(p, position);
			break;
		}
	}

	private void insertCollinear(Vector3 p, PointLinePosition res) {
		Triangle t, tp, u;

		switch (res) {
		case INFRONT_OF_A:
			t = new Triangle(firstP, p);
			tp = new Triangle(p, firstP);
			t.abnext = tp;
			tp.abnext = t;
			t.bcnext = tp;
			tp.canext = t;
			t.canext = firstT;
			firstT.bcnext = t;
			tp.bcnext = firstT.abnext;
			firstT.abnext.canext = tp;
			firstT = t;
			firstP = p;
			break;
		case BEHIND_B:
			t = new Triangle(p, lastP);
			tp = new Triangle(lastP, p);
			t.abnext = tp;
			tp.abnext = t;
			t.bcnext = lastT;
			lastT.canext = t;
			t.canext = tp;
			tp.bcnext = t;
			tp.canext = lastT.abnext;
			lastT.abnext.bcnext = tp;
			lastT = t;
			lastP = p;
			break;
		case ON_SEGMENT:
			u = firstT;
			while (PointComparator.isGreater(p, u.a))
				u = u.canext;
			t = new Triangle(p, u.b);
			tp = new Triangle(u.b, p);
			u.b = p;
			u.abnext.a = p;
			t.abnext = tp;
			tp.abnext = t;
			t.bcnext = u.bcnext;
			u.bcnext.canext = t;
			t.canext = u;
			u.bcnext = t;
			tp.canext = u.abnext.canext;
			u.abnext.canext.bcnext = tp;
			tp.bcnext = u.abnext;
			u.abnext.canext = tp;
			if (firstT.equals(u)) {
				System.out.println("is equal triangle");
				firstT = t;
			}
			break;
			default:
			    break;
		}
	}

	private void startTriangulation(Vector3 p1, Vector3 p2) {
		Vector3 ps, pb;
		if (PointComparator.isLess(p1, p2)) {
			ps = p1;
			pb = p2;
		} else {
			ps = p2;
			pb = p1;
		}
		firstT = new Triangle(pb, ps);
		lastT = firstT;

		Triangle t = new Triangle(ps, pb);
		firstT.abnext = t;
		t.abnext = firstT;
		firstT.bcnext = t;
		t.canext = firstT;
		firstT.canext = t;
		t.bcnext = firstT;

		firstP = firstT.b;
		lastP = lastT.a;
		startTriangleHull = firstT;
	}

	private Triangle extendInside(Triangle t, Vector3 p) {

		Triangle h1, h2;
		h1 = treatDegeneracyInside(t, p);
		if (h1 != null)
			return h1;

		h1 = new Triangle(t.c, t.a, p);
		h2 = new Triangle(t.b, t.c, p);
		t.c = p;
		t.circumcircle();
		h1.abnext = t.canext;
		h1.bcnext = t;
		h1.canext = h2;
		h2.abnext = t.bcnext;
		h2.bcnext = h1;
		h2.canext = t;
		h1.abnext.switchneighbors(t, h1);
		h2.abnext.switchneighbors(t, h2);
		t.bcnext = h2;
		t.canext = h1;
		return t;
	}

	private Triangle treatDegeneracyInside(Triangle t, Vector3 p) {

		if (t.abnext.halfplane
				&& PointLineTest.pointLineTest(t.b, t.a, p) == ON_SEGMENT)
			return extendOutside(t.abnext, p);
		if (t.bcnext.halfplane
				&& PointLineTest.pointLineTest(t.c, t.b, p) == ON_SEGMENT)
			return extendOutside(t.bcnext, p);
		if (t.canext.halfplane
				&& PointLineTest.pointLineTest(t.a, t.c, p) == ON_SEGMENT)
			return extendOutside(t.canext, p);
		return null;
	}

	private Triangle extendOutside(Triangle t, Vector3 p, PointLinePosition test) {
		if (ON_SEGMENT == test) {
			Triangle dg = new Triangle(t.a, t.b, p);
			Triangle hp = new Triangle(p, t.b);
			t.b = p;
			dg.abnext = t.abnext;
			dg.abnext.switchneighbors(t, dg);
			dg.bcnext = hp;
			hp.abnext = dg;
			dg.canext = t;
			t.abnext = dg;
			hp.bcnext = t.bcnext;
			hp.bcnext.canext = hp;
			hp.canext = t;
			t.bcnext = hp;
			return dg;
		}

		Triangle ccT = extendcounterclock(t, p);
		Triangle cT = extendclock(t, p);
		ccT.bcnext = cT;
		cT.canext = ccT;
		startTriangleHull = cT;
		return cT.abnext;
	}

	private Triangle extendOutside(Triangle t, Vector3 p) {
		return extendOutside(t, p, PointLineTest.pointLineTest(t.a, t.b, p));
	}

	private Triangle extendcounterclock(Triangle t, Vector3 p) {

		t.halfplane = false;
		t.c = p;
		t.circumcircle();

		Triangle tca = t.canext;

		PointLinePosition position = PointLineTest.pointLineTest(tca.a, tca.b, p);
		switch (position) {
            case RIGHT:
            case BEHIND_B:
            case INFRONT_OF_A:
            case ERROR:
                Triangle nT = new Triangle(t.a, p);
                nT.abnext = t;
                t.canext = nT;
                nT.canext = tca;
                tca.bcnext = nT;
                return nT;
            default:
            case ON_SEGMENT:
            case LEFT:
                return extendcounterclock(tca, p);
        }
	}

	private Triangle extendclock(Triangle t, Vector3 p) {

		t.halfplane = false;
		t.c = p;
		t.circumcircle();

		Triangle tbc = t.bcnext;

        PointLinePosition position = PointLineTest.pointLineTest(tbc.a, tbc.b, p);
        switch (position) {
            case RIGHT:
            case BEHIND_B:
            case INFRONT_OF_A:
            case ERROR:
			Triangle nT = new Triangle(p, t.b);
			nT.abnext = t;
			t.bcnext = nT;
			nT.bcnext = tbc;
			tbc.canext = nT;
			    return nT;
            default:
            case ON_SEGMENT:
            case LEFT:
                return extendclock(tbc, p);
		}
	}

	private void flip(Triangle t, int mc) {

		Triangle u = t.abnext, v;
		t.modCounter = mc;
		if (u.halfplane || !u.circumcircleContains(t.c)) {
			return;
		}

		if (t.a == u.a) {
			v = new Triangle(u.b, t.b, t.c);
			v.abnext = u.bcnext;
			t.abnext = u.abnext;
		} else if (t.a == u.b) {
			v = new Triangle(u.c, t.b, t.c);
			v.abnext = u.canext;
			t.abnext = u.bcnext;
		} else if (t.a == u.c) {
			v = new Triangle(u.a, t.b, t.c);
			v.abnext = u.abnext;
			t.abnext = u.canext;
		} else {
			throw new RuntimeException("Error in flip.");
		}

		v.modCounter = mc;
		v.bcnext = t.bcnext;
		v.abnext.switchneighbors(u, v);
		v.bcnext.switchneighbors(t, v);
		t.bcnext = v;
		v.canext = t;
		t.b = v.a;
		t.abnext.switchneighbors(u, t);
		t.circumcircle();

		//currT = v;
		flip(t, mc);
		flip(v, mc);
	}

	/**
	 * compute the number of vertices in the convex hull. <br />
	 * NOTE: has a 'bug-like' behavor: <br />
	 * in cases of colinear - not on a asix parallel rectangle,
	 * colinear points are reported
	 *
	 * @return the number of vertices in the convex hull.
	 */
	public int convexHullSize() {
		int ans = 0;
		Iterator<Vector3> it = this.getConvexHullVerticesIterator();
		while (it.hasNext()) {
			ans++;
			it.next();
		}
		return ans;
	}

	public List<Vector3> findConnectedVertices(Vector3 point, List<Triangle> triangles) {
		// Finding the triangles to delete.
		List<Triangle> deletedTriangles = findConnectedTriangles(point);

		Set<Vector3> pointsSet = new HashSet<Vector3>();
		List<Vector3> pointsVec = new ArrayList<Vector3>();

		if (deletedTriangles != null) {
			connectTriangles(point, pointsSet, pointsVec, triangles);
			return pointsVec;
		} else {
			System.err.println("findConnectedVertices: Could not find connected vertices since the first found triangle doesn't" +
					" share the given point.");
			return null;
		}
	}

	/*
	 * Receives a point and returns all the points of the triangles that
	 * shares point as a corner (Connected vertices to this point).
	 *
	 * By Doron Ganel & Eyal Roth
	 */
	private List<Triangle> findConnectedTriangles(Vector3 point) {

		// Getting one of the neigh
		Triangle triangle = find(point);

		// Validating find results.
		if (!triangle.isCorner(point)) {
			System.err.println("findConnectedTriangles: Could not find connected vertices since the first found triangle doesn't" +
					" share the given point.");
			return null;
		}

		return findTriangleNeighborhood(triangle, point);
	}

	private void connectTriangles(Vector3 point, Set<Vector3> pointsSet,
			List<Vector3> pointsVec, List<Triangle> triangles) {
		for (Triangle tmpTriangle : triangles) {
			Vector3 point1 = tmpTriangle.p1();
			Vector3 point2 = tmpTriangle.p2();
			Vector3 point3 = tmpTriangle.p3();

			if (point1.equals(point) && !pointsSet.contains(point2)) {
				pointsSet.add(point2);
				pointsVec.add(point2);
			}

			if (point2.equals(point) && !pointsSet.contains(point3)) {
				pointsSet.add(point3);
				pointsVec.add(point3);
			}

			if (point3.equals(point)&& !pointsSet.contains(point1)) {
				pointsSet.add(point1);
				pointsVec.add(point1);
			}
		}
	}

	// Walks on a consistent side of triangles until a cycle is achieved.
	//By Doron Ganel & Eyal Roth
	// changed to public by Udi
	public List<Triangle> findTriangleNeighborhood(Triangle firstTriangle, Vector3 point) {
		List<Triangle> triangles = new ArrayList<Triangle>(30);
		triangles.add(firstTriangle);

		Triangle prevTriangle = null;
		Triangle currentTriangle = firstTriangle;
		Triangle nextTriangle = currentTriangle.nextNeighbor(point, prevTriangle);

		while (!nextTriangle.equals(firstTriangle)) {
			//the point is on the perimeter
			if(nextTriangle.isHalfplane()) {
				return null;
			}

			triangles.add(nextTriangle);
			prevTriangle = currentTriangle;
			currentTriangle = nextTriangle;
			nextTriangle = currentTriangle.nextNeighbor(point, prevTriangle);
		}

		return triangles;
	}

	/**
	 *
	 * @param p
	 *            query point
	 * @return true iff p is within this triangulation (in its 2D convex hull).
	 */

	public boolean contains(Vector3 p) {
		Triangle tt = find(p);
		return !tt.halfplane;
	}

	/**
	 *
	 * @param x
	 *            - X cordination of the query point
	 * @param y
	 *            - Y cordination of the query point
	 * @return true iff (x,y) falls inside this triangulation (in its 2D convex
	 *         hull).
	 */
	public boolean contains(float x, float y) {
		return contains(new Vector3(x, y,0));
	}

	/**
	 *
	 * @param q
	 *            Query point
	 * @return the q point with updated Z value (z value is as given the
	 *         triangulation).
	 */
	public Vector3 z(Vector3 q) {
		Triangle t = find(q);
		return t.z(q);
	}

	/**
	 *
	 * @param x
	 *            - X cordination of the query point
	 * @param y
	 *            - Y cordination of the query point
	 * @return the q point with updated Z value (z value is as given the
	 *         triangulation).
	 */
	public double z(float x, float y) {
		Vector3 q = new Vector3(x, y,0);
		Triangle t = find(q);
		return t.z_value(q);
	}

	private void updateBoundingBox(Vector3 p) {
		float x = p.x, y = p.y, z = p.z;

		// Check X
		if (x < bbMin.x) {
			bbMin.x = x;
		} else if (x > bbMax.x) {
			bbMax.x = x;
		}

		// Check Y
		if (y < bbMin.y) {
			bbMin.y = y;
		} else if (y > bbMax.y) {
			bbMax.y = y;
		}

		// Check Z
		if (z < bbMin.z) {
			bbMin.z = z;
		} else if (z > bbMax.z) {
			bbMax.z = z;
		}
	}
	/**
	 * @return  The bounding rectange between the minimum and maximum coordinates
	 */
	public BoundingBox getBoundingBox() {
		return new BoundingBox(bbMin, bbMax);
	}

	/**
	 * return the min point of the bounding box of this triangulation
	 * {{x0,y0,z0}}
	 */
	public Vector3 minBoundingBox() {
		return bbMin;
	}

	/**
	 * return the max point of the bounding box of this triangulation
	 * {{x1,y1,z1}}
	 */
	public Vector3 maxBoundingBox() {
		return bbMax;
	}

	/**
	 * returns an iterator to the set of all the points on the XY-convex hull
	 * @return iterator to the set of all the points on the XY-convex hull.
	 */
	private Iterator<Vector3> getConvexHullVerticesIterator() {
		List<Vector3> ans = new ArrayList<Vector3>();
		Triangle curr = this.startTriangleHull;
		boolean cont = true;
		double x0 = bbMin.x, x1 = bbMax.x;
		double y0 = bbMin.y, y1 = bbMax.y;
		boolean sx, sy;
		while (cont) {
			sx = curr.p1().x == x0 || curr.p1().x == x1;
			sy = curr.p1().y == y0 || curr.p1().y == y1;
			if ((sx && sy) || (!sx && !sy)) {
				ans.add(curr.p1());
			}
			if (curr.bcnext != null && curr.bcnext.halfplane)
				curr = curr.bcnext;
			if (curr == this.startTriangleHull)
				cont = false;
		}
		return ans.iterator();
	}

	private List<Triangle> generateTriangles() {

		List<Triangle> front = new ArrayList<Triangle>();

		List<Triangle> triangles = new ArrayList<Triangle>();
		front.add(this.startTriangle);

		Set<Triangle> triSet = new HashSet<Triangle>();

		while (front.size() > 0) {
			Triangle t = front.remove(0);
			if (t.mark == false) {
				t.mark = true;

				triangles.add(t);
				triSet.add(t);

				checkToInclude(t, t.abnext, front);
				checkToInclude(t, t.bcnext, front);
				checkToInclude(t, t.canext, front);
			}
		}

		return triangles;
	}

	private void checkToInclude(Triangle t, Triangle nextTriangle, List<Triangle> front) {
		if (nextTriangle != null && !nextTriangle.mark && !nextTriangle.isHalfplane()) {
			front.add(nextTriangle);
		}
	}

	/**
	 * Triangulate given points.
	 * Note: duplicated points are ignored.
	 * @param points
	 * @return list of triangles
	 */
	public List<Triangle> triangulate(List<Vector3> points) {
		init(points.size());

		Set<Vector3> vertices = new TreeSet<Vector3>(new PointComparator());

		bbMin = new Vector3(points.get(0));
		bbMax = new Vector3(points.get(0));

		//Insert Points
		for (Vector3 point:points) {
			this.insertPoint(vertices, point);
		}

		List<Triangle> triangles = null;

		if (modCount != modCount2 && vertices.size() > 2) {
			triangles = generateTriangles();
		}

		return triangles;
	}
}