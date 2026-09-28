package com.fitmate.global.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoPoints {

    public static final int WGS84_SRID = 4326;

    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), WGS84_SRID);

    private GeoPoints() {
    }

    /** PostGIS 좌표 순서는 (경도, 위도)이므로 헷갈리지 않도록 이 메서드로만 생성한다. */
    public static Point of(double latitude, double longitude) {
        return FACTORY.createPoint(new Coordinate(longitude, latitude));
    }
}
