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
import java.util.Set;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.ParsedQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryShardContext;

final class DlsQueryParser {
  
  private DlsQueryParser() {
      
  }
    
  static Query parse(final Set<String> unparsedDlsQueries, final QueryShardContext queryShardContext) throws IOException {
      
      if(unparsedDlsQueries == null || unparsedDlsQueries.isEmpty()) {
          return null;
      }
      
      BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
      dlsQueryBuilder.setMinimumNumberShouldMatch(1);
      
      for (final String unparsedDlsQuery : unparsedDlsQueries) {
          XContentParser parser = XContentFactory.xContent(unparsedDlsQuery).createParser(unparsedDlsQuery);                
          QueryBuilder qb = queryShardContext.newParseContext(parser).parseInnerQueryBuilder().get();
          ParsedQuery parsedQuery = queryShardContext.toQuery(qb);
          dlsQueryBuilder.add(parsedQuery.query(), Occur.SHOULD);
      }

      return dlsQueryBuilder.build();
  }
    
}
