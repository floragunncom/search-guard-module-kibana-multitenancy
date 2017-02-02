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
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.index.IndexService;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryShardContext;

import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.support.HeaderHelper;
import com.google.common.collect.Sets;

public class SearchGuardFlsDlsIndexSearcherWrapper extends SearchGuardIndexSearcherWrapper {

    private final QueryShardContext queryShardContext;
    private final static Set<String> metaFields = Sets.union(Sets.newHashSet("_source", "_version"), 
            Sets.newHashSet(MapperService.getAllMetaFields()));
    private final NamedXContentRegistry namedXContentRegistry;

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
        this.queryShardContext = indexService.newQueryShardContext(0, null, null);
        this.namedXContentRegistry = indexService.xContentRegistry();
    }

    @Override
    protected DirectoryReader dlsFlsWrap(final DirectoryReader reader) throws IOException {

        Set<String> flsFields = null;
        Set<String> unparsedDlsQueries = null;
        
        final Map<String, Set<String>> allowedFlsFields = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_FLS_FIELDS);
        final Map<String, Set<String>> queries = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_DLS_QUERY);

        final String flsEval = evalMap(allowedFlsFields, index.getName());
        final String dlsEval = evalMap(queries, index.getName());

        if (flsEval != null) { 
            flsFields = new HashSet<String>(metaFields);
            flsFields.addAll(allowedFlsFields.get(flsEval));
        }
        
        if (dlsEval != null) { 
            unparsedDlsQueries = queries.get(dlsEval);
        }
        
        return new DlsFlsFilterLeafReader.DlsFlsDirectoryReader(reader, flsFields, DlsQueryParser.parse(unparsedDlsQueries, queryShardContext, this.namedXContentRegistry));
    }
        
        
    @Override
    protected IndexSearcher dlsFlsWrap(final IndexSearcher searcher) throws EngineException {

        if(searcher.getIndexReader().getClass() != DlsFlsFilterLeafReader.DlsFlsDirectoryReader.class) {
            throw new RuntimeException("Unexpected index reader class "+searcher.getIndexReader().getClass());
        }
        
        return searcher;
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
