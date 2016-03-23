package gov.usgs.cida.dsas.wps;

import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import gov.usgs.cida.dsas.exceptions.UnsupportedCoordinateReferenceSystemException;
import gov.usgs.cida.dsas.util.CRSUtils;
import gov.usgs.cida.dsas.util.LayerImportUtil;
import gov.usgs.cida.dsas.wps.geom.CalculationAreaDescriptor;
import gov.usgs.cida.dsas.wps.geom.IntersectionCalculator;
import gov.usgs.cida.dsas.wps.geom.Transect;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.ProjectionPolicy;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.gs.ImportProcess;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import static gov.usgs.cida.dsas.utilities.features.Constants.REQUIRED_CRS_WGS84;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
@DescribeProcess(
		title = "Create execution plan",
		description = "Create the execution plan that splits up processing and verifies completeness",
		version = "1.0.0")
public class CreateExecutionPlanProcess implements GeoServerProcess {

	private LayerImportUtil importer;

	public CreateExecutionPlanProcess(ImportProcess importProcess, Catalog catalog) {
		this.importer = new LayerImportUtil(catalog, importProcess);
	}
	
	@DescribeResult(name = "executionPlan", description = "Layer containing polygons describing execution plan")
	public String execute(@DescribeParameter(name = "shorelines", min = 1, max = 1) SimpleFeatureCollection shorelines,
			@DescribeParameter(name = "baseline", min = 1, max = 1) SimpleFeatureCollection baseline,
			@DescribeParameter(name = "calculationProjection", min = 1, max = 1) String epsgCode,
			@DescribeParameter(name = "spacing", min = 1, max = 1) Double spacing,
			@DescribeParameter(name = "smoothing", min = 0, max = 1) Double smoothing,
			@DescribeParameter(name = "maxLength", min = 1, max =1) Double maxLength,
			@DescribeParameter(name = "workspace", min = 1, max = 1) String workspace,
			@DescribeParameter(name = "store", min = 1, max = 1) String store,
			@DescribeParameter(name = "planLayer", min = 1, max = 1) String planLayer) throws Exception {
		// defaults
		if (smoothing == null) {
			smoothing = 0d;
		}

		return new Process(shorelines, baseline, epsgCode, spacing, smoothing, maxLength, workspace, store, planLayer).execute();
	}
	
	protected class Process {
		
		private final SimpleFeatureCollection shorelineFeatureCollection;
		private final SimpleFeatureCollection baselineFeatureCollection;
		private final double spacing;
		private final double smoothing;
		private final double maxLength;
		private final String workspace;
		private final String store;
		private final String planLayer;

		private final CoordinateReferenceSystem crs;
		
		public Process(SimpleFeatureCollection shorelines, SimpleFeatureCollection baseline,
				String epsgCode, Double spacing, Double smoothing, Double maxLength,
				String workspace, String store, String planLayer) {
			this.shorelineFeatureCollection = shorelines;
			this.baselineFeatureCollection = baseline;
			this.spacing = spacing;
			this.smoothing = smoothing;
			this.maxLength = maxLength;
			this.workspace = workspace;
			this.store = store;
			this.planLayer = planLayer;
			
			try {
				this.crs = CRSUtils.lookupCRS(epsgCode);
			} catch (FactoryException ex) {
				throw new RuntimeException("Invalid EPSG code", ex);
			}
		}
		
		public String execute() throws Exception {
			importer.checkIfLayerExists(workspace, planLayer);
			CoordinateReferenceSystem shorelinesCrs = CRSUtils.getCRSFromFeatureCollection(shorelineFeatureCollection);
			CoordinateReferenceSystem baselineCrs = CRSUtils.getCRSFromFeatureCollection(baselineFeatureCollection);
			
			if (!CRS.equalsIgnoreMetadata(shorelinesCrs, REQUIRED_CRS_WGS84)) {
				throw new UnsupportedCoordinateReferenceSystemException("Shorelines are not in accepted projection");
			}
			
			if (!CRS.equalsIgnoreMetadata(baselineCrs, REQUIRED_CRS_WGS84)) {
				throw new UnsupportedCoordinateReferenceSystemException("Baseline is not in accepted projection");
			}
			
			IntersectionCalculator calc = new IntersectionCalculator(shorelineFeatureCollection, baselineFeatureCollection, biasRefFeatureCollection, utmCrs, maxLength, useFarthest, performBiasCorrection);

			Transect[] vectsOnBaseline = calc.getEvenlySpacedOrthoVectorsAlongBaseline(baselineFeatureCollection, spacing, smoothing);
			SimpleFeatureCollection executionPlan = calc.splitIntoSections(vectsOnBaseline);
			String createdPlanLayer = importer.importLayer(executionPlan, workspace, store, planLayer, crs, ProjectionPolicy.REPROJECT_TO_DECLARED);
			return createdPlanLayer;
		}
	}
}
