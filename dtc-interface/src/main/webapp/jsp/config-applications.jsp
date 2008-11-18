<%@ page language="java" contentType="text/html" %>

<html>

  <jsp:include page="snippets/head.jsp" flush="true"/>

  <body id="page">
    <jsp:include page="snippets/banner.jsp" flush="true"/>
    <jsp:include page="snippets/crumb.jsp" flush="true"/>

    <div id="content">
      <div id="leftContainer">
	
      </div>
      
      <div id="rightContainer">
	<div class="box">
	  <div class="boxHeader">
	    Server Applications
	  </div>
	  <div class="boxContent">
	    <a href="<%= request.getContextPath() %>/config/applications">&gt; Applications</a><br/>
	    <a href="<%= request.getContextPath() %>/config/resources">&gt; Resources</a><br/>
	    <a href="<%= request.getContextPath() %>/config/test-specs">&gt; Test Specifications</a><br/>
	    <a href="<%= request.getContextPath() %>/config/test-suites">&gt; Test Suites</a><br/>
	  </div>
	</div>	  
      </div>
    </div>

    <jsp:include page="snippets/foot.jsp" flush="true"/>
  </body>
  
</html>
