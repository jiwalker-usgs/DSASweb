package gov.usgs.cida.dsas.util;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import java.io.IOException;
import org.geotools.feature.FeatureCollection;
import org.opengis.geometry.BoundingBox;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class ShorelineUtils {
	
	private static final double WITHIN_BUFFER = 100.0d;
	private static GeometryFactory factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));

	public static int countVertices(FeatureCollection shorelines, Polygon transectArea) {
		VertexCountingVisitor vertexCountingVisitor = new VertexCountingVisitor(transectArea);
		try {
			shorelines.accepts(vertexCountingVisitor, null);
		} catch (IOException ex) {
			throw new RuntimeException("Could not count vertices", ex);
		}
		int numberOfVertices = vertexCountingVisitor.getVertexCount();
		return numberOfVertices;
	}
	
	public static double nearestVertexInBbox(FeatureCollection shorelines, Point origin, BoundingBox bbox) {
		NearestPointVisitor nearestPointVisitor = new NearestPointVisitor(origin, bboxToPolygon(bbox));
		try {
			shorelines.accepts(nearestPointVisitor, null);
		} catch (IOException ex) {
			throw new RuntimeException("Could not count vertices", ex);
		}
		double nearestPoint = nearestPointVisitor.getNearestDistance();
		return nearestPoint;
	}
	
	public static Polygon bboxToPolygon(BoundingBox bbox) {
		Polygon poly = factory.createPolygon(new Coordinate[] {
			new Coordinate(bbox.getMinX(), bbox.getMinY()),
			new Coordinate(bbox.getMinX(), bbox.getMaxY()),
			new Coordinate(bbox.getMaxX(), bbox.getMaxY()),
			new Coordinate(bbox.getMaxX(), bbox.getMinY()),
			new Coordinate(bbox.getMinX(), bbox.getMinY())
		});
		return poly;
	}

}
