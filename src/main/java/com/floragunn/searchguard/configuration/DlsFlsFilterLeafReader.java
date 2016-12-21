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

//This implementation is based on
//https://github.com/apache/lucene-solr/blob/branch_6_3/lucene/test-framework/src/java/org/apache/lucene/index/FieldFilterLeafReader.java
//https://github.com/apache/lucene-solr/blob/branch_6_3/lucene/misc/src/java/org/apache/lucene/index/PKIndexSplitter.java
//https://github.com/salyh/elasticsearch-security-plugin/blob/4b53974a43b270ae77ebe79d635e2484230c9d01/src/main/java/org/elasticsearch/plugins/security/filter/DlsWriteFilter.java

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.support.XContentMapValues;

import com.floragunn.searchguard.support.WildcardMatcher;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

class DlsFlsFilterLeafReader extends FilterLeafReader {

    private final String[] includes;
    private final FieldInfos flsFieldInfos;
    private final Bits liveDocs;
    private final int numDocs;
    private final boolean flsEnabled;
    private final boolean dlsEnabled;

    DlsFlsFilterLeafReader(final LeafReader delegate, final Set<String> includes, final Query dlsQuery) {
        super(delegate);
        
        flsEnabled = includes != null && !includes.isEmpty();
        dlsEnabled = dlsQuery != null;
        
        if(flsEnabled) {
            this.includes = includes.toArray(new String[0]);
            final FieldInfos infos = delegate.getFieldInfos();
            
            final List<FieldInfo> fi = new ArrayList<FieldInfo>(infos.size());
            for (final FieldInfo info : infos) {
                final String fname = info.name;
                if ( (!WildcardMatcher.containsWildcard(fname) && includes.contains(fname)) 
                        || WildcardMatcher.matchAny(this.includes, fname)) {
                    fi.add(info);
                }
            }
    
            this.flsFieldInfos = new FieldInfos(fi.toArray(new FieldInfo[0]));
        } else {
            this.includes = null;
            this.flsFieldInfos = null;
        }
        
        
        if(dlsEnabled) {
            try {
                
                //borrowed from Apache Lucene (Copyright Apache Software Foundation (ASF))
                final IndexSearcher searcher = new IndexSearcher(this);
                searcher.setQueryCache(null);
                final boolean needsScores = false;
                final Weight preserveWeight = searcher.createNormalizedWeight(dlsQuery, needsScores);
                
                final int maxDoc = in.maxDoc();
                final FixedBitSet bits = new FixedBitSet(maxDoc);
                final Scorer preverveScorer = preserveWeight.scorer(this.getContext());
                if (preverveScorer != null) {
                  bits.or(preverveScorer.iterator());
                }
                
                if (in.hasDeletions()) {
                    final Bits oldLiveDocs = in.getLiveDocs();
                    assert oldLiveDocs != null;
                    final DocIdSetIterator it = new BitSetIterator(bits, 0L);
                    for (int i = it.nextDoc(); i != DocIdSetIterator.NO_MORE_DOCS; i = it.nextDoc()) {
                      if (!oldLiveDocs.get(i)) {
                        bits.clear(i);
                      }
                    }
                  }

                  this.liveDocs = bits;
                  this.numDocs = bits.cardinality();
                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            this.liveDocs = null;
            this.numDocs = -1;
        }
    }

    private static class DlsFlsSubReaderWrapper extends FilterDirectoryReader.SubReaderWrapper {

        private final Set<String> includes;
        private final Query dlsQuery;

        public DlsFlsSubReaderWrapper(final Set<String> includes, final Query dlsQuery) {
            this.includes = includes;
            this.dlsQuery = dlsQuery;
        }

        @Override
        public LeafReader wrap(final LeafReader reader) {
            return new DlsFlsFilterLeafReader(reader, includes, dlsQuery);
        }

    }

    static class DlsFlsDirectoryReader extends FilterDirectoryReader {

        private final Set<String> includes;
        private final Query dlsQuery;

        public DlsFlsDirectoryReader(final DirectoryReader in, final Set<String> includes, final Query dlsQuery) throws IOException {
            super(in, new DlsFlsSubReaderWrapper(includes, dlsQuery));
            this.includes = includes;
            this.dlsQuery = dlsQuery;
        }

        @Override
        protected DirectoryReader doWrapDirectoryReader(final DirectoryReader in) throws IOException {
            return new DlsFlsDirectoryReader(in, includes, dlsQuery);
        }

        @Override
        public Object getCoreCacheKey() {
            return in.getCoreCacheKey();
        }
    }

    @Override
    public void document(final int docID, final StoredFieldVisitor visitor) throws IOException {
        if(flsEnabled) {
            in.document(docID, new FlsStoredFieldVisitor(visitor));
        } else {
            in.document(docID, visitor);
        }
    }

    private boolean isFls(final String name) {
        
        if(!flsEnabled) {
            return true;
        }
        
        return flsFieldInfos.fieldInfo(name) != null;
    }

    @Override
    public FieldInfos getFieldInfos() {
        
        if(!flsEnabled) {
            return in.getFieldInfos();
        }
        
        return flsFieldInfos;
    }

    @Override
    public Fields fields() throws IOException {
        final Fields fields = in.fields();
        
        if(!flsEnabled) {
            return fields;
        }
        
        return new Fields() {

            @Override
            public Iterator<String> iterator() {
                return Iterators.<String> filter(fields.iterator(), new Predicate<String>() {

                    @Override
                    public boolean apply(final String input) {
                        return isFls(input);
                    }
                });
            }

            @Override
            public Terms terms(final String field) throws IOException {

                if (!isFls(field)) {
                    return null;
                }

                return in.terms(field);

            }

            @Override
            public int size() {
                return flsFieldInfos.size();
            }

        };
    }

    private class FlsStoredFieldVisitor extends StoredFieldVisitor {

        private final StoredFieldVisitor delegate;

        public FlsStoredFieldVisitor(final StoredFieldVisitor delegate) {
            super();
            this.delegate = delegate;
        }

        @Override
        public void binaryField(final FieldInfo fieldInfo, final byte[] value) throws IOException {

            if (fieldInfo.name.equals("_source")) {
                final BytesReference bytesRef = new BytesArray(value);
                final Tuple<XContentType, Map<String, Object>> bytesRefTuple = XContentHelper.convertToMap(bytesRef, false);
                final Map<String, Object> filteredSource = XContentMapValues.filter(bytesRefTuple.v2(), includes, null);
                final XContentBuilder xBuilder = XContentBuilder.builder(bytesRefTuple.v1().xContent()).map(filteredSource);
                delegate.binaryField(fieldInfo, xBuilder.bytes().toBytes());
            } else {
                delegate.binaryField(fieldInfo, value);
            }
        }

        @Override
        public Status needsField(final FieldInfo fieldInfo) throws IOException {
            return isFls(fieldInfo.name) ? delegate.needsField(fieldInfo) : Status.NO;
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public void stringField(final FieldInfo fieldInfo, final byte[] value) throws IOException {
            delegate.stringField(fieldInfo, value);
        }

        @Override
        public void intField(final FieldInfo fieldInfo, final int value) throws IOException {
            delegate.intField(fieldInfo, value);
        }

        @Override
        public void longField(final FieldInfo fieldInfo, final long value) throws IOException {
            delegate.longField(fieldInfo, value);
        }

        @Override
        public void floatField(final FieldInfo fieldInfo, final float value) throws IOException {
            delegate.floatField(fieldInfo, value);
        }

        @Override
        public void doubleField(final FieldInfo fieldInfo, final double value) throws IOException {
            delegate.doubleField(fieldInfo, value);
        }

        @Override
        public boolean equals(final Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

    }

    @Override
    public Fields getTermVectors(final int docID) throws IOException {
        final Fields fields = in.getTermVectors(docID);

        if (!flsEnabled || fields == null) {
            return fields;
        }

        return new Fields() {

            @Override
            public Iterator<String> iterator() {
                return Iterators.<String> filter(fields.iterator(), new Predicate<String>() {

                    @Override
                    public boolean apply(final String input) {
                        return isFls(input);
                    }
                });
            }

            @Override
            public Terms terms(final String field) throws IOException {

                if (!isFls(field)) {
                    return null;
                }

                return in.terms(field);

            }

            @Override
            public int size() {
                return flsFieldInfos.size();
            }

        };
    }

    @Override
    public NumericDocValues getNumericDocValues(final String field) throws IOException {
        return isFls(field) ? in.getNumericDocValues(field) : null;
    }

    @Override
    public BinaryDocValues getBinaryDocValues(final String field) throws IOException {
        return isFls(field) ? in.getBinaryDocValues(field) : null;
    }

    @Override
    public SortedDocValues getSortedDocValues(final String field) throws IOException {
        return isFls(field) ? in.getSortedDocValues(field) : null;
    }

    @Override
    public SortedNumericDocValues getSortedNumericDocValues(final String field) throws IOException {
        return isFls(field) ? in.getSortedNumericDocValues(field) : null;
    }

    @Override
    public SortedSetDocValues getSortedSetDocValues(final String field) throws IOException {
        return isFls(field) ? in.getSortedSetDocValues(field) : null;
    }

    @Override
    public NumericDocValues getNormValues(final String field) throws IOException {
        return isFls(field) ? in.getNormValues(field) : null;
    }

    @Override
    public Bits getDocsWithField(final String field) throws IOException {
        return isFls(field) ? in.getDocsWithField(field) : null;
    }

    @Override
    public Object getCoreCacheKey() {
        return in.getCoreCacheKey();
    }

    @Override
    public Bits getLiveDocs() {
        
        if(dlsEnabled) {
            return liveDocs;
        }
        
        return in.getLiveDocs();
    }

    @Override
    public int numDocs() {
        
        if(dlsEnabled) {
            return numDocs;
        }
        
        return in.numDocs();
    }

}
