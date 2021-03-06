/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.xpack.spatial.index.query;

import org.apache.lucene.document.LatLonShape;
import org.apache.lucene.geo.LatLonGeometry;
import org.apache.lucene.search.IndexOrDocValuesQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.Version;
import org.elasticsearch.common.geo.GeoShapeUtils;
import org.elasticsearch.common.geo.ShapeRelation;
import org.elasticsearch.geometry.Geometry;
import org.elasticsearch.geometry.Line;
import org.elasticsearch.geometry.MultiLine;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.index.query.SearchExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class VectorGeoShapeWithDocValuesQueryProcessor {

    private static final List<Class<? extends Geometry>> WITHIN_UNSUPPORTED_GEOMETRIES = new ArrayList<>();
    static {
        WITHIN_UNSUPPORTED_GEOMETRIES.add(Line.class);
        WITHIN_UNSUPPORTED_GEOMETRIES.add(MultiLine.class);
    }

    public Query geoShapeQuery(Geometry shape, String fieldName, ShapeRelation relation,
                               SearchExecutionContext context, boolean hasDocValues) {
        // CONTAINS queries are not supported by VECTOR strategy for indices created before version 7.5.0 (Lucene 8.3.0)
        if (relation == ShapeRelation.CONTAINS && context.indexVersionCreated().before(Version.V_7_5_0)) {
            throw new QueryShardException(context,
                ShapeRelation.CONTAINS + " query relation not supported for Field [" + fieldName + "].");
        }
        final LatLonGeometry[] luceneGeometries = relation == ShapeRelation.WITHIN ?
                GeoShapeUtils.toLuceneGeometry(fieldName, context, shape, WITHIN_UNSUPPORTED_GEOMETRIES) :
                GeoShapeUtils.toLuceneGeometry(fieldName, context, shape, Collections.emptyList());

        if (luceneGeometries.length == 0) {
            return new MatchNoDocsQuery();
        }
        Query query = LatLonShape.newGeometryQuery(fieldName, relation.getLuceneRelation(), luceneGeometries);
        if (hasDocValues) {
            final Query queryDocValues = new LatLonShapeDocValuesQuery(fieldName, relation.getLuceneRelation(), luceneGeometries);
            query =  new IndexOrDocValuesQuery(query, queryDocValues);
        }
        return query;
    }
}

