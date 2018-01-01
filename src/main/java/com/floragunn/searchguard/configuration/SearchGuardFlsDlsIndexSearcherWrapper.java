/*
 * Copyright 2016-2017 by floragunn GmbH - All rights reserved
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
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
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
import com.floragunn.searchguard.support.WildcardMatcher;
import com.google.common.collect.Sets;

public class SearchGuardFlsDlsIndexSearcherWrapper extends SearchGuardIndexSearcherWrapper {

    private final QueryShardContext queryShardContext;
    private static final Set<String> metaFields = Sets.union(Sets.newHashSet("_source", "_version"), 
            Sets.newHashSet(MapperService.getAllMetaFields()));
    private final NamedXContentRegistry namedXContentRegistry;

    private static void printLicenseInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("******************************************************"+System.lineSeparator());
        sb.append("Searchguard DLS/FLS(+) Security is not free software"+System.lineSeparator());
        sb.append("for commercial use in production."+System.lineSeparator());
        sb.append("You have to obtain a license if you "+System.lineSeparator());
        sb.append("use it in production."+System.lineSeparator());
        sb.append("(+) Document-/Fieldlevel"+System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("See https://floragunn.com/searchguard-validate-license"+System.lineSeparator());
        sb.append("In case of any doubt mail to <sales@floragunn.com>"+System.lineSeparator());
        sb.append("*****************************************************"+System.lineSeparator());
        
        final String licenseInfo = sb.toString();
        
        if(!Boolean.getBoolean("sg.display_lic_none")) {
            
            if(!Boolean.getBoolean("sg.display_lic_only_stdout")) {
                LogManager.getLogger(SearchGuardFlsDlsIndexSearcherWrapper.class).warn(licenseInfo);
                System.err.println(licenseInfo);
            }
    
            System.out.println(licenseInfo);
        }
        
    }

    static {
        //printLicenseInfo();
    }

    public SearchGuardFlsDlsIndexSearcherWrapper(final IndexService indexService, final Settings settings, final AdminDNs adminDNs) {
        super(indexService, settings, adminDNs);
        this.queryShardContext = indexService.newQueryShardContext(0, null, () -> 0L, null);
        this.namedXContentRegistry = indexService.xContentRegistry();
    }

    @Override
    protected DirectoryReader dlsFlsWrap(final DirectoryReader reader) throws IOException {

        Set<String> flsFields = null;
        Set<String> unparsedDlsQueries = null;
        
        final Map<String, Set<String>> allowedFlsFields = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_FLS_FIELDS_HEADER);
        final Map<String, Set<String>> queries = (Map<String, Set<String>>) HeaderHelper.deserializeSafeFromHeader(threadContext,
                ConfigConstants.SG_DLS_QUERY_HEADER);

        final String flsEval = evalMap(allowedFlsFields, index.getName());
        final String dlsEval = evalMap(queries, index.getName());

        if (flsEval != null) { 
            flsFields = new HashSet<>(metaFields);
            flsFields.addAll(allowedFlsFields.get(flsEval));
        }
        
        if (dlsEval != null) { 
            unparsedDlsQueries = queries.get(dlsEval);
        }
        
        return new DlsFlsFilterLeafReader.DlsFlsDirectoryReader(reader, flsFields, DlsQueryParser.parse(unparsedDlsQueries, queryShardContext, this.namedXContentRegistry));
    }
        
        
    @Override
    protected IndexSearcher dlsFlsWrap(final IndexSearcher searcher) throws EngineException {

        if(searcher.getIndexReader().getClass() != DlsFlsFilterLeafReader.DlsFlsDirectoryReader.class
                && searcher.getIndexReader().getClass() != EmptyFilterLeafReader.EmptyDirectoryReader.class) {
            throw new RuntimeException("Unexpected index reader class "+searcher.getIndexReader().getClass());
        }
        
        return searcher;
    }
        
    private String evalMap(final Map<String,Set<String>> map, final String index) {

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
