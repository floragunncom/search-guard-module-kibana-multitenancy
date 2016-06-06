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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.engine.EngineConfig;
import org.elasticsearch.index.engine.EngineException;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.query.ParsedQuery;
import org.elasticsearch.index.query.QueryParsingException;
import org.elasticsearch.index.shard.ShardId;
import org.elasticsearch.indices.IndicesLifecycle;

import com.floragunn.searchguard.action.configupdate.TransportConfigUpdateAction;
import com.floragunn.searchguard.support.HeaderHelper;
import com.google.common.collect.Sets;

public class SearchGuardFlsDlsIndexSearcherWrapper extends SearchGuardIndexSearcherWrapper {

    private final IndexQueryParserService parser;
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

    @Inject
    public SearchGuardFlsDlsIndexSearcherWrapper(final ShardId shardId, final IndicesLifecycle indicesLifecycle,
            final Settings indexSettings, final AdminDNs admindns, final IndexQueryParserService parser) {
        super(shardId, indicesLifecycle, indexSettings, admindns);
        this.parser = parser;
        metaFields = Sets.union(Sets.newHashSet("_source", "_version"), Sets.newHashSet(MapperService.getAllMetaFields()));
    }

    @Override
    protected DirectoryReader dlsFlsWrap(final DirectoryReader reader) throws IOException {

        final Set<String> flsFields = new HashSet<String>(metaFields);
        final RequestHolder current = RequestHolder.current();

        if (current != null && current.getRequest() != null) {

            final Set<String> allowedFlsFields = (Set<String>) HeaderHelper.deserializeSafeFromHeader(current.getRequest(), "_sg_fls_fields");
              
            if (allowedFlsFields != null && !allowedFlsFields.isEmpty()) {
                flsFields.addAll(allowedFlsFields);
                if (log.isTraceEnabled()) {
                    log.trace("Found! _sg_fls_fields for {}", current.getRequest().getClass());
                }
            } else {
                
                if (log.isTraceEnabled()) {
                    log.trace("No _sg_fls_fields for {}", current.getRequest().getClass());
                }
                
                return reader;
            }

        } else {
            
            if (log.isTraceEnabled()) {
                log.trace("No context/request for fls");
            }

        }
        
        return new FlsFilterLeafReader.FlsDirectoryReader(reader, flsFields);

    }

    @Override
    protected IndexSearcher dlsFlsWrap(final EngineConfig engineConfig, final IndexSearcher searcher) throws EngineException {

        final RequestHolder current = RequestHolder.current();
        Exception ex = null;

        if (current != null && current.getRequest() != null) {
            
            final Set<String> queries = (Set<String>) HeaderHelper.deserializeSafeFromHeader(current.getRequest(), "_sg_dls_query");
            
            if (queries != null && !queries.isEmpty()) {

                if (log.isTraceEnabled()) {
                    log.trace("Found! _sg_dls_query for {}", current.getRequest().getClass());
                }

                try {
                    final List<ParsedQuery> parsed = new ArrayList<ParsedQuery>();
                    for (final String query : queries) {
                        if (log.isTraceEnabled()) {
                            log.trace("parse: {}", query);
                        }
                        parsed.add(parser.parse(query));
                    }
                    return new DlsIndexSearcher(searcher, engineConfig, parsed);
                } catch (final QueryParsingException e) {
                    log.error("Unable to parse dls query " + e, e);
                    ex = e;
                }
            } else {
                
                if (log.isTraceEnabled()) {
                    log.trace("No _sg_dls_query for {}", current.getRequest().getClass());
                }
                
                return searcher;
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("No context/request for dls");
            }
        }

        throw new EngineException(shardId, "Unable to handle document level security due to: "+(ex==null?"":ex.toString()));
    }
}
