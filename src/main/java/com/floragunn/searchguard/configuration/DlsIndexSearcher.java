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
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.ParsedQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;

class DlsIndexSearcher extends IndexSearcher {

    private final Logger log = LogManager.getLogger(this.getClass());
    private final Set<String> unparsedDlsQueries;
    private final IndexSearcher original;
    private final QueryShardContext queryShardContext;
    private final List<ParsedQuery> parsedDlsQueries = new ArrayList<ParsedQuery>();
    
    DlsIndexSearcher(IndexSearcher original, QueryShardContext queryShardContext, Set<String> unparsedDlsQueries) {
        super(original.getIndexReader());
        this.original = original;
        this.unparsedDlsQueries = unparsedDlsQueries;
        this.queryShardContext = queryShardContext;
        
        setQueryCache(null);  
        //setQueryCache(original.getQueryCache());
        setQueryCachingPolicy(original.getQueryCachingPolicy());
        setSimilarity(original.getSimilarity(true));
    }

    @Override
    public Weight createNormalizedWeight(Query query, boolean needsScores) throws IOException {
        
        if (log.isTraceEnabled()) {
            log.trace("query: {}", query);
        }
        
        for (final String unparsedDlsQuery : unparsedDlsQueries) {
            
            if (log.isTraceEnabled()) {
                log.trace("parse: {}", unparsedDlsQuery);
            }
            
            XContentParser parser = XContentFactory.xContent(unparsedDlsQuery).createParser(unparsedDlsQuery);                
            QueryBuilder qb = queryShardContext.newParseContext(parser).parseInnerQueryBuilder().get();
            ParsedQuery parsedQuery = queryShardContext.toQuery(qb);
            parsedDlsQueries.add(parsedQuery);
        }
        
        return original.createNormalizedWeight(rewriteToDls(query, needsScores), needsScores);
    }


    private Query rewriteToDls(Query original, boolean needsScores) throws IOException {

        if(!parsedDlsQueries.isEmpty()) {
            
            BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
            boolQueryBuilder.add(original, needsScores?Occur.MUST:Occur.FILTER);
            BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
            dlsQueryBuilder.setMinimumNumberShouldMatch(1);
            
            for (ParsedQuery dlsQuery: parsedDlsQueries) {
                dlsQueryBuilder.add(dlsQuery.query(), Occur.SHOULD);
            }
            
            boolQueryBuilder.add(dlsQueryBuilder.build(), needsScores?Occur.MUST:Occur.FILTER);
            return boolQueryBuilder.build();
        }
        
        
        return original;
    }
}
