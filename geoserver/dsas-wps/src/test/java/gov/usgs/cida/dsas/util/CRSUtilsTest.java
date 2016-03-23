package gov.usgs.cida.dsas.util;

import com.vividsolutions.jts.geom.MultiLineString;
import gov.usgs.cida.dsas.wps.CreateTransectsAndIntersectionsProcessTest;
import gov.usgs.cida.owsutils.commons.shapefile.utils.FeatureCollectionFromShp;
import java.io.IOException;
import java.net.URL;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class CRSUtilsTest {

	/**
	 * Test of getLinesFromFeatureCollection method, of class CRSUtils. need to
	 * fill this stuff out
	 */
	@Test
	@Ignore
	public void testGetLinesFromFeatureCollection() throws IOException {
		URL baselineShapefile = CreateTransectsAndIntersectionsProcessTest.class.getClassLoader()
				.getResource("gov/usgs/cida/coastalhazards/jersey/NewJerseyN_baseline.shp");
		FeatureCollection<SimpleFeatureType, SimpleFeature> baselinefc
				= FeatureCollectionFromShp.getFeatureCollectionFromShp(baselineShapefile);
		MultiLineString expResult = null;
		MultiLineString result = CRSUtils.getLinesFromFeatureCollection((SimpleFeatureCollection) baselinefc);
		assertEquals(expResult, result);
	}
	
	@Test
	public void testLookupCRS() throws FactoryException {
		CoordinateReferenceSystem wgs84 = CRSUtils.lookupCRS("EPSG:4326");
		assertThat(wgs84.toWKT(), containsString("WGS 84"));
	}
	
	@Test
	public void testLookupCRScase() throws FactoryException {
		CoordinateReferenceSystem utm17n = CRSUtils.lookupCRS("epsg:32617");
		assertThat(utm17n.toWKT(), containsString("UTM zone 17N"));
	}
	
	@Test
	public void testLookupCRSwithoutEPSG() throws FactoryException {
		CoordinateReferenceSystem utm17n = CRSUtils.lookupCRS("32618");
		assertThat(utm17n.toWKT(), containsString("UTM zone 18N"));
	}

}
