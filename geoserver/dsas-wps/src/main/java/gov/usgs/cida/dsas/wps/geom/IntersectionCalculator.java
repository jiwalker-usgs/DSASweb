package gov.usgs.cida.dsas.wps.geom;

import com.google.common.collect.Maps;
import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.index.strtree.STRtree;
import gov.usgs.cida.dsas.exceptions.PoorlyDefinedBaselineException;
import gov.usgs.cida.dsas.util.BaselineDistanceAccumulator;
import gov.usgs.cida.dsas.util.CRSUtils;
import gov.usgs.cida.dsas.util.ShorelineUtils;
import gov.usgs.cida.dsas.utilities.features.AttributeGetter;

import static gov.usgs.cida.dsas.utilities.features.Constants.AVG_SLOPE_ATTR;
import static gov.usgs.cida.dsas.utilities.features.Constants.BASELINE_ORIENTATION_ATTR;
import static gov.usgs.cida.dsas.utilities.features.Constants.BIAS_ATTR;
import static gov.usgs.cida.dsas.utilities.features.Constants.BIAS_UNCY_ATTR;
import static gov.usgs.cida.dsas.utilities.features.Constants.DEFAULT_BIAS;
import static gov.usgs.cida.dsas.utilities.features.Constants.DEFAULT_BIAS_UNCY;

import gov.usgs.cida.dsas.utilities.features.Constants.Orientation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ReprojectingFeatureCollection;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.joda.time.DateTime;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import static gov.usgs.cida.dsas.utilities.features.Constants.REQUIRED_CRS_WGS84;

/**
 *
 * @author Jordan Walker <jiwalker@usgs.gov>
 */
public class IntersectionCalculator {
	
	private static final double MIN_TRANSECT_LENGTH = 50.0; // meters
	private static final double TRANSECT_PADDING = 5.0d; // meters
	private static final int MAX_VERTICES_IN_CALC_AREA = 10_000;
	private static final double CALC_AREA_OFFSET = 0.5d;
	
	private CoordinateReferenceSystem utmCrs;
	
	private List<SimpleFeature> transectList;
	private List<SimpleFeature> intersectionList;
	
	private SimpleFeatureCollection transformedShorelines;
	private SimpleFeatureCollection transformedBaselines;

	private SimpleFeatureType intersectionFeatureType;
	private SimpleFeatureType biasIncomingFeatureType;
	private SimpleFeatureType transectFeatureType;

	private STRtree biasTree;
	
	private GeometryFactory geomFactory;
	
	private double maxTransectLength;
	private int transectId;
	private boolean useFarthest;

