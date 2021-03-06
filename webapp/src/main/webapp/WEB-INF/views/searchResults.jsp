<%@ page pageEncoding="UTF-8" %>
<%@ include file="includes.jsp" %>
<c:set var="currentPage" value="search" />
<c:set var="head">
<title>Резултати от търсене за: ${param.keywords}</title>
</c:set>
<%@ include file="header.jsp" %>

<div align="center">
	<form action="${root}/search" method="GET">
		<input type="text" name="keywords" style="width: 400px; margin-bottom: 0px;" value="${param.keywords}" />&nbsp;&nbsp;<input type="submit" class="btn" value="${msg.search}" />
	</form>
	
	<a href="<c:url value="/alerts/new?keywords=${param.keywords}" />">${msg.createNewAlert}</a>
</div>

<table class="table table-bordered table-striped">
<thead>
<tr>
	<td style="width: 40px;">${msg.documentId}</td>
	<td style="width: 120px;">${msg.title}</td>
    <td>${msg.snippet}</td>
    <td>${msg.source}</td>
    <td>${msg.publishDate}</td>
    <td>${msg.documentLink}</td>
</tr>
</thead>
<tbody>
<c:forEach items="${results}" var="entry">
<tr id="row-${entry.id}">
	<td>${entry.externalId}</td>
	<td><div class="searchResultsTitle">${entry.title}</div></td>
	<td>${entry.content.substring(0, 150)}...</td>
	<td>${entry.sourceDisplayName}</td>
	<td><fmt:formatDate value="${entry.publishDate.toDate()}" pattern="dd.MM.yyyy" /></td>
	<td><a href="${entry.url}">${msg.open}</a></td>
</tr>
</c:forEach>
</tbody>
</table>
<%@ include file="footer.jsp" %>