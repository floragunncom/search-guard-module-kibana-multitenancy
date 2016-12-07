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
import org.elasticsearch.index.query.IndexQueryParserService;

final class DlsQueryParser {
  
  private DlsQueryParser() {
      
  }
    
  static Query parse(final Set<String> unparsedDlsQueries, final IndexQueryParserService parser) throws IOException {
      
      if(unparsedDlsQueries == null || unparsedDlsQueries.isEmpty()) {
          return null;
      }
      
      BooleanQuery.Builder dlsQueryBuilder = new BooleanQuery.Builder();
      dlsQueryBuilder.setMinimumNumberShouldMatch(1);
      
      for (final String unparsedDlsQuery : unparsedDlsQueries) {
          dlsQueryBuilder.add(parser.parse(unparsedDlsQuery).query(), Occur.SHOULD);
      }

      return dlsQueryBuilder.build();
  }
    
}
