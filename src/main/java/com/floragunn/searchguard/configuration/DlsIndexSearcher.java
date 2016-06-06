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
import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.elasticsearch.index.engine.EngineConfig;
import org.elasticsearch.index.query.ParsedQuery;

class DlsIndexSearcher extends IndexSearcher {

    private final List<ParsedQuery> dlsQueries;
    private final IndexSearcher original;
    
    DlsIndexSearcher(IndexSearcher original, EngineConfig engineConfig, List<ParsedQuery> dlsQueries) {
        super(original.getIndexReader());
        this.original = original;
        this.dlsQueries = dlsQueries;
        this.setQueryCache(null); //engineConfig.getQueryCache()
        this.setQueryCachingPolicy(engineConfig.getQueryCachingPolicy());
        this.setSimilarity(engineConfig.getSimilarity());
    }
    
    @Override
    public Weight createWeight(Query query, boolean needsScores) throws IOException {
        return original.createWeight(rewriteToDls(query, needsScores), needsScores);
    }


    private Query rewriteToDls(Query original, boolean needsScores) throws IOException {

        if(dlsQueries != null && !dlsQueries.isEmpty()) {
            
            BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
            boolQueryBuilder.add(original, needsScores?Occur.MUST:Occur.FILTER);
            BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
            dlsQueryBuilder.setMinimumNumberShouldMatch(1);
            
            for (ParsedQuery dlsQuery: dlsQueries) {
                dlsQueryBuilder.add(dlsQuery.query(), Occur.SHOULD);
            }
            
            boolQueryBuilder.add(dlsQueryBuilder.build(), needsScores?Occur.MUST:Occur.FILTER);
            return boolQueryBuilder.build();
        }
        
        
        return original;
    }
}
