package com.liferay.portal.security.shibboleth.internal.servlet.taglib;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.servlet.taglib.BaseDynamicInclude;
import com.liferay.portal.kernel.servlet.taglib.DynamicInclude;
import com.liferay.portal.kernel.settings.CompanyServiceSettingsLocator;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration;
import com.liferay.portal.security.shibboleth.constants.ShibbolethConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * User: Romeo Sheshi <a href="mailto:rsheshi@gmail.com">Romeo Sheshi</a>
 * Date: 27/10/2016
 * Time: 17:12
 */
@Component(immediate = true, service = DynamicInclude.class)
public class ShibbolethNavigationPreDynamicInclude extends BaseDynamicInclude {

    @Override
    public void include(
            HttpServletRequest request, HttpServletResponse response,
            String key)
            throws IOException {
        _log.error("Shibboleth dynamic include");
        ThemeDisplay themeDisplay = (ThemeDisplay)request.getAttribute(
                WebKeys.THEME_DISPLAY);

        try {

            ShibbolethConfiguration shibbolethConfiguration = getShibbolethConfiguration(themeDisplay.getCompanyId());

            String loginUrl = shibbolethConfiguration.loginUrl();

        request.setAttribute("shibbolethLoginUrl",loginUrl);



        RequestDispatcher requestDispatcher =
                _servletContext.getRequestDispatcher(_JSP_PATH);


            requestDispatcher.include(request, response);
        } catch (ServletException se) {
            _log.error("Unable to include JSP " + _JSP_PATH, se);

            throw new IOException("Unable to include JSP " + _JSP_PATH, se);
        } catch (ConfigurationException e) {
            throw new IOException("Unable to load configuration", e);
        }
    }

    @Override
    public void register(
            DynamicIncludeRegistry dynamicIncludeRegistry) {
        _log.error("Shibboleth dynamic include register");

        dynamicIncludeRegistry.register(
                "com.liferay.login.web#/navigation.jsp#pre");
    }


    @Reference(
            target = "(osgi.web.symbolicname=liferay-shibboleth-plugin)",
            unbind = "-"
    )
    protected void setServletContext(ServletContext servletContext) {
        _servletContext = servletContext;
    }

    @Reference(unbind = "-")
    protected void setConfigurationProvider(
            ConfigurationProvider configurationProvider) {

        _configurationProvider = configurationProvider;
    }

    private ShibbolethConfiguration getShibbolethConfiguration(long companyId) throws ConfigurationException {
        return _configurationProvider.getConfiguration(
                ShibbolethConfiguration.class,
                new CompanyServiceSettingsLocator(
                        companyId, ShibbolethConstants.SERVICE_NAME));
    }
    private ConfigurationProvider _configurationProvider;
    private static final String _JSP_PATH =
            "/html/portlet/login/navigation/shibboleth.jsp";

    private static final Log _log = LogFactoryUtil.getLog(
            ShibbolethNavigationPreDynamicInclude.class);

    private ServletContext _servletContext;


}
