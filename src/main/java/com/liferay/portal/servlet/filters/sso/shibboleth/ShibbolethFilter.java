package com.liferay.portal.servlet.filters.sso.shibboleth;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.servlet.BaseFilter;
import com.liferay.portal.kernel.settings.CompanyServiceSettingsLocator;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration;
import com.liferay.portal.security.shibboleth.constants.ShibbolethConstants;
import com.liferay.portal.shibboleth.util.ShibbolethPropsKeys;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author Romeo Sheshi <rsheshi@gmail.com>
 * @author Ivan Novakov <ivan.novakov@debug.cz>
 */


@Component(
        immediate = true,
        configurationPid = "com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration",
        property = {
                "dispatcher=FORWARD", "dispatcher=REQUEST", "servlet-context-name=",
                "servlet-filter-name=SSO Shibboleth Filter", "url-pattern=/c/portal/login",
                "url-pattern=/c/portal/logout","after-filter=Auto Login Filter"
        },
        service = Filter.class
)
public class ShibbolethFilter extends BaseFilter {

    @Override
    public boolean isFilterEnabled(HttpServletRequest request, HttpServletResponse response) {
        try {
            long companyId = PortalUtil.getCompanyId(request);

            ShibbolethConfiguration configuration =
                    _configurationProvider.getConfiguration(
                            ShibbolethConfiguration.class,
                            new CompanyServiceSettingsLocator(
                                    companyId, ShibbolethConstants.SERVICE_NAME));
            if (configuration.enabled()) {
                return true;
            }
        } catch (Exception e) {
            _log.error(e, e);
        }
        return false;
    }

    @Override
    protected Log getLog() {
        return _log;
    }

    @Override
    protected void processFilter(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws Exception {

        _log.info("Shibboleth filter");
        String pathInfo = request.getPathInfo();
        HttpSession session = request.getSession();
        long companyId = PortalUtil.getCompanyId(request);
        ShibbolethConfiguration configuration =
                _configurationProvider.getConfiguration(
                        ShibbolethConfiguration.class,
                        new CompanyServiceSettingsLocator(
                                companyId, ShibbolethConstants.SERVICE_NAME));


        if (pathInfo.contains("/portal/logout")) {
            if (configuration.logoutEnabled()) {
                session.invalidate();
                String logoutUrl =configuration.logoutUrl();
                response.sendRedirect(logoutUrl);
                return;
            }
        } else {
            extractData(session, companyId, request);
        }
        processFilter(ShibbolethFilter.class.getCanonicalName(), request, response, filterChain);
    }

    /**
     * Extracts user data from AJP or HTTP header
     *
     * @return true if any data is present
     */
    protected boolean extractData(HttpSession session, long companyId, HttpServletRequest request) throws Exception {
        String login = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_LOGIN);
        ShibbolethConfiguration configuration =
                _configurationProvider.getConfiguration(
                        ShibbolethConfiguration.class,
                        new CompanyServiceSettingsLocator(
                                companyId, ShibbolethConstants.SERVICE_NAME));
        if (Validator.isNull(login)) {

            boolean headersEnabled =configuration.headersEnabled();

            if (headersEnabled) {
                _log.info("Using HTTP headers as source for attribute values");
            } else {
                _log.info("Using Environment variables as source for attribute values");
            }

            String aaiProvidedLoginName = getHeader(configuration.userHeader(), request, headersEnabled);

            String aaiProvidedEmail = getHeader(configuration.userEmailHeader(), request, headersEnabled);

            String aaiProvidedFirstname = getHeader(configuration.firstname(), request, headersEnabled);

            String aaiProvidedSurname = getHeader(configuration.surname(), request, headersEnabled);

            String aaiProvidedAffiliation = getHeader(configuration.userHeaderAffiliation(), request, headersEnabled);


            if (Validator.isNull(aaiProvidedLoginName)) {
                _log.error("Required header [" + configuration.userHeader() + "] not found");
                _log.error("AAI authentication failed as login name header is empty.");
                return false;
            }

            if ( configuration.screenameTransformEnabled()) {
                _log.info("ScreenName transform is enabled.");
                //check validity of screen name 

                // most probably it is an eduPersonPrincipalName. Make transformations
                if(aaiProvidedLoginName.contains("@")){
                    _log.info("The login name provided by AAI looks like an "
                            + "email (or eduPersonPrincipalName): "
                            + aaiProvidedLoginName
                            + " It needs to be converted to be a Liferay screen name.");
                    aaiProvidedLoginName = aaiProvidedLoginName.replaceAll("@", ".at.");
                    _log.info("Login name is converted to:" + aaiProvidedLoginName);
                } else _log.info("error");
                //Liferay does not like underscores
                if (aaiProvidedLoginName.contains("_")) {
                    _log.info("The login name provided by AAI contains underscores:"
                            + aaiProvidedLoginName
                            + "It needs to be converted to be a Liferay screen name.");
                    aaiProvidedLoginName = aaiProvidedLoginName.replaceAll("_", "-");
                    _log.info("Login name is converted to:" + aaiProvidedLoginName);
                }
            }
            else {
                _log.info("ScreenName transform is disabled.");
            }

            _log.info("AAI-provided screen name is:" + aaiProvidedLoginName);
            session.setAttribute(ShibbolethPropsKeys.SHIBBOLETH_LOGIN, aaiProvidedLoginName);

            //get the first of multi-valued email address
            if (aaiProvidedEmail.contains(";")) {
                _log.info("The email address string provided by AAI is multi-valued:"
                        + aaiProvidedEmail
                        + " Using the first value.");
                String[] emails = aaiProvidedEmail.split(";");
                aaiProvidedEmail = emails[0];
            }
            _log.info("AAI-provided email is:" + aaiProvidedEmail);
            session.setAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_EMAIL, aaiProvidedEmail);

            if (Validator.isNull(aaiProvidedFirstname)) {
                _log.error("No First name provided in: "
                        + configuration.firstname()
                        + " using a default value instead.");
                aaiProvidedFirstname = "MissingFirstName";
            }
            _log.info("AAI-provided first name is:" + aaiProvidedFirstname);
            session.setAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_FIRSTNAME, aaiProvidedFirstname);

