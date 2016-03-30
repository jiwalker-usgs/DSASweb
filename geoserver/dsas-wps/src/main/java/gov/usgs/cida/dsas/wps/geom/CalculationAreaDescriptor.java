package gov.usgs.cida.dsas.wps.geom;

import com.vividsolutions.jts.geom.Polygon;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class CalculationAreaDescriptor {
	
	private static final Logger log = LoggerFactory.getLogger(CalculationAreaDescriptor.class);

	private static final String CALC_CRS = "calc_crs";
	private static final String SHORELINE_DIRECTION = "shoredir";
	
	private final SimpleFeatureType type;
	
	private CoordinateReferenceSystem crs;
	private Transect[] transects;
	private Polygon transectArea;
	
	public CalculationAreaDescriptor() {
		this.crs = null;
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("ExecutionPlan");
		builder.add("geom", Polygon.class, crs);
		builder.add(CALC_CRS, String.class);
		builder.add(SHORELINE_DIRECTION, Integer.class);
		this.type = builder.buildFeatureType();
	}

	public CoordinateReferenceSystem getCrs() {
		return crs;
	}

	public void setCrs(CoordinateReferenceSystem crs) {
		this.crs = crs;
	}

	public Transect[] getTransects() {
		return transects;
	}

	public void setTransects(Transect[] transects) {
		this.transects = transects;
	}

	public Polygon getTransectArea() {
		return transectArea;
	}

	public void setTransectArea(Polygon transectArea) {
		this.transectArea = transectArea;
	}

	public SimpleFeature toFeature() {
		String epsgCode;
		try {
			epsgCode = CRS.lookupIdentifier(crs, true);
		} catch (FactoryException ex) {
			throw new RuntimeException("Could not get EPSG code", ex);
		}
		return SimpleFeatureBuilder.build(type, new Object[]{transectArea, epsgCode}, null);
	}

}
