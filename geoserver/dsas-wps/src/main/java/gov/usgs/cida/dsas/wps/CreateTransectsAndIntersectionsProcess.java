package gov.usgs.cida.dsas.wps;

import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import gov.usgs.cida.dsas.exceptions.UnsupportedCoordinateReferenceSystemException;
import gov.usgs.cida.dsas.util.CRSUtils;
import gov.usgs.cida.dsas.util.LayerImportUtil;
import gov.usgs.cida.dsas.util.UTMFinder;
import gov.usgs.cida.dsas.wps.geom.CalculationAreaDescriptor;

import static gov.usgs.cida.dsas.utilities.features.Constants.REQUIRED_CRS_WGS84;

import gov.usgs.cida.dsas.wps.geom.IntersectionCalculator;
import gov.usgs.cida.dsas.wps.geom.Transect;
import java.util.List;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.ProjectionPolicy;
import org.geoserver.wps.gs.GeoServerProcess;
import org.geoserver.wps.gs.ImportProcess;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.process.factory.DescribeParameter;
import org.geotools.process.factory.DescribeProcess;
import org.geotools.process.factory.DescribeResult;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
@DescribeProcess(
		title = "Generate Transects and Intersections",
		description = "Create a transect layer from the baseline and shorelines",
		version = "1.0.0")
public class CreateTransectsAndIntersectionsProcess implements GeoServerProcess {

	private LayerImportUtil importer;

	public CreateTransectsAndIntersectionsProcess(ImportProcess importProcess, Catalog catalog) {
		this.importer = new LayerImportUtil(catalog, importProcess);
	}

	/**
	 * May actually want to return reference to new layer Check whether we need
	 * an offset at the start of the baseline
	 *
	 * With new maxLength algorithm, we may want to allow a maxLength parameter
	 * to speed up calculation
	 *
	 * @param shorelines feature collection of shorelines
	 * @param baseline feature collection of baselines
	 * @param biasRef feature collection of PDBC bias reference line
	 * @param execPlan feature collection with breakdown of execution areas
	 * @param spacing spacing in meters of transects along baseline
	 * @param smoothing how much smoothing to apply to transect generation
	 * @param maxLength maximum length to use when drawing transects (default is to calculate)
	 * @param farthest whether to use nearest or farthest intersection of
	 * shoreline (default false)
	 * @param workspace workspace in which to create new layers
	 * @param store store in which to create new layers
	 * @param transectLayer name of created transect layer
	 * @param intersectionLayer name of created intersection layer
	 * @return layer names of transects and intersections
	 * @throws java.lang.Exception no exceptions caught, may throw anything
	 */
	@DescribeResult(name = "transects", description = "Layer containing Transects normal to baseline")
	public String execute(
			@DescribeParameter(name = "shorelines", min = 1, max = 1) SimpleFeatureCollection shorelines,
			@DescribeParameter(name = "baseline", min = 1, max = 1) SimpleFeatureCollection baseline,
			@DescribeParameter(name = "biasRef", min = 0, max = 1) SimpleFeatureCollection biasRef,
			@DescribeParameter(name = "executionPlan", min = 1, max = 1) SimpleFeatureCollection execPlan,
			@DescribeParameter(name = "spacing", min = 1, max = 1) Double spacing,
			@DescribeParameter(name = "smoothing", min = 0, max = 1) Double smoothing,
			@DescribeParameter(name = "maxLength", min = 0, max =1) Double maxLength,
			@DescribeParameter(name = "farthest", min = 0, max = 1) Boolean farthest,
			@DescribeParameter(name = "workspace", min = 1, max = 1) String workspace,
			@DescribeParameter(name = "store", min = 1, max = 1) String store,
			@DescribeParameter(name = "transectLayer", min = 1, max = 1) String transectLayer,
			@DescribeParameter(name = "intersectionLayer", min = 1, max = 1) String intersectionLayer) throws Exception {
		// defaults
		if (smoothing == null) {
			smoothing = 0d;
		}
		if (maxLength == null) {
			maxLength = Double.NaN;
		}
		if (farthest == null) {
			farthest = false;
		}

		return new Process(shorelines, baseline, biasRef, execPlan, spacing, smoothing, maxLength, farthest, workspace, store, transectLayer, intersectionLayer).execute();
	}

