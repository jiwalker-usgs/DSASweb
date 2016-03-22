package gov.usgs.cida.dsas.util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class VertexCountingVisitor implements FeatureVisitor {

	private int vertexCount;
	private Polygon transectArea;

	public VertexCountingVisitor(Polygon transectArea) {
		this.vertexCount = 0;
		this.transectArea = transectArea;
	}

	@Override
	public void visit(Feature ftr) {
		Geometry geom = (Geometry) ftr.getDefaultGeometryProperty().getValue();
		if (transectArea.contains(geom)) {
			int numPoints = geom.getNumPoints();
			vertexCount += numPoints;
		}
	}
	
	public int getVertexCount() {
		return vertexCount;
	}

}
