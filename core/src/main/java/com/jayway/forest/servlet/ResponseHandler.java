package com.jayway.forest.servlet;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.forest.core.MediaTypeHandler;
import com.jayway.forest.di.DependencyInjectionSPI;
import com.jayway.forest.exceptions.AbstractHtmlException;
import com.jayway.forest.exceptions.CreatedException;
import com.jayway.forest.exceptions.NotFoundException;
import com.jayway.forest.exceptions.RenderTemplateException;
import com.jayway.forest.exceptions.WrappedException;
import com.jayway.forest.reflection.Capabilities;
import com.jayway.forest.reflection.RestReflection;
import com.jayway.forest.reflection.impl.AtomRestReflection;
import com.jayway.forest.reflection.impl.HtmlRestReflection;
import com.jayway.forest.reflection.impl.JsonRestReflection;
import com.jayway.forest.reflection.impl.PagedSortedListResponse;
import com.jayway.forest.roles.Linkable;
import com.jayway.forest.roles.UriInfo;

/**
 */
public class ResponseHandler {
    private static Logger log = LoggerFactory.getLogger(ResponseHandler.class);
    private static Map<String, RestReflection> reflectors;

    static {
        reflectors = new HashMap<String, RestReflection>();
        reflectors.put(MediaTypeHandler.APPLICATION_JSON, JsonRestReflection.INSTANCE);
        reflectors.put(MediaTypeHandler.TEXT_HTML, HtmlRestReflection.INSTANCE);
        reflectors.put(MediaTypeHandler.APPLICATION_ATOM, AtomRestReflection.INSTANCE);
    }

    private RestReflection restReflection() {
        return reflectors.get( mediaTypeHandler.accept() );
    }

    public static final String SUCCESS_RESPONSE = "Operation completed successfully";

    private MediaTypeHandler mediaTypeHandler;
    private HttpServletResponse response;
    private ExceptionMapper exceptionMapper;

    public ResponseHandler( HttpServletRequest request, HttpServletResponse response, ExceptionMapper exceptionMapper, DependencyInjectionSPI dependencyInjectionSPI ) throws IOException {
        mediaTypeHandler = new MediaTypeHandler(request, response );
        this.response = response;
        this.exceptionMapper = exceptionMapper;

        if ( request.getPathInfo() == null ) {
             response.sendError(404);
             throw new NotFoundException();
         }
         
        // add request specifics to the current context
        dependencyInjectionSPI.clear();
        dependencyInjectionSPI.addRequestContext(UriInfo.class, new UriInfo(request));
        dependencyInjectionSPI.addRequestContext(HttpServletRequest.class, request);
        dependencyInjectionSPI.addRequestContext(HttpServletResponse.class, response);
    }

    public void handleResponse( Object responseObject ) {
        if (responseObject != null) {
            try {
                OutputStreamWriter writer = new OutputStreamWriter( response.getOutputStream(), Charset.forName("UTF-8"));
                String responseString;
                if ( responseObject instanceof Capabilities ) {
                    responseString = restReflection().renderCapabilities((Capabilities) responseObject ).toString();
                } else if ( responseObject instanceof PagedSortedListResponse) {
                    responseString = restReflection().renderListResponse((PagedSortedListResponse) responseObject).toString();
                } else if ( responseObject instanceof Response ) {
                    Response error = (Response) responseObject;
                    responseString = restReflection().renderError(error).toString();
                    response.setStatus( error.status() );
                } else if ( responseObject instanceof CreatedException ) {
                    responseString = restReflection().renderCreatedResponse( ((CreatedException) responseObject).getLinkable() ).toString();
                    response.setStatus( ((CreatedException) responseObject).getCode() );
                    response.addHeader( "Location", ((CreatedException) responseObject).getLinkable().getHref() );
                } else {
                    responseString = restReflection().renderQueryResponse( responseObject ).toString();
                }
                writer.write( responseString, 0, responseString.length() );
                writer.flush();
            } catch (IOException e) {
                response.setStatus( HttpServletResponse.SC_INTERNAL_SERVER_ERROR );
                log.error("Error sending response", e);
            }
        }
    }

    public void invoke( RestfulServlet.Runner runner ) {
        Object responseObject;
        try {
            responseObject = runner.run(mediaTypeHandler);
        } catch ( Exception e ) {
            if ( e instanceof CreatedException ) {
                responseObject = e;
            } else {
                responseObject = mapInternalException(e);
            }
        }
        handleResponse(responseObject);
    }

    private Response mapInternalException( Exception e ) {
        if ( e instanceof WrappedException ) {
            Throwable cause = e.getCause();
            if ( cause instanceof Exception ) {
                e = (Exception) cause;
            }
        }

        if ( exceptionMapper != null ) {
            Response response = exceptionMapper.map(e);
            if (response != null) {
                return response;
            }
        }

        if ( e instanceof RenderTemplateException ) {
            RenderTemplateException render = (RenderTemplateException) e;
            return new Response( render.getCode(), render.getCapability().renderForm( restReflection() ).toString() );
        } else if ( e instanceof AbstractHtmlException ) {
            return new Response( ((AbstractHtmlException) e).getCode(), e.getMessage() );
        } else {
            log.error(e.getClass().getSimpleName() + " is not mapped", e);
            if ( e.getMessage() == null ) {
                return new Response( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getClass().getSimpleName() );
            } else {
                return new Response( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage() );
            }
        }
    }

    public static <T> List<Linkable> transform( Iterable<T> from, Transform<T> transformer ) {
        List<Linkable> links = new ArrayList<Linkable>();
        for ( T t : from ) {
            links.add( transformer.transform( t) );
        }
        return links;
    }


    public interface Transform<T> {
        Linkable transform( T t );
    }
}
