package gov.usgs.cida.dsas.rest.service.shapefile;

import gov.usgs.cida.dsas.rest.service.ServiceURI;
import gov.usgs.cida.dsas.rest.service.security.DynamicRolesLoginRedirectFeature;
import gov.usgs.cida.dsas.rest.service.security.ForbiddenExceptionMapper;
import gov.usgs.cida.dsas.rest.service.security.TokenBasedSecurityFilter;
import javax.ws.rs.ApplicationPath;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;

/**
 *
 * @author isuftin
 */
@ApplicationPath(ServiceURI.SHAPEFILE_SERVICE_ENDPOINT)
public class ShapefileApplication extends ResourceConfig {

	public ShapefileApplication() {
		packages(this.getClass().getPackage().getName());
		register(MultiPartFeature.class);
		register(JspMvcFeature.class);
		register(ForbiddenExceptionMapper.class);
		register(TokenBasedSecurityFilter.class);
		register(DynamicRolesLoginRedirectFeature.class);
	}

}
