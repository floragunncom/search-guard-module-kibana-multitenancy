/*
 * Copyright 2016 by floragunn UG (haftungsbeschränkt) - All rights reserved
 * 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed here is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 
 * This software is free of charge for non-commercial and academic use.
 * For commercial use in a production environment you have to obtain a license
 * from https://floragunn.com
 * 
 */

package com.floragunn.searchguard.configuration;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryShardContext;

import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.HeaderHelper;
import com.google.common.collect.Sets;

public class SearchGuardFlsDlsIndexSearcherWrapper extends SearchGuardIndexSearcherWrapper {

    private final QueryShardContext queryShardContext;
    private final Set<String> metaFields;

    public static void printLicenseInfo() {
        System.out.println("***************************************************");
        System.out.println("Searchguard DLS/FLS(+) Security is not free software");
        System.out.println("for commercial use in production.");
        System.out.println("You have to obtain a license if you ");
        System.out.println("use it in production.");
        System.out.println("(+) Document-/Fieldlevel");
        System.out.println("***************************************************");
    }

    static {
        printLicenseInfo();
    }

    public SearchGuardFlsDlsIndexSearcherWrapper(final IndexService indexService, final Settings settings) {
        super(indexService, settings);
        this.queryShardContext = indexService.newQueryShardContext();
        metaFields = Sets.union(Sets.newHashSet("_source", "_version"), Sets.newHashSet(MapperService.getAllMetaFields()));
    }

    @Override
    protected DirectoryReader dlsFlsWrap(final DirectoryReader reader) throws IOException {

        final Set<String> flsFields = new HashSet<String>(metaFields);
        final Map<String, Set<String>> allowedFlsFields = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_FLS_FIELDS);

        final String eval = evalMap(allowedFlsFields, index.getName());

        if (eval != null) {
            flsFields.addAll(allowedFlsFields.get(eval));
            if (log.isTraceEnabled()) {
                log.trace("Found! _sg_fls_fields for {}", index.getName());
            }
        } else {

            if (log.isTraceEnabled()) {
                log.trace("No _sg_fls_fields for {}", index.getName());
            }

            return reader;
        }

        return new FlsFilterLeafReader.FlsDirectoryReader(reader, flsFields);
    }

    @Override
    protected IndexSearcher dlsFlsWrap(final IndexSearcher searcher) throws EngineException {

        final Map<String, Set<String>> queries = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_DLS_QUERY);

        final String eval = evalMap(queries, index.getName());

        if (eval != null) {

            if (log.isTraceEnabled()) {
                log.trace("Found! _sg_dls_query for {}", index.getName());
            }

            return new DlsIndexSearcher(searcher, queryShardContext, queries.get(eval));

        } else {

            if (log.isTraceEnabled()) {
                log.trace("No _sg_dls_query for {}", index.getName());
            }

            return searcher;
        }
    }

    private String evalMap(Map<String, Set<String>> map, String index) {

        if (map == null) {
            return null;
        }

        if (map.get(index) != null) {
            return index;
        } else if (map.get("*") != null) {
            return "*";
        }
        if (map.get("_all") != null) {
            return "_all";
        }

        return null;
    }
}
