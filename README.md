# triangulation
A jdt triangulation fork

```java
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
```

## Maven
```
<dependency>
    <groupId>com.harium.etyl.geometry</groupId>
    <artifactId>triangulation</artifactId>
    <version>1.0.0</version>
</dependency>
```