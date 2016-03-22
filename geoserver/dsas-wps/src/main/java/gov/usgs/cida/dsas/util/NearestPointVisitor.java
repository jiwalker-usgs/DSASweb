package gov.usgs.cida.dsas.util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class NearestPointVisitor implements FeatureVisitor {

	private double nearestDistance;
	private Point origin;
	private Polygon bbox;

	public NearestPointVisitor(Point origin, Polygon bbox) {
		this.nearestDistance = Double.MAX_VALUE;
		this.origin = origin;
		this.bbox = bbox;
	}

	@Override
	public void visit(Feature ftr) {
		Geometry geom = (Geometry) ftr.getDefaultGeometryProperty().getValue();
		if (bbox.contains(geom)) {
			double distance = origin.distance(geom);
			if (distance < nearestDistance) {
				nearestDistance = distance;
			}
		}
	}
	
	public double getNearestDistance() {
		return nearestDistance;
	}

}
