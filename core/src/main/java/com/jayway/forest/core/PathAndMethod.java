package com.jayway.forest.core;

import java.util.ArrayList;
import java.util.List;

class PathAndMethod {
    private List<String> pathSegments;
    private String method;

    public PathAndMethod( String rawPath, String httpMethod ) {
        int index = rawPath.indexOf( '/' );
        if ( index > 0 ) {
            rawPath = rawPath.substring( index+1 );
        }
        boolean onlyPathSegments = rawPath.endsWith("/");
        pathSegments =  new ArrayList<String>();
        String[] split = rawPath.split("/");
        for ( int i=0; i<split.length; i++) {
            if ((onlyPathSegments || i != split.length - 1) && split[i].length() > 0) {
                pathSegments.add(split[i]);
            }
        }
        method = null;
        if (onlyPathSegments) {
            if (httpMethod.equals( "POST" )) {
                method = "create";
            } else if ( httpMethod.equals("DELETE")) {
                method = "delete";
            }
        } else {
            method = split[ split.length -1 ];
        }

    }

    public List<String> pathSegments() {
        return pathSegments;
    }

    public String method() {
        return method;
    }
}