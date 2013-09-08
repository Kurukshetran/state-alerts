package bg.statealerts.services

import java.io.File
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.flexible.standard.QueryParserUtil
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.NumericRangeQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.SearcherFactory
import org.apache.lucene.search.SearcherManager
import org.apache.lucene.search.Sort
import org.apache.lucene.search.SortField
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.DependsOn
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import bg.statealerts.dao.DocumentDao
import bg.statealerts.model.Document
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import javax.inject.Inject
import org.apache.lucene.index.ReaderManager
import org.apache.lucene.search.IndexSearcher
import org.joda.time.Interval

@Service
@DependsOn(Array("indexer")) // indexer initializes index
class SearchService {

  var analyzer: Analyzer = _

  @Inject
  var documentDao: DocumentDao = _
  
  @Value("${index.path}")
  var indexPath: String = _
  @Value("${lucene.analyzer.class}")
  var analyzerClass: String = _
  
  var readerManager: ReaderManager = _
  
  @PostConstruct
  def init() = {
    readerManager = new ReaderManager(FSDirectory.open(new File(indexPath)))
    analyzer = Class.forName(analyzerClass).getConstructor(classOf[Version]).newInstance(Version.LUCENE_43).asInstanceOf[Analyzer]
  }

  @PreDestroy
  def destroy() = {
    readerManager.close()
  }
  
  @Transactional(readOnly=true)
  def search(keywords: String): List[Document] = {
    val escapedKeywords = QueryParserUtil.escape(keywords)
    val q = new TermQuery(new Term("text", escapedKeywords))
    
    getDocuments(q, 50)
  }

  @Transactional(readOnly=true)
  def search(keywords: String, interval: Interval): List[Document] = {

    val escapedKeywords = QueryParserUtil.escape(keywords)
    val textQuery = new TermQuery(new Term("text", escapedKeywords))
    val timestampQuery = NumericRangeQuery.newLongRange("indexTimestamp", interval.getStartMillis(), interval.getEndMillis(), true, true)
    val query = new BooleanQuery()
    query.add(textQuery, BooleanClause.Occur.MUST)
    query.add(timestampQuery, BooleanClause.Occur.MUST)

    getDocuments(query, 50)
  }

  private def getDocuments(query: Query, limit: Int): List[Document] = {
    val reader = readerManager.acquire()
    
    try {
    	val searcher = new IndexSearcher(reader)
	    val sort = new Sort(new SortField("publishTimestamp", SortField.Type.LONG, true))
	    val result: TopDocs = searcher.search(query, null, limit, sort)
	
	    var documents = List[Document]()
	
	    val topDocs: Array[ScoreDoc] = result.scoreDocs
	    
	    for (topDoc <- topDocs) {
	      val luceneDoc = searcher.doc(topDoc.doc)
	      val doc = documentDao.get(classOf[Document], luceneDoc.get("id").toInt)
	      documents ::= doc
	    }
	    documents
    } finally {
      readerManager.release(reader)
    }
  }
  
  @Scheduled(fixedRate = 600000) // 10 minutes
  def refreshReaderManager(): Unit = {
    readerManager.maybeRefresh()
  }
}