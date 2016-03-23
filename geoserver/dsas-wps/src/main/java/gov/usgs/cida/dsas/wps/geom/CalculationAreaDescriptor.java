package gov.usgs.cida.dsas.wps.geom;

import com.vividsolutions.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class CalculationAreaDescriptor {

	private CoordinateReferenceSystem crs;
	private Transect[] transects;
	private Polygon transectArea;
	
	public CalculationAreaDescriptor() {
		this.crs = null;
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

	SimpleFeature toFeature() {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

}
