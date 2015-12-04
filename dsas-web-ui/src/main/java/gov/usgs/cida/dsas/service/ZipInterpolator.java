package gov.usgs.cida.dsas.service;

import gov.usgs.cida.dsas.uncy.ShapefileOutputXploder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.geotools.data.shapefile.files.ShpFileType;
import org.geotools.data.shapefile.files.ShpFiles;
import org.slf4j.LoggerFactory;

public class ZipInterpolator {

	private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ZipInterpolator.class);
	
	/**
	 * Unpack a shapefile zip file, expand the shp from MultilineM to Point
	 * while substituting point-by-point uncertainty values, and return a new
	 * zipfile with that content.
	 *
	 * @param uploadDestinationFile
	 * @return new zipfile.
	 * @throws java.io.IOException
	 */
	public File explode(File uploadDestinationFile) throws IOException {

		// unpack
		File tmpDir = Files.createTempDirectory("xplode").toFile();
		File shpFile = unpack(tmpDir, uploadDestinationFile);

		Map<String, Object> config = new HashMap<>(3);
		config.put(ShapefileOutputXploder.UNCERTAINTY_COLUMN_PARAM, "xplode");
		config.put(ShapefileOutputXploder.INPUT_FILENAME_PARAM, shpFile.getAbsolutePath());
		File outputFile = null;
		try (ShapefileOutputXploder xploder = new ShapefileOutputXploder(config)) {
			xploder.explode();
			outputFile = xploder.getOutputFile();
		} catch (Exception ex) {
			LOGGER.warn("There was an issue during exploding the Shapefile to points", ex);
		}
		return repack(tmpDir, outputFile);
	}

	/**
	 * Creates a zipfile with all the shape components.
	 *
	 * @param tmpDir
	 * @param ptFile
	 * @return
	 */
	private File repack(File tmpDir, File ptFile) throws MalformedURLException, IOException {
		ShpFiles shpFF = new ShpFiles(ptFile);

		Set<File> toPack = new HashSet<>();

		// Map entries are URLs in external form.
		Map<ShpFileType, String> ffMap = shpFF.getFileNames();
		for (String fUrl : ffMap.values()) {
			URL url = new URL(fUrl);
			String fString = url.getFile();
			File f = new File(fString);
			if (f.exists()) {
				toPack.add(f);
			}
		}

		File newZip = File.createTempFile("points", "zip", tmpDir);
		try (FileOutputStream fos = new FileOutputStream(newZip);
				ZipOutputStream zos = new ZipOutputStream(fos)) {
			for (File f : toPack) {
				ZipEntry ze = new ZipEntry(f.getName());
				zos.putNextEntry(ze);

				FileInputStream fis = new FileInputStream(f);
				try {
					copy(fis, zos);
				} finally {
					zos.closeEntry();
				}
			}
		}

		return newZip;
	}

	private void copy(InputStream fis, OutputStream zos) throws IOException {
		byte[] buf = new byte[8 * 1024];
		int ct = 0;
		do {
			ct = fis.read(buf);
			if (ct > 0) {
				zos.write(buf, 0, ct);
			}
		} while (ct > 0);
	}

	/**
	 *
	 * @param tmpDir
	 * @param uploadDestinationFile
	 * @return shortest-named .shp file found
	 */
	private File unpack(File tmpDir, File zipFile) throws IOException {
		File f = null;

		try (FileInputStream fin = new FileInputStream(zipFile);
				ZipInputStream zis = new ZipInputStream(fin)) {
			while (true) {
				ZipEntry ze = zis.getNextEntry();
				if (ze == null) {
					break;
				}

				File zf = new File(tmpDir, ze.getName());
				if (ze.getName().endsWith(".shp")) {
					if (f == null || f.getName().length() > ze.getName().length()) {
						f = new File(tmpDir, ze.getName());
					}
				}

				try (FileOutputStream fout = new FileOutputStream(zf)) {
					copy(zis, fout);
				}
			}
		}
		return f;
	}

}
