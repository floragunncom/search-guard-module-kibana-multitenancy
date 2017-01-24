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
import org.apache.lucene.search.Query;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.EngineConfig;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesLifecycle;

import com.floragunn.searchguard.support.HeaderHelper;
import com.google.common.collect.Sets;

public class SearchGuardFlsDlsIndexSearcherWrapper extends SearchGuardIndexSearcherWrapper {

    private final IndexQueryParserService parser;
    private final static Set<String> metaFields = Sets.union(Sets.newHashSet("_source", "_version"), 
            Sets.newHashSet(MapperService.getAllMetaFields()));

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

    @Inject
    public SearchGuardFlsDlsIndexSearcherWrapper(final ShardId shardId, final IndicesLifecycle indicesLifecycle,
            final Settings indexSettings, final AdminDNs admindns, final IndexQueryParserService parser) {
        super(shardId, indicesLifecycle, indexSettings, admindns);
        this.parser = parser;
    }

    @Override
    protected DirectoryReader dlsFlsWrap(final DirectoryReader reader) throws IOException {

        
        final RequestHolder current = RequestHolder.current();

        if (current != null && current.getRequest() != null) {

            final Map<String,Set<String>> allowedFlsFields = (Map<String,Set<String>>) HeaderHelper.deserializeSafeFromHeader(current.getRequest(), "_sg_fls_fields");
            final Map<String,Set<String>> queries = (Map<String,Set<String>>) HeaderHelper.deserializeSafeFromHeader(current.getRequest(), "_sg_dls_query");
            
            final String flsEval = evalMap(allowedFlsFields, shardId.getIndex());
            final String dlsEval = evalMap(queries, shardId.getIndex());
            
            Set<String> flsFields = null;
            Query dlsQuery = null;
            
            if (flsEval != null) {
                flsFields = new HashSet<String>(metaFields);
                flsFields.addAll(allowedFlsFields.get(flsEval));
            } 
            
            if (dlsEval != null) {
                dlsQuery = DlsQueryParser.parse(queries.get(dlsEval), parser);
            } 

            return new DlsFlsFilterLeafReader.DlsFlsDirectoryReader(reader, flsFields, dlsQuery);
            
        } else {
            
            if (log.isTraceEnabled()) {
                log.trace("No context/request");
            }

        }
        
        return reader;

    }

    @Override
    protected IndexSearcher dlsFlsWrap(final EngineConfig engineConfig, final IndexSearcher searcher) throws EngineException {
        
        if(searcher.getIndexReader().getClass() != DlsFlsFilterLeafReader.DlsFlsDirectoryReader.class) {
            throw new RuntimeException("Unexpected searcher class: "+searcher.getIndexReader().getClass());
        }
        
        return searcher;
    }
    
    private String evalMap(final Map<String,Set<String>> map, final String index) {
        
        if(map == null) {
            return null;
        }
        
        if(map.get(index) != null) {
            return index;
        } else if(map.get("*") != null) {
            return "*";
        } if(map.get("_all") != null) {
            return "_all";
        }
        
        //regex
        for(final String key: map.keySet()) {
            if(WildcardMatcher.containsWildcard(key) 
                    && WildcardMatcher.match(key, index)) {
                return key;
            }
        }
        
        return null;
    }
}
