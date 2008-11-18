<%@ page language="java" contentType="text/html" %>

<html>

  <jsp:include page="snippets/head.jsp" flush="true"/>

  <body id="page">
    <jsp:include page="snippets/banner.jsp" flush="true"/>
    <jsp:include page="snippets/crumb.jsp" flush="true"/>

    <div id="content">
      <div id="fullContainer">
	<div class="box">
	  <div class="boxHeader">
	    Server Applications
	  </div>
	  <div class="boxContent">

	  </div>
	</div>	  
	<div class="box">
	  <div class="boxHeader">
	    Client Applications
	  </div>
	  <div class="boxContent">

	  </div>
	</div>	  
	<div class="box">
	  <div class="boxHeader">
	    System Probes
	  </div>
	  <div class="boxContent">

	  </div>
	</div>
      </div>
    </div>

    <jsp:include page="snippets/foot.jsp" flush="true"/>
  </body>
  
</html>
