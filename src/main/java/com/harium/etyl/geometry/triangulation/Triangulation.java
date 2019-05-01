package com.harium.etyl.geometry.triangulation;

import com.badlogic.gdx.math.Vector3;

import java.util.List;

public interface Triangulation {

    List<Triangle> triangulate(List<Vector3> pointCloud);

    Triangle find(Vector3 vertex);

}
