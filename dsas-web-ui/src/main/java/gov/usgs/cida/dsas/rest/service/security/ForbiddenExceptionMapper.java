package gov.usgs.cida.dsas.rest.service.security;

import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import org.apache.commons.lang.StringUtils;

/**
 *
 * @author isuftin
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

	@Context
	UriInfo uriInfo;

	@Override
	public Response toResponse(ForbiddenException exception) {
		String[] pathParts = uriInfo.getAbsolutePath().getPath().split("/");
		String applicationPath = "/" + (StringUtils.isNotBlank(pathParts[0]) ? pathParts[0] : pathParts[1]);
		String originalPath = uriInfo.getAbsolutePath().getPath().substring(applicationPath.length());
		try {
			return Response.temporaryRedirect(new URI(applicationPath + "/ui/security/login#" + originalPath)).build();
		} catch (URISyntaxException ex) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Error redirecting to login page").encoding(MediaType.TEXT_PLAIN).build();
		}
	}

}