            if (Validator.isNull(aaiProvidedSurname)) {
                _log.error("No Surname provided in: "
                        + configuration.surname()
                        + " using a default value instead.");
                aaiProvidedSurname = "MissingSurname";
            }
            _log.info("AAI-provided Surname is:" + aaiProvidedSurname);
            session.setAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_SURNAME, aaiProvidedSurname);

            if (Validator.isNull(aaiProvidedAffiliation)) {
                _log.debug("No affiliation provided");
                aaiProvidedAffiliation = "";
            }
            if (configuration.affiliationTruncateEnabled() && aaiProvidedAffiliation.contains(":")) {
                _log.info("affiliation contains ':' characters: "
                        + aaiProvidedAffiliation
                        + " assuming eduPersonEntitlement format");
                // AAI-provided affiliation is multi-valued
                if (aaiProvidedAffiliation.contains(";")) {
                    _log.info("AAI-provided affiliation is multi-valued:"
                            + aaiProvidedAffiliation
                            + " Processing each vale");
                    String[] affiliations = aaiProvidedAffiliation.split(";");
                    aaiProvidedAffiliation = "";

                    for (int i = 0; i < affiliations.length; i++) {
                        String[] parts = affiliations[i].split(":");
                        aaiProvidedAffiliation += parts[parts.length - 1];
                        if (i < affiliations.length - 1) {
                            aaiProvidedAffiliation += ";";
                        }
                    }

                } else {
                    String[] parts = aaiProvidedAffiliation.split(":");
                    aaiProvidedAffiliation = parts[parts.length - 1];
                }
            }
            _log.info("AAI-provided affiliation is:" + aaiProvidedAffiliation);
            session.setAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_AFFILIATION, aaiProvidedAffiliation);

            return true;
        } else {
            return false;
        }
    }

    protected String getHeader(String headerName, HttpServletRequest request, boolean headersEnabled) {
        if (Validator.isNull(headerName)) {
            return null;
        }
        String headerValue;

        if (headersEnabled) {
            headerValue = request.getHeader(headerName);
        } else {
            headerValue = (String) request.getAttribute(headerName);
        }

        _log.info("Header [" + headerName + "]: " + headerValue);

        return headerValue;
    }

    @Reference(unbind = "-")
    protected void setConfigurationProvider(
            ConfigurationProvider configurationProvider) {

        _configurationProvider = configurationProvider;
    }

    private ConfigurationProvider _configurationProvider;
    private static final Log _log = LogFactoryUtil.getLog(ShibbolethFilter.class);

}