	public IntersectionCalculator(SimpleFeatureCollection shorelines,
			SimpleFeatureCollection baseline,
			SimpleFeatureCollection biasRef, CoordinateReferenceSystem utmCrs, 
			double maxTransectLength, boolean useFarthest, boolean performBiasCorrection) {

		this.utmCrs = utmCrs;
		this.intersectionFeatureType = Intersection.buildSimpleFeatureType(shorelines, utmCrs);
		this.transectFeatureType = Transect.buildFeatureType(utmCrs);
		this.geomFactory = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING));

		this.transformedShorelines = new ReprojectingFeatureCollection(shorelines, utmCrs);
		this.transformedBaselines = new ReprojectingFeatureCollection(baseline, utmCrs);
		if (performBiasCorrection) {
			SimpleFeatureCollection transformedBiasRef = CRSUtils.transformFeatureCollection(biasRef, REQUIRED_CRS_WGS84, utmCrs);
			this.biasIncomingFeatureType = biasRef.getSchema();
			Polygon biasBounds = ShorelineUtils.bboxToPolygon(transformedBiasRef.getBounds());
			this.biasTree = new ShorelineSTRTreeBuilder(transformedBiasRef, biasBounds).build();
		} else {
			this.biasTree = null;
		}
		
		this.transectList = new ArrayList<>();
		this.intersectionList = new ArrayList<>();
		
		if (Double.isNaN(maxTransectLength) || maxTransectLength == 0.0) {
			maxTransectLength = IntersectionCalculator.calculateMaxDistance(transformedShorelines, transformedBaselines);
		}
		
		this.maxTransectLength = maxTransectLength;
		this.transectId = 0;
		this.useFarthest = useFarthest;
	}

	public Transect[] getEvenlySpacedOrthoVectorsAlongBaseline(double spacing, double smoothing) {
		List<Transect> vectList = new LinkedList<>();

		BaselineDistanceAccumulator accumulator = new BaselineDistanceAccumulator();
		AttributeGetter attGet = new AttributeGetter(transformedBaselines.getSchema());

		try (SimpleFeatureIterator features = transformedBaselines.features()){
			while (features.hasNext()) {
				SimpleFeature feature = features.next();
				String orientVal = (String) attGet.getValue(BASELINE_ORIENTATION_ATTR, feature);
				Orientation orientation = Orientation.fromAttr(orientVal);
				if (orientation == Orientation.UNKNOWN) {
					// default to seaward
					orientation = Orientation.SEAWARD;
				}
				String baselineId = feature.getID();

				MultiLineString lines = CRSUtils.getMultilineFromFeature(feature);
				for (int i = 0; i < lines.getNumGeometries(); i++) { // probably only one Linestring
					LineString line = (LineString) lines.getGeometryN(i);
					int direction = shorelineDirection(line);

					double baseDist = accumulator.accumulate(line);

					vectList.addAll(handleLineString(line, spacing, orientation, direction, baselineId, baseDist, smoothing)); // rather than SEAWARD, get from baseline feature
				}
			}
		}
		return vectList.toArray(new Transect[vectList.size()]);
	}
	
	/**
	 * Perform the calculation of the intersections and add values to result
	 * @param vectsOnBaseline all possible transect vectors
	 * @param executionPlan polygon to subset into
	 */
	public void calculateIntersections(Transect[] vectsOnBaseline, SimpleFeature executionPlan) {
		if (vectsOnBaseline.length == 0) {
			return;
		}
		
		AttributeGetter attGet = new AttributeGetter(intersectionFeatureType);
		AttributeGetter biasGetter = new AttributeGetter(biasIncomingFeatureType);
		// grow by about 200?
		double guessTransectLength = MIN_TRANSECT_LENGTH * 4;
		
		Geometry planBounds = (Geometry)executionPlan.getDefaultGeometry();
		// STRtree occupies a substantial portion of memory, this should be kept as small as reasonable
		STRtree strTree = new ShorelineSTRTreeBuilder(transformedShorelines, planBounds).build();
		for (Transect transect : vectsOnBaseline) {
			if (planBounds.covers(transect.getOriginPoint())) {
				Map<DateTime, Intersection> allIntersections = Maps.newHashMap();
				double startDistance = 0;
				ProxyDatumBias biasCorrection = null;
				boolean changeTransectLength = true;
				double subTransectLength = guessTransectLength;
				if (transect.getLength() > 0.0) {
					changeTransectLength = false;
				}

				do {
					Transect subTransect = null;
					if (changeTransectLength) {

						if (startDistance + guessTransectLength >= maxTransectLength) {
							subTransectLength = maxTransectLength - startDistance;
						}
						subTransect = transect.subTransect(startDistance, subTransectLength);
						LineString lineString = subTransect.getLineString();
						LineString intersection = (LineString)lineString.intersection(planBounds);
						subTransect.setLength(intersection.getLength());
						startDistance += subTransectLength;
					} else {
						subTransect = transect;
						startDistance = maxTransectLength; // end the loop
					}
					Intersection.updateIntersectionsWithSubTransect(allIntersections, transect.getOriginPoint(), subTransect, strTree, useFarthest, attGet);
					if (biasCorrection == null && biasTree != null) {
						biasCorrection = getBiasValue(subTransect, biasTree, biasGetter);
					}
				} while (startDistance < maxTransectLength);

				if (!allIntersections.isEmpty()) {  // ignore non-crossing lines
					if (changeTransectLength) {
						double transectLength = Intersection.absoluteFarthest(MIN_TRANSECT_LENGTH, allIntersections.values());
						transect.setLength(transectLength + TRANSECT_PADDING);
					}
					transect.setBias(biasCorrection);
					SimpleFeature feature = transect.createFeature(transectFeatureType);
					transectList.add(feature);

					for (Intersection intersection : allIntersections.values()) {
						// do I need to worry about order?
						intersection.setBias(biasCorrection);
						intersectionList.add(intersection.createFeature(intersectionFeatureType));
					}
				}
			}
		}
	}

	/**
	 * Vectors point 90&deg; counterclockwise currently
	 * @param lineString line along which to get vectors
	 * @param spacing how often to create vectors along line
	 * @param orientation
	 * @param orthoDirection
	 * @param baselineId
	 * @param accumulatedBaselineLength
	 * @param smoothing
	 * @return List of fancy vectors I concocted
	 */
	protected List<Transect> handleLineString(LineString lineString, double spacing, Orientation orientation, int orthoDirection, String baselineId, double accumulatedBaselineLength, double smoothing) {
		List<LineSegment> intervals = findIntervals(lineString, true, spacing, smoothing);
		List<Transect> transects = new ArrayList<>(intervals.size());
		for (LineSegment interval : intervals) {
			transects.add(Transect.generatePerpendicularVector(interval.p0, interval, orientation, transectId++, baselineId, accumulatedBaselineLength, orthoDirection));
			accumulatedBaselineLength += spacing;
		}
		return transects;
	}

	private double nearestPoint(Transect transect) {
		double nearest = Double.MAX_VALUE;
		transect.setLength(maxTransectLength);
		Envelope envelopeInternal = transect.getLineString().getEnvelopeInternal();
		BoundingBox bbox = new ReferencedEnvelope(envelopeInternal, utmCrs);
		double nearestVertexInBbox = ShorelineUtils.nearestVertexInBbox(transformedShorelines, transect.getOriginPoint(), bbox);
		return nearestVertexInBbox;
	}

	/**
	 * Gives direction to point transects as long as baseline is good
	 * This is pretty much just hacked up at this point
	 * Any better way of doing this would be smart
	 *
	 * Use Shoreward orientation so distances are positive (otherwise we should use absolute distance)
	 * @param baseline
	 * @return
	 */
	protected int shorelineDirection(LineString baseline) {
		Coordinate[] coordinates = baseline.getCoordinates();
		int n = coordinates.length;
		LineSegment a = new LineSegment(coordinates[0], coordinates[1]);
		LineSegment b = new LineSegment(coordinates[n - 1], coordinates[n - 2]);
		LineSegment m = null;
		if (n > 2) {
			m = new LineSegment(coordinates[(int) n / 2], coordinates[(int) n / 2 + 1]);
		}
		double[] nearestPoints = new double[]{0.0, 0.0};
		Transect vector = Transect.generatePerpendicularVector(coordinates[0], a, Orientation.SHOREWARD, 0, "0", Double.NaN, Angle.CLOCKWISE);
		nearestPoints[0] = nearestPoint(vector);
		vector.rotate180Deg();
		nearestPoints[1] = nearestPoint(vector);
		vector = Transect.generatePerpendicularVector(coordinates[n - 1], b, Orientation.SHOREWARD, 0, "0", Double.NaN, Angle.COUNTERCLOCKWISE);
		double currentNearest = nearestPoint(vector);
		nearestPoints[0] = (nearestPoints[0] < currentNearest) ? nearestPoints[0] : currentNearest;
		vector.rotate180Deg();
		currentNearest = nearestPoint(vector);
		nearestPoints[1] = (nearestPoints[1] < currentNearest) ? nearestPoints[1] : currentNearest;
		if (m != null) {
			vector = Transect.generatePerpendicularVector(coordinates[(int) n / 2], m, Orientation.SHOREWARD, 0, "0", Double.NaN, Angle.CLOCKWISE);
			currentNearest = nearestPoint(vector);
			nearestPoints[0] = (nearestPoints[0] < currentNearest) ? nearestPoints[0] : currentNearest;
			vector.rotate180Deg();
			currentNearest = nearestPoint(vector);
			nearestPoints[1] = (nearestPoints[1] < currentNearest) ? nearestPoints[1] : currentNearest;
		}
		if (nearestPoints[0] < nearestPoints[1]) {
			return Angle.CLOCKWISE;
		} else if (nearestPoints[0] > nearestPoints[1]) {
			return Angle.COUNTERCLOCKWISE;
		}
		throw new PoorlyDefinedBaselineException("Baseline is ambiguous, transect direction cannot be determined");
	}

	public SimpleFeatureCollection getResultTransectsCollection() {
		return DataUtilities.collection(transectList);
	}

	public SimpleFeatureCollection getResultIntersectionsCollection() {
		return DataUtilities.collection(intersectionList);
	}
	
	// NOTE: For each segment p0 is interval coord, with p1 = p0 + direction of segment as unit vector.
	public static List<LineSegment> findIntervals(LineString lineString, boolean includeOrigin, double interval) {
		LinkedList<LineSegment> intervalList = new LinkedList<>();
		List<LineSegment> segmentList = toLineSegments(lineString);
		double progress = 0;
		double next = includeOrigin ? 0 : interval;
		for (LineSegment segment : segmentList) {
			double segmentLength = segment.getLength();
			if (progress + segmentLength >= next) {
				double segmentAngle = segment.angle();
				double segmentOffset;
				for (segmentOffset = next - progress; segmentOffset <= segmentLength; segmentOffset += interval, next += interval) {
					Coordinate c = segment.pointAlong(segmentOffset / segmentLength);
					intervalList.add(new LineSegment(
							c.x, c.y, c.x + Math.cos(segmentAngle), c.y + Math.sin(segmentAngle)));
				}
			}
			progress += segmentLength;
		}
		return intervalList;
	}

	// NOTE: For each segment p0 is interval coord, with p1 = p0 + direction of segment as unit vector.
	public static List<LineSegment> findIntervals(LineString lineString, boolean includeOrigin, double interval, double smoothing) {
		if (smoothing <= 0 || lineString.getNumPoints() == 2) {
			return findIntervals(lineString, includeOrigin, interval);
		}
		LinkedList<LineSegment> intervalList = new LinkedList<>();
		List<LineSegment> segmentList = toLineSegments(lineString);
		double progress = 0;
		double next = includeOrigin ? 0 : interval;
		final double half = smoothing / 2d;
		int index = 0;
		final int count = segmentList.size();
		for (LineSegment segment : segmentList) {
			final double length = segment.getLength();
			if (progress + length >= next) {
				double offset;
				for (offset = next - progress; offset <= length; offset += interval, next += interval) {
					Coordinate intervalCenter = segment.pointAlong(offset / length);
					double low = offset - half; // offset from p0 of current segment for low bound of smoothed segment (may be < 0)
					double high = offset + half; // offset from p0 of current segment for high bound of smoothed segment (may be > length)
					double angle;
					if (low < 0 || high > length) {
						Coordinate intervalLow = low < 0 && index > 0
								? // find distance along line string from end of *last* segment (removing distance along current segment);
								fromEnd(segmentList.subList(0, index), 0 - low, false)
								: // otherwise project along segment
								segment.pointAlong(low / length);
						Coordinate intervalHigh = high > length && index < (count - 1)
								? // find distance along line string from end of *last* segment (removing distance along current segment);
								fromStart(segmentList.subList(index + 1, count), high - length, false)
								: // otherwise project along segment
								segment.pointAlong(high / length);
						LineSegment smoothSegment = new LineSegment(intervalLow, intervalHigh);
						angle = smoothSegment.angle();
					} else {
						angle = segment.angle();
					}
					intervalList.add(new LineSegment(
							intervalCenter.x, intervalCenter.y, intervalCenter.x + Math.cos(angle), intervalCenter.y + Math.sin(angle)));
				}
			}
			index++;
			progress += length;
		}
		return intervalList;
	}

	// Extracts consituent segments from line string
	public static List<LineSegment> toLineSegments(LineString line) {
		int cCount = line.getNumPoints();
		List<LineSegment> segments = new ArrayList<>(cCount - 1);
		for (int cIndex = 1; cIndex < cCount; ++cIndex) {
			segments.add(new LineSegment(line.getCoordinateN(cIndex - 1), line.getCoordinateN(cIndex)));
		}
		return segments;
	}

	static Coordinate fromStart(LineString lineString, double distance, boolean clamp) {
		return fromStart(toLineSegments(lineString), distance, clamp);
	}

	static Coordinate fromStart(List<LineSegment> lineSegments, double distance, boolean clamp) {
		ListIterator<LineSegment> iterator = lineSegments.listIterator();
		double progress = 0;
		while (iterator.hasNext()) {
			final LineSegment segement = iterator.next();
			final double length = segement.getLength();
			final double offset = distance - progress;
			if (offset <= length) {
				return segement.pointAlong(offset / length);
			}
			progress += length;
		}
		// special case handling, distance is longer than line
		LineSegment last = lineSegments.get(lineSegments.size() - 1);
		// clamp or project?
		if (clamp) {
			// end of last segment in list
			return last.getCoordinate(1);
		} else {
			double overflow = distance - progress;
			// project along last line segment, add 1 since this method
			// uses fraction-of-segment-length as distance from p0 and our
			// overflow is distance from p1
			return last.pointAlong(1d + (overflow / last.getLength()));
		}
	}

	static Coordinate fromEnd(LineString lineString, double distance, boolean clamp) {
		return fromEnd(toLineSegments(lineString), distance, clamp);
	}

	static Coordinate fromEnd(List<LineSegment> lineSegments, double distance, boolean clamp) {
		ListIterator<LineSegment> iterator = lineSegments.listIterator(lineSegments.size());
		double progress = 0;
		while (iterator.hasPrevious()) {
			final LineSegment segement = iterator.previous();
			final double length = segement.getLength();
			final double offset = distance - progress;
			if (offset <= length) {
				// since method is fraction from p0, but offset is distance from p1.
				return segement.pointAlong(1d - (offset / length));
			}
			progress += length;
		}
		// special case handling, distance is longer than line
		LineSegment first = lineSegments.get(0);
		// clamp or project?
		if (clamp) {
			// start of first segment in list
			return first.getCoordinate(0);
		} else {
			double overflow = distance - progress;
			// project along first line segment, negate since this method
			// uses fraction-of-segment-length as distance from p0 towards p1 and our
			// overflow is distance from p0 (in p1 to p0 direction)
			return first.pointAlong(-(overflow / first.getLength()));
		}
	}

	public static double calculateMaxDistance(SimpleFeatureCollection transformedShorelines, SimpleFeatureCollection transformedBaselines) {
		ReferencedEnvelope shorelineBounds = transformedShorelines.getBounds();
		ReferencedEnvelope baselineBounds = transformedBaselines.getBounds();

		double[] slUpperCorner1 = shorelineBounds.getUpperCorner().getCoordinate();
		double[] slLowerCorner1 = shorelineBounds.getLowerCorner().getCoordinate();
		double[] slUpperCorner2 = new double[]{slLowerCorner1[0], slUpperCorner1[1]};
		double[] slLowerCorner2 = new double[]{slUpperCorner1[0], slLowerCorner1[1]};

		double[] blUpperCorner1 = baselineBounds.getUpperCorner().getCoordinate();
		double[] blLowerCorner1 = baselineBounds.getLowerCorner().getCoordinate();
		double[] blUpperCorner2 = new double[]{blLowerCorner1[0], blUpperCorner1[1]};
		double[] blLowerCorner2 = new double[]{blUpperCorner1[0], blLowerCorner1[1]};

		double[][] pointsCompare = {slUpperCorner1, slUpperCorner2, slLowerCorner1,
			slLowerCorner2, blUpperCorner1, blUpperCorner2, blLowerCorner1, blLowerCorner2};

		double maxDist = 0.0d;
		for (int i = 0; i < 4; i++) {
			Coordinate a = new Coordinate(pointsCompare[i][0], pointsCompare[i][1]);
			for (int j = 0; j < 4; j++) {
				Coordinate b = new Coordinate(pointsCompare[4 + j][0], pointsCompare[4 + j][1]);
				double dist = a.distance(b);
				if (dist > maxDist) {
					maxDist = dist;
				}
			}
		}

		return maxDist;
	}

	public static ProxyDatumBias getBiasValue(Transect transect, STRtree biasTree, AttributeGetter biasGetter) {
		ProxyDatumBias proxy = null;
		LineString line = transect.getLineString();
		List<ShorelineFeature> possibleIntersects = biasTree.query(line.getEnvelopeInternal());
		for (ShorelineFeature feature : possibleIntersects) {
			LineString segment = feature.segment;
			if (segment.intersects(line)) {
				Point intersection = (Point) segment.intersection(line);
				double slopeVal = feature.interpolate(intersection, AVG_SLOPE_ATTR, biasGetter, Double.NaN);
				double biasVal = feature.interpolate(intersection, BIAS_ATTR, biasGetter, DEFAULT_BIAS);
				double uncybVal = feature.interpolate(intersection, BIAS_UNCY_ATTR, biasGetter, DEFAULT_BIAS_UNCY);
				proxy = new ProxyDatumBias(slopeVal, biasVal, uncybVal);
			}
		}
		return proxy;
	}

	public SimpleFeatureCollection splitIntoSections(Transect[] vectsOnBaseline) {
		List<SimpleFeature> executionPlan = new ArrayList<>();
		
		int numberTransectsPerArea = vectsOnBaseline.length;
		int verticesWithinArea = getVerticesInArea(vectsOnBaseline);
		while (verticesWithinArea > MAX_VERTICES_IN_CALC_AREA) {
			numberTransectsPerArea = (int)Math.floor(numberTransectsPerArea / 2);
			// copyOfRange upperbound is exclusive
			Transect[] subset = Arrays.copyOfRange(vectsOnBaseline, 0, numberTransectsPerArea + 1);
			verticesWithinArea = getVerticesInArea(subset);
		}
		
		int lowerBound = 0;
		while (lowerBound < vectsOnBaseline.length) {
			int upperBound = lowerBound + numberTransectsPerArea;
			if (upperBound >= vectsOnBaseline.length) {
				upperBound = vectsOnBaseline.length - 1;
			}
			CalculationAreaDescriptor calculationAreaDescriptor = new CalculationAreaDescriptor();
			// TODO Change this if crs can change across calculationAreas
			calculationAreaDescriptor.setCrs(utmCrs);
			// copyOfRange upperbound is exclusive
			Transect[] subset = Arrays.copyOfRange(vectsOnBaseline, lowerBound, upperBound + 1);
			calculationAreaDescriptor.setTransects(subset);
			calculationAreaDescriptor.setTransectArea(getTransectArea(subset));
			executionPlan.add(calculationAreaDescriptor.toFeature());
			
			lowerBound = upperBound + 1;
		}
		return DataUtilities.collection(executionPlan);
	}
	
	private int getVerticesInArea(Transect[] transects) {
		Polygon transectArea = getTransectArea(transects);
		return ShorelineUtils.countVertices(transformedShorelines, transectArea);
	}
	
	private Polygon getTransectArea(Transect[] transects) {
		Coordinate[] coords = new Coordinate[transects.length * 2 + 1];
		
		for (int i=0; i<coords.length; i++) {
			if (i < transects.length) {
				coords[i] = transects[i].getOriginCoord();
			} else if (i < (2 * transects.length)) {
				coords[i] = transects[2 * transects.length - (i + 1)].getLineString().getCoordinateN(1);
			} else {
				coords[i] = transects[0].getOriginCoord();
			}
		}
		Polygon transectArea = geomFactory.createPolygon(coords);
		return transectArea;
	}

}
