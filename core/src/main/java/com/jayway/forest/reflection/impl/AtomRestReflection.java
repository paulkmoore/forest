package com.jayway.forest.reflection.impl;

import static com.jayway.forest.core.RoleManager.role;
import static org.apache.commons.lang.StringEscapeUtils.escapeXml;

import java.io.StringWriter;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import com.jayway.forest.exceptions.UnsupportedMediaTypeException;
import com.jayway.forest.reflection.Capabilities;
import com.jayway.forest.reflection.RestReflection;
import com.jayway.forest.roles.Linkable;
import com.jayway.forest.roles.UriInfo;
import com.jayway.forest.servlet.Response;

/**
 */
public class AtomRestReflection implements RestReflection {

    public static final AtomRestReflection INSTANCE = new AtomRestReflection();
    private VelocityEngine engine;

    private AtomRestReflection() {}

    @Override
    public Object renderListResponse(PagedSortedListResponse response) {
        engine = new VelocityEngine();
        engine.setProperty("resource.loader","class");
        engine.setProperty("class.resource.loader.class",
                "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
        try {
            engine.init();
            Template template = engine.getTemplate("com/jayway/forest/atom.vm", "utf-8");
            VelocityContext context = new VelocityContext();
            context.put( "base", role( UriInfo.class).getBaseUrl() );
            context.put( "title", response.getName() );
            context.put( "next", escapeXml(response.getNext()) );
            context.put( "previous", escapeXml(response.getPrevious()) );
            UriInfo uriInfo = role(UriInfo.class);
            context.put("self", escapeXml(uriInfo.getSelf()));
            context.put( "list", response.getList() );
            StringWriter writer = new StringWriter();
            template.merge( context, writer );
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object renderQueryResponse(Object responseObject) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderError(Response response) {
        return response.message();
    }

    @Override
    public Object renderCreatedResponse(Linkable linkable) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderCapabilities(Capabilities capabilities) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderCommandForm( BaseReflection baseReflection ) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderQueryForm(BaseReflection baseReflection) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderCommandCreateForm(BaseReflection baseReflection) {
        throw new UnsupportedMediaTypeException();
    }

    @Override
    public Object renderCommandDeleteForm(BaseReflection baseReflection) {
        throw new UnsupportedMediaTypeException();
    }
}
