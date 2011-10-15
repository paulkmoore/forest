package com.jayway.forest.reflection.impl;

import com.jayway.forest.di.DependencyInjectionSPI;
import com.jayway.forest.reflection.PagingSortingParameter;
import com.jayway.forest.roles.FieldComparator;
import com.jayway.forest.roles.Linkable;
import com.jayway.forest.roles.Resource;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.*;
import java.util.*;

public class QueryForListCapability extends QueryCapability {

    private DependencyInjectionSPI dependencyInjectionSPI;

    public QueryForListCapability(DependencyInjectionSPI dependencyInjectionSPI, Resource resource, Method method, String documentation) {
        super( resource, method, documentation, method.getName() );
        this.dependencyInjectionSPI = dependencyInjectionSPI;
    }

    @Override
    public PagedSortedListResponse get(HttpServletRequest request) {
        // setup Paging and Sorting parameter
        UrlParameter urlParameter = new UrlParameter(request.getParameterMap());
        PagingSortingParameterImpl pagingSortingParameter = pagingParameter( urlParameter );
        dependencyInjectionSPI.addRequestContext( PagingSortingParameter.class, pagingSortingParameter );

        // actual call to the resource method
        List<?> returnedList = (List<?>) super.get(request);

        PagedSortedListResponse response = new PagedSortedListResponse();
        if (pagingSortingParameter.isTouched()) {
            // the resource has handled the paging
            // so just copy the values to the pagedSortedListResponse
            urlParameter.setPageSize(pagingSortingParameter.getPageSize());
            response.setPage(pagingSortingParameter.getPage());
            response.setList(returnedList);
            response.setPageSize(pagingSortingParameter.getPageSize());
            response.setTotalElements(pagingSortingParameter.getTotalElements());
            if ( pagingSortingParameter.getPage()*pagingSortingParameter.getPageSize() < pagingSortingParameter.getTotalElements() ) {
                response.setNext( name() + urlParameter.linkTo( pagingSortingParameter.getPage()+1) );
            }
            if ( pagingSortingParameter.getPage() > 1 ) {
                response.setPrevious( name() + urlParameter.linkTo( pagingSortingParameter.getPage()-1) );
            }

            for ( String sortField : pagingSortingParameter.getAddedSortFields() ) {
                response.addOrderByAsc( sortField, name() + urlParameter.linkSortBy(sortField, true));
                response.addOrderByDesc(sortField, name() + urlParameter.linkSortBy(sortField, false));
            }
        } else {
            // resource has not used the parameters so handle sorting/paging here
            if ( returnedList != null && !returnedList.isEmpty() ) {
                List<String> sortingParameters = new LinkedList<String>();
                inferSortParameters(sortingParameters, returnedList.get(0).getClass());

                // add possible search strings to the response
                for (String sortField : sortingParameters) {
                    response.addOrderByAsc(sortField, name() + urlParameter.linkSortBy(sortField, true));
                    response.addOrderByDesc(sortField, name() + urlParameter.linkSortBy(sortField, false));
                }
                if (urlParameter.sortBy() != null) {
                    // urlParameters have been parsed and passed to pagingSortingParameter, so sort based on these
                    Collections.sort(returnedList, new FieldComparator( pagingSortingParameter.sortParameters() ));
                } else if (Linkable.class.isAssignableFrom(inferListType())) {
                    // a Linkable is default sorted by name
                    Collections.sort(returnedList, new FieldComparator(new SortParameter("name")));
                }
            }

            // build response
            response.setPage(pagingSortingParameter.getPage());
            response.setPageSize(pagingSortingParameter.getPageSize());
            response.setTotalElements(returnedList == null ? 0 : ((Integer) returnedList.size()).longValue());

            Long actualListSize = response.getTotalElements();
            Long maxIndex = response.getPage() * response.getPageSize();
            Long minIndex = ( response.getPage() - 1 )*response.getPageSize();
            if (actualListSize >= minIndex) {
                List<Object> resultList = new ArrayList<Object>();
                for ( int i=minIndex.intValue(); i<actualListSize && i<maxIndex; i++ ) {
                    resultList.add(returnedList.get(i));
                }
                response.setList(resultList);

                if ( maxIndex < actualListSize ) {
                    response.setNext( name() + urlParameter.linkTo( pagingSortingParameter.getPage()+1) );
                }
                if ( minIndex > 0 ) {
                    response.setPrevious( name() + urlParameter.linkTo( pagingSortingParameter.getPage()-1) );
                }
            }
        }
        response.setTotalPages(  calculateTotalPages(response.getTotalElements(), response.getPageSize()) );
        response.setName(name());

        return response;
    }

    // TODO handle more complicated lists
    private Class inferListType() {
        Type type = method.getGenericReturnType();
        Class listElementClass = Object.class;
        if ( type instanceof ParameterizedType) {
            Type listElementType = ((ParameterizedType) type).getActualTypeArguments()[0];
            // no nested lists yet
            if ( !(listElementType instanceof ParameterizedType) ) {
                listElementClass = (Class) listElementType;
            }
        }
        return listElementClass;
    }

    private void inferSortParameters(List<String> sortingParameters, Class<?> clazz) {
        if ( clazz != Object.class ) {
            for (Field field : clazz.getDeclaredFields()) {
                if ( Modifier.isStatic( field.getModifiers() )) continue;
                if ( Modifier.isFinal( field.getModifiers())) continue;
                sortingParameters.add( field.getName() );
            }
            inferSortParameters( sortingParameters, clazz.getSuperclass() );
        }
    }

    private Long calculateTotalPages( Long totalElements, Long pageSize ) {
        if ( totalElements % pageSize == 0) {
            return totalElements / pageSize;
        }
        return 1+(long)Math.floor(totalElements / pageSize );
    }

    private PagingSortingParameterImpl pagingParameter( UrlParameter urlParameter ) {
        // todo set as a property of the application
        Long pageSize = 10l;
        if ( urlParameter.pageSize() != null ) {
            pageSize = urlParameter.pageSize();
        }
        String sortBy = urlParameter.sortBy();
        return new PagingSortingParameterImpl( urlParameter.page(), pageSize, sortBy );
    }

}
