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
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.ToChildBlockJoinQuery;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;


final class DlsQueryParser {
    
  private static final Query NON_NESTED_QUERY;
  
  static {
      //Match all documents but not the nested ones
      //Nested document types start with __ 
      //https://discuss.elastic.co/t/whats-nested-documents-layout-inside-the-lucene/59944/9
      NON_NESTED_QUERY = new BooleanQuery.Builder()
      .add(new MatchAllDocsQuery(), Occur.FILTER)
      .add(new PrefixQuery(new Term("_type", "__")), Occur.MUST_NOT)
      .build();
  }
  
  private DlsQueryParser() {
      
  }
    
  static Query parse(final Set<String> unparsedDlsQueries, final QueryShardContext queryShardContext, 
          final NamedXContentRegistry namedXContentRegistry) throws IOException {
      
      if(unparsedDlsQueries == null || unparsedDlsQueries.isEmpty()) {
          return null;
      }
      
      final boolean hasNestedMapping = queryShardContext.getMapperService().hasNested();
      
      final BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
      dlsQueryBuilder.setMinimumNumberShouldMatch(1);
      
      for (final String unparsedDlsQuery : unparsedDlsQueries) {
          final XContentParser parser = JsonXContent.jsonXContent.createParser(namedXContentRegistry, unparsedDlsQuery);                
          final QueryBuilder qb = queryShardContext.parseInnerQueryBuilder(parser);
          final Query dlsQuery = queryShardContext.toQuery(qb).query();
          dlsQueryBuilder.add(dlsQuery, Occur.SHOULD);
          
          if (hasNestedMapping) {
              handleNested(queryShardContext, dlsQueryBuilder, dlsQuery);
          }  
      }

      return dlsQueryBuilder.build();
  }
  
  private static void handleNested(final QueryShardContext queryShardContext, 
          final BooleanQuery.Builder dlsQueryBuilder, 
          final Query parentQuery) {      
      final BitSetProducer parentDocumentsFilter = queryShardContext.bitsetFilter(NON_NESTED_QUERY);
      dlsQueryBuilder.add(new ToChildBlockJoinQuery(parentQuery, parentDocumentsFilter), Occur.SHOULD);
  }
    
}
