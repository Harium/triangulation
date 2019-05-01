package examples;

import com.badlogic.gdx.math.Vector3;
import com.harium.etyl.geometry.triangulation.DelaunayTriangulation;
import com.harium.etyl.geometry.triangulation.Triangle;
import com.harium.etyl.geometry.triangulation.Triangulation;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        Triangulation triangulation = new DelaunayTriangulation();

        Vector3 pointA = new Vector3(0, 1, 0);
        Vector3 pointB = new Vector3(2, 0, 0);
        Vector3 pointC = new Vector3(2, 2, 0);
        Vector3 pointD = new Vector3(4, 1, 0);

        List<Vector3> pointCloud = new ArrayList<Vector3>();
        pointCloud.add(pointA);
        pointCloud.add(pointB);
        pointCloud.add(pointC);
        pointCloud.add(pointD);

        List<Triangle> triangles = triangulation.triangulate(pointCloud);
    }

}
