package bg.statealerts.services.extractors

import com.gargoylesoftware.htmlunit.html.HtmlElement
import bg.statealerts.model.Document
import java.util.regex.Pattern

class TableContentExtractor extends DocumentDetailsExtractor {

  def populateDocument(doc: Document, row: HtmlElement, rowIdx: Int, ctx: ExtractionContext) = {

    // if there is more than one element that matches the XPath, use the rowIdx (some tables do not have proper row separation, so the idx may be needed)
    if (ctx.descriptor.datePath.nonEmpty) {
      val elements = row.getByXPath(ctx.descriptor.datePath.get)
      if (elements.size() == 0) {
        throw new IllegalStateException("Cannot find date element for xpath " + ctx.descriptor.datePath)
      }
      val element = if (elements.size() > 1) elements.get(rowIdx) else elements.get(0)
      var text = element.asInstanceOf[HtmlElement].getTextContent().trim()
      if (ctx.descriptor.dateRegex.nonEmpty) {
        val pattern = Pattern.compile(ctx.descriptor.dateRegex.get)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
          text = matcher.group()
        }
      }
      doc.publishDate = ctx.dateFormatter.parseDateTime(text)
    }

    if (ctx.descriptor.titlePath.nonEmpty) {
      val elements = row.getByXPath(ctx.descriptor.titlePath.get)
      if (elements.size() == 0) {
        throw new IllegalStateException("Cannot find title element for xpath " + ctx.descriptor.titlePath)
      }
      val element = if (elements.size() > 1) elements.get(rowIdx) else elements.get(0)
      doc.title = element.asInstanceOf[HtmlElement].getTextContent().trim()
    }
    if (ctx.descriptor.externalIdPath.nonEmpty) {
      val elements = row.getByXPath(ctx.descriptor.externalIdPath.get)
      if (elements.size() == 0) {
        throw new IllegalStateException("Cannot find externalId element for xpath " + ctx.descriptor.externalIdPath)
      }
      val element = if (elements.size() > 1) elements.get(rowIdx) else elements.get(0)
      doc.externalId = element.asInstanceOf[HtmlElement].getTextContent().trim()
    }
    //TODO use rowIdx
    if (ctx.descriptor.contentLocationType == ContentLocationType.Table && ctx.descriptor.contentPath.nonEmpty) {
    	doc.content = row.getFirstByXPath(ctx.descriptor.contentPath.get).asInstanceOf[HtmlElement].getTextContent().trim()
    }
  }
}