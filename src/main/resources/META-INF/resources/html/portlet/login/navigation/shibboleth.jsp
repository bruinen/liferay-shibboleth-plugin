
<%@ include file="/html/portlet/login/navigation/init.jsp" %>

<%
    String shibbolethLoginUrl = (String)request.getAttribute("shibbolethLoginUrl");
%>


	<liferay-ui:icon
			iconCssClass="icon-user"
		url="<%= shibbolethLoginUrl %>" message="shibboleth" />

