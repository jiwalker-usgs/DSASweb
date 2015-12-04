package gov.usgs.cida.dsas.model;


import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.naming.NamingException;
import org.geotools.feature.SchemaException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

/**
 * A Java object that represents a Shapefile
 *
 * @author isuftin
 */
@Deprecated
public interface IShapeFile {

	static final String SHP = "shp";
	static final String SHX = "shx";
	static final String DBF = "dbf";
	static final String PRJ = "prj";
	static final String FBX = "fbx";
	static final String SBX = "sbx";
	static final String AIH = "aih";
	static final String IXS = "ixs";
	static final String MXS = "mxs";
	static final String ATX = "atx";
	static final String SHP_XML = "shp.xml";
	static final String CPG = "cpg";
	static final String CST = "cst";
	static final String CSV = "csv";
	public static final String[] REQUIRED_FILES = new String[]{SHP, SHX, DBF};
	public static final String[] OPTIONAL_FILES = new String[]{PRJ, FBX, SBX, AIH, IXS, MXS, ATX, SHP_XML, CPG, CST, CSV};

	/**
	 * Gets the EPSG code that the shapefile is encoded in
	 *
	 * @return string representation of EPSG code
	 */
	public String getEPSGCode() throws IOException;

	/**
	 * Validates file to be a valid shapefile
	 *
	 * @return true if valid file, false if not
	 * @throws java.io.IOException
	 */
	public boolean validate() throws IOException;
	
	/**
	 * Gets the File objects for the required files that make up a shapefile
	 * @return the required files
	 */
	public List<File> getRequiredFiles();
	
	/**
	 * Gets the File objects for the optional files that make up a shapefile
	 * @return the required files
	 */
	public List<File> getOptionalFiles();
        
	/**
	 * Gets the attribute names from the dbf file
	 * @return the list of names
	 */
        public List<String> getDbfColumnNames();
}
