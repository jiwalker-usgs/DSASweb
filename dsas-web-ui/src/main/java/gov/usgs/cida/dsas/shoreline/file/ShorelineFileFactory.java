package gov.usgs.cida.dsas.shoreline.file;

import gov.usgs.cida.dsas.service.util.ImportUtil;
import gov.usgs.cida.dsas.utilities.properties.Property;
import gov.usgs.cida.dsas.utilities.properties.PropertyUtil;
import gov.usgs.cida.dsas.dao.shoreline.ShorelineLidarFileDAO;
import gov.usgs.cida.dsas.dao.shoreline.ShorelineShapefileDAO;
import gov.usgs.cida.dsas.featureTypeFile.exception.LidarFileFormatException;
import gov.usgs.cida.dsas.featureTypeFile.exception.ShorelineFileFormatException;
import gov.usgs.cida.dsas.shoreline.file.ShorelineFile.ShorelineType;
import gov.usgs.cida.owsutils.commons.communication.RequestResponse;
import gov.usgs.cida.dsas.dao.geoserver.GeoserverDAO;
import gov.usgs.cida.owsutils.commons.io.FileHelper;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

/**
 *
 * @author isuftin
 */
public class ShorelineFileFactory {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ShorelineFileFactory.class);
	//private static final String WORKSPACE_PARAM = "workspace";
	private static final String FILENAME_PARAM = "filename.param";
	private File zipFile;
	private HttpServletRequest request;
	private File baseDirectory;
	private File uploadDirectory;
	private File workDirectory;

	public ShorelineFileFactory(File zipFile, String workspace) throws IOException {
		if (zipFile == null) {
			throw new NullPointerException("Zip file may not be null");
		}

		if (!zipFile.exists()) {
			throw new FileNotFoundException("Zip file can not be found");
		}

		if (!FileHelper.isZipFile(zipFile)) {
			throw new IOException("File is not a zip file");
		}

		this.zipFile = zipFile;
		init();
	}

	public ShorelineFileFactory(HttpServletRequest request) {
		if (request == null) {
			throw new NullPointerException("Request object may not be null");
		}

		this.request = request;

		init();
	}

	private void init() {
		this.baseDirectory = new File(PropertyUtil.getProperty(Property.DIRECTORIES_BASE, FileUtils.getTempDirectory().getAbsolutePath()));
		this.uploadDirectory = new File(baseDirectory, PropertyUtil.getProperty(Property.DIRECTORIES_UPLOAD));
		this.workDirectory = new File(baseDirectory, PropertyUtil.getProperty(Property.DIRECTORIES_WORK));
	//	this.workspace = workspace;
	}

	//takes the zip file cleaned name and replaces the names of the files in it with the cleaned name plus the files extension. Returns the path to the exploded renamed contents.
	private File renameZipFileContents(File zipFile) throws IOException {
		File workLocation = createWorkLocationForZip(zipFile);
		FileHelper.unzipFile(workLocation.getAbsolutePath(), zipFile);
		FileHelper.renameDirectoryContents(workLocation);
		return workLocation;
	}

	private File createWorkLocationForZip(File zipFile) throws IOException {
		String fileName = FilenameUtils.getBaseName(zipFile.getName());
		File fileWorkDirectory = new File(baseDirectory, fileName);
		if (fileWorkDirectory.exists()) {
			try {
				FileUtils.cleanDirectory(fileWorkDirectory);
			} catch (IOException ex) {
				LOGGER.debug("Could not clean work directory at " + fileWorkDirectory.getAbsolutePath(), ex);
			}
		}
		FileUtils.forceMkdir(fileWorkDirectory);
		return fileWorkDirectory;
	}
	
	public ShorelineFile buildShorelineFile() throws ShorelineFileFormatException, IOException, FileUploadException {
		if (null == this.zipFile) {
			this.zipFile = saveShorelineZipFileFromRequest(this.request);
			LOGGER.debug("Shoreline saved");
		}

		this.zipFile = gov.usgs.cida.owsutils.commons.io.FileHelper.flattenZipFile(this.zipFile);
		// rename the contents to the zip files name and then pass the exploded directory location, rather than the zip
		File dirToContents = renameZipFileContents(this.zipFile);
		
		ShorelineFile result;
		ShorelineType type = ShorelineType.OTHER;
		try {
			ShorelineLidarFile.validate(dirToContents);
			LOGGER.debug("Lidar file verified");
			type = ShorelineType.LIDAR;
		} catch (LidarFileFormatException | IOException ex) {
			LOGGER.info("Failed lidar validation, try shapefile", ex);
			try {
				ShorelineShapefile.validate(dirToContents);
				LOGGER.debug("Shapefile verified");
				type = ShorelineType.SHAPEFILE;
			} catch (ShorelineFileFormatException | IOException ex1) {
				LOGGER.info("Failed shapefile validation", ex1);
			}
		}

		// By now, I should have figured out what type of file I'm dealing with
		String geoserverEndpoint = PropertyUtil.getProperty(Property.GEOSERVER_ENDPOINT);
		String geoserverUsername = PropertyUtil.getProperty(Property.GEOSERVER_USERNAME);
		String geoserverPassword = PropertyUtil.getProperty(Property.GEOSERVER_PASSWORD);
		GeoserverDAO geoserverHandler = new GeoserverDAO(geoserverEndpoint, geoserverUsername, geoserverPassword);
		switch (type) {
			case LIDAR:
				result = new ShorelineLidarFile(dirToContents, geoserverHandler, new ShorelineLidarFileDAO());
				break;
			case SHAPEFILE:
				result = new ShorelineShapefile(dirToContents, geoserverHandler, new ShorelineShapefileDAO());
				break;
			case OTHER:
			default:
				FileUtils.deleteQuietly(this.zipFile);
				throw new IOException("File is neither LiIDAR or Shapefile");
		}

		FileUtils.deleteQuietly(this.zipFile);

		return result;
	}

	private File saveShorelineZipFileFromRequest(HttpServletRequest request) throws IOException, FileUploadException {
		String filenameParam = PropertyUtil.getProperty(Property.FILE_UPLOAD_FILENAME_PARAM);
		String fnReqParam = request.getParameter(FILENAME_PARAM);
		if (StringUtils.isNotBlank(fnReqParam)) {
			filenameParam = fnReqParam;
		}
		
		LOGGER.debug("Cleaning file name.\nWas: {}", filenameParam);
		String cleanedZipName = ImportUtil.cleanFileName(request.getParameter(filenameParam));
		LOGGER.debug("Is: {}", cleanedZipName);
		
		File cleanedZipFile = new File(this.uploadDirectory, cleanedZipName);
		if (cleanedZipFile.exists()) {
			String filePath = cleanedZipFile.getAbsolutePath();
			LOGGER.debug("Zip file {} already existed. Will remove before writing new zip file from request", filePath);
			if (!FileUtils.deleteQuietly(cleanedZipFile)) {
				LOGGER.warn("Zip file {} could not be deleted. This will probably cause issues in saving the incoming file", filePath);
			}
		}

		try {
			RequestResponse.saveFileFromRequest(request, cleanedZipFile, filenameParam);
		} catch (FileUploadException | IOException ex) {
			LOGGER.info("Could not save file from request", ex);
			FileUtils.deleteQuietly(cleanedZipFile);
			throw ex;
		}
		return cleanedZipFile;
	}

}