	protected class Process {

		private final SimpleFeatureCollection shorelineFeatureCollection;
		private final SimpleFeatureCollection baselineFeatureCollection;
		private final SimpleFeatureCollection biasRefFeatureCollection;
		private final SimpleFeatureCollection executionPlanCollection;
		private final double spacing;
		private final double smoothing;
		private double maxLength;
		private final boolean useFarthest;
		private final boolean performBiasCorrection;
		private final String workspace;
		private final String store;
		private final String transectLayer;
		private final String intersectionLayer;

		private CoordinateReferenceSystem utmCrs;

		protected Process(SimpleFeatureCollection shorelines,
				SimpleFeatureCollection baseline,
				SimpleFeatureCollection biasRef,
				SimpleFeatureCollection execPlan,
				double spacing,
				double smoothing,
				double maxLength,
				Boolean farthest,
				String workspace,
				String store,
				String transectLayer,
				String intersectionLayer) {
			
			this.shorelineFeatureCollection = shorelines;
			this.baselineFeatureCollection = baseline;
			this.executionPlanCollection = execPlan;

			if (biasRef == null) {
				this.performBiasCorrection = false;
				this.biasRefFeatureCollection = null;
			} else {
				this.performBiasCorrection = true;
				this.biasRefFeatureCollection = biasRef;
			}

			this.spacing = spacing;
			this.smoothing = smoothing;
			this.maxLength = maxLength;
			this.useFarthest = farthest;

			this.workspace = workspace;
			this.store = store;
			this.transectLayer = transectLayer;
			this.intersectionLayer = intersectionLayer;
		}

		protected String execute() throws Exception {
			importer.checkIfLayerExists(workspace, transectLayer);
			importer.checkIfLayerExists(workspace, intersectionLayer);

			CoordinateReferenceSystem shorelinesCrs = CRSUtils.getCRSFromFeatureCollection(shorelineFeatureCollection);
			CoordinateReferenceSystem baselineCrs = CRSUtils.getCRSFromFeatureCollection(baselineFeatureCollection);
			CoordinateReferenceSystem biasCrs = null;
			if (performBiasCorrection) {
				biasCrs = CRSUtils.getCRSFromFeatureCollection(biasRefFeatureCollection);
			}
			
			if (performBiasCorrection && !CRS.equalsIgnoreMetadata(biasCrs, REQUIRED_CRS_WGS84)) {
				throw new UnsupportedCoordinateReferenceSystemException("Bias reference is not in accepted projection");
			}

			if (!CRS.equalsIgnoreMetadata(shorelinesCrs, REQUIRED_CRS_WGS84)) {
				throw new UnsupportedCoordinateReferenceSystemException("Shorelines are not in accepted projection");
			}
			
			if (!CRS.equalsIgnoreMetadata(baselineCrs, REQUIRED_CRS_WGS84)) {
				throw new UnsupportedCoordinateReferenceSystemException("Baseline is not in accepted projection");
			}
			
			CoordinateReferenceSystem utmCrs = UTMFinder.findUTMZoneCRSForCentroid((SimpleFeatureCollection) shorelineFeatureCollection);
			IntersectionCalculator calc = new IntersectionCalculator(shorelineFeatureCollection, baselineFeatureCollection, biasRefFeatureCollection, utmCrs, maxLength, useFarthest, performBiasCorrection);

			Transect[] vectsOnBaseline = calc.getEvenlySpacedOrthoVectorsAlongBaseline(spacing, smoothing);
			SimpleFeatureCollection calculationAreas = calc.splitIntoSections(vectsOnBaseline);
			
			SimpleFeatureIterator features = calculationAreas.features();
			while (features.hasNext()) {
				SimpleFeature feature = features.next();
				calc.calculateIntersections(vectsOnBaseline, feature);
			}
			String createdTransectLayer = importer.importLayer(calc.getResultTransectsCollection(), workspace, store, transectLayer, utmCrs, ProjectionPolicy.REPROJECT_TO_DECLARED);
			String createdIntersectionLayer = importer.importLayer(calc.getResultIntersectionsCollection(), workspace, store, intersectionLayer, utmCrs, ProjectionPolicy.REPROJECT_TO_DECLARED);
			return createdTransectLayer + "," + createdIntersectionLayer;
		}

	}
}
