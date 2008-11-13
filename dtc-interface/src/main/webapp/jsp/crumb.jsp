<%@ page language="java" contentType="text/html" %>
<%@ page import="java.util.List" %>
<%@ page import="com.projectdarkstar.tools.dtc.util.Caster" %>
<% List<String> trail = Caster.cast(request.getAttribute("trail")); %>

<div id="crumb">
<% if(trail != null) {
     for(int i = 0; i < trail.size()-1; i+=2) {
%>
	<a href="<%= trail.get(i+1) %>"><%= trail.get(i) %></a>
<%	if(i+2 < trail.size() - 1) {
%>
	  &gt;&gt;
<%	}
     }
   }
%>
</div>
