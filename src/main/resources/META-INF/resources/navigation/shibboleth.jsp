
<%@ include file="/html/portlet/login/init.jsp"%>
<%@ page import="com.liferay.portal.kernel.module.configuration.ConfigurationProviderUtil" %>
<%@ page import="com.liferay.portal.kernel.settings.CompanyServiceSettingsLocator" %>
<%@ page import="com.liferay.portal.kernel.settings.ParameterMapSettingsLocator" %>
<%@ page import="com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration" %>
<%@ page import="com.liferay.portal.security.shibboleth.constants.ShibbolethConstants" %>

<%

	ShibbolethConfiguration shibbolethConfiguration = ConfigurationProviderUtil.getConfiguration(CASConfiguration.class, new ParameterMapSettingsLocator(request.getParameterMap(), "shibboleth--", new CompanyServiceSettingsLocator(company.getCompanyId(), ShibbolethConstants.SERVICE_NAME)));


	boolean shibbolethEnabled = shibbolethConfiguration.enabled();
    String shibbolethLoginUrl = shibbolethConfiguration.loginUrl();
%>

<c:if test="<%=shibbolethEnabled%>">

	<liferay-ui:icon
			iconCssClass="icon-user"
		url="<%= shibbolethLoginUrl %>" message="shibboleth" />

</c:if>
