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

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.engine.EngineConfig;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.query.ParsedQuery;
import org.elasticsearch.index.query.QueryParsingException;

class DlsIndexSearcher extends IndexSearcher {

    private final ESLogger log = Loggers.getLogger(this.getClass());
    private final List<ParsedQuery> parsedDlsQueries = new ArrayList<ParsedQuery>();
    private volatile boolean rewritten;

    DlsIndexSearcher(IndexSearcher original, EngineConfig engineConfig, IndexQueryParserService parser, Set<String> unparsedDlsQueries) {
        super(original.getIndexReader());
        this.setQueryCache(null); // engineConfig.getQueryCache()
        this.setQueryCachingPolicy(engineConfig.getQueryCachingPolicy());
        this.setSimilarity(engineConfig.getSimilarity());

        try {
            for (final String unparsedDlsQuery : unparsedDlsQueries) {
                if (log.isTraceEnabled()) {
                    log.trace("parse: {}", unparsedDlsQuery);
                }
                parsedDlsQueries.add(parser.parse(unparsedDlsQuery));
            }
        } catch (final QueryParsingException e) {
            throw e;
        }
    }

    @Override
    public Query rewrite(Query original) throws IOException {

        if (rewritten || parsedDlsQueries == null || parsedDlsQueries.isEmpty()) {
            return super.rewrite(original);
        }

        final Query r = super.rewrite(rewriteToDls(original));
        rewritten = true;
        return r;
    }

    private Query rewriteToDls(Query original) throws IOException {

        BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
        boolQueryBuilder.add(original, Occur.MUST);
        BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
        dlsQueryBuilder.setMinimumNumberShouldMatch(1);

        for (ParsedQuery dlsQuery : parsedDlsQueries) {
            dlsQueryBuilder.add(dlsQuery.query(), Occur.SHOULD);
        }

        boolQueryBuilder.add(dlsQueryBuilder.build(), Occur.MUST);
        return boolQueryBuilder.build();

    }
}
