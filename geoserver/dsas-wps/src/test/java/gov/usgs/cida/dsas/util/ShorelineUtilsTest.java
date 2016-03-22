package gov.usgs.cida.dsas.util;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import gov.usgs.cida.owsutils.commons.shapefile.utils.FeatureCollectionFromShp;
import java.io.IOException;
import java.net.URL;
import org.geotools.feature.FeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Test;
import org.opengis.geometry.BoundingBox;

import static org.junit.Assert.*;

/**
 *
 * @author jiwalker
 */
public class ShorelineUtilsTest {
	
	@Test
	public void testEstimateDensity() throws IOException {
		URL shp = ShorelineUtilsTest.class.getClassLoader().getResource("gov/usgs/cida/coastalhazards/jersey/NewJerseyN_shorelines.shp");
		FeatureCollection shorelines = FeatureCollectionFromShp.getFeatureCollectionFromShp(shp);
		ReferencedEnvelope jerseyBbox = shorelines.getBounds();
		GeometryFactory factory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));
		Polygon poly = factory.createPolygon(new Coordinate[] {
			new Coordinate(jerseyBbox.getMinX(), jerseyBbox.getMinY()),
			new Coordinate(jerseyBbox.getMinX(), jerseyBbox.getMaxY()),
			new Coordinate(jerseyBbox.getMaxX(), jerseyBbox.getMaxY()),
			new Coordinate(jerseyBbox.getMaxX(), jerseyBbox.getMinY()),
			new Coordinate(jerseyBbox.getMinX(), jerseyBbox.getMinY())
		});
		int expResult = 65511;
		int result = ShorelineUtils.countVertices(shorelines, poly);
		assertEquals(expResult, result);
	}
	
}
