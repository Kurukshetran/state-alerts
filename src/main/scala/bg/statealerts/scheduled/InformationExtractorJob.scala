package bg.statealerts.scheduled

import java.io.File
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import com.codahale.jerkson.Json
import bg.statealerts.dao.BaseDao
import bg.statealerts.model.Document
import bg.statealerts.model.Import
import bg.statealerts.services.DocumentService
import bg.statealerts.services.Indexer
import bg.statealerts.services.extractors.Extractor
import javax.annotation.PostConstruct
import javax.inject.Inject
import java.util.Random
import bg.statealerts.services.extractors.ContentLocationType

@Component
class InformationExtractorJob {

  var extractors: List[Extractor] = List()

  @Value("${statealerts.config.location}")
  var configLocation: String = _

  @Value("${random.sleep.max.minutes}")
  var randomSleepMaxMinutes: Int = 0

  @Inject
  var service: DocumentService = _

  @Inject
  var dao: BaseDao = _

  @Inject
  var indexer: Indexer = _

  val random = new Random()

  @PostConstruct
  def init() {
    var file: File = new File(configLocation + "/extractors.json")
    var config: ExtractorConfiguration = Json.parse[ExtractorConfiguration](file)
    for (descriptor <- config.extractors) {
      validateDescriptor(descriptor) // the application fails on startup if a configuration is invalid
      var extractor = new Extractor(descriptor)
      extractors ::= extractor
    }
  }

  @Scheduled(fixedRate = 100000)
  def run() {
    if (randomSleepMaxMinutes > 0) {
      // sleep a random amount of time before running the extraction, so that it is not completely obvious to website admins it's a scraping process
      Thread.sleep(random.nextInt(randomSleepMaxMinutes * 60) * 1000)
    }
    var lastImportTime = dao.getLastImportDate();
    if (lastImportTime == null) {
      lastImportTime = new DateTime().minusDays(14)
    }
    val now = new DateTime();
    var total: Int = 0
    for (extractor <- extractors) {
      val documents: List[Document] = extractor.extract(lastImportTime)
      var persistedDocuments = List[Document]()
      total += documents.size
      for (document <- documents) {
        persistedDocuments ::= service.save(document)
      }
      //TODO more effort to keep in sync with db
      indexer.index(persistedDocuments)
    }

    if (total > 0) {
      val docImport = new Import()
      docImport.importedDocuments = total;
      docImport.importTime = now
      service.save(docImport)
    }
  }

  def validateDescriptor(descriptor: ExtractorDescriptor) {
    val contentLocationType = ContentLocationType.withName(descriptor.contentLocationType)
    if (contentLocationType == ContentLocationType.Table && (!descriptor.titlePath.nonEmpty || !descriptor.datePath.nonEmpty)) {
      throw new IllegalStateException("Required extractor configuration parameters are not present for " + descriptor.sourceName + ". For contentLocationType=Table, 'titlePath' and 'datePath' are required")
    }
    if (contentLocationType == ContentLocationType.LinkedDocumentInTable && !descriptor.documentLinkPath.nonEmpty) {
      throw new IllegalStateException("Required extractor configuration parameters are not present for " + descriptor.sourceName + ". For contentLocationType=LinkedDocumentInTable, 'documentLinkPath' is required")
    }
    if (contentLocationType == ContentLocationType.LinkedDocumentOnLinkedPage && (!descriptor.documentLinkPath.nonEmpty || !descriptor.documentPageLinkPath.nonEmpty)) {
      throw new IllegalStateException("Required extractor configuration parameters are not present for " + descriptor.sourceName + ". For contentLocationType=LinkedDocumentOnLinkedPage, 'documentLinkPath' and 'documentPageLinkPath' are required")
    }

    if (contentLocationType == ContentLocationType.LinkedDocumentOnLinkedPage || contentLocationType == ContentLocationType.LinkedPage) {
      if (!descriptor.titlePath.nonEmpty && !descriptor.documentPageTitlePath.nonEmpty) {
        throw new IllegalStateException("Extractor " + descriptor.sourceName + " must have either 'titlePath' or 'documentPageTitlePath'")
      }
      if (!descriptor.datePath.nonEmpty && !descriptor.documentPageDatePath.nonEmpty) {
        throw new IllegalStateException("Extractor " + descriptor.sourceName + " must have either 'datePath' or 'documentPageDatePath'")
      }
    }
  }
}