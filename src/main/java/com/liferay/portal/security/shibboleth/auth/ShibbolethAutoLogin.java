package com.liferay.portal.security.shibboleth.auth;

import com.liferay.portal.kernel.exception.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.model.Role;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.module.configuration.ConfigurationException;
import com.liferay.portal.kernel.module.configuration.ConfigurationProvider;
import com.liferay.portal.kernel.security.auto.login.AutoLogin;
import com.liferay.portal.kernel.security.auto.login.AutoLoginException;
import com.liferay.portal.kernel.service.RoleLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.settings.CompanyServiceSettingsLocator;
import com.liferay.portal.kernel.util.*;
import com.liferay.portal.security.exportimport.UserImporter;
import com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration;
import com.liferay.portal.security.shibboleth.constants.ShibbolethConstants;
import com.liferay.portal.shibboleth.util.ShibbolethPropsKeys;
import com.liferay.portal.util.PropsValues;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * Performs autologin based on the header values passed by Shibboleth.
 * <p/>
 * The Shibboleth user ID header set in the configuration must contain the user
 * ID, if users are authenticated by screen name or the user email, if the users
 * are authenticated by email (Portal settings --> Authentication --> General).
 *
 * @author Romeo Sheshi <rsheshi@gmail.com>
 * @author Ivan Novakov <ivan.novakov@debug.cz>
 */
@Component(immediate = true,
           configurationPid = "com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration",
           service = AutoLogin.class)
public class ShibbolethAutoLogin implements AutoLogin {


    private static Log _log = LogFactoryUtil.getLog(ShibbolethAutoLogin.class);


    private UserLocalService userLocalService;
    private RoleLocalService roleLocalService;


    @Override
    public String[] handleException(HttpServletRequest request, HttpServletResponse response, Exception e) throws AutoLoginException {
        // taken from BaseAutoLogin
        if (Validator.isNull(request.getAttribute(AutoLogin.AUTO_LOGIN_REDIRECT))) {
            throw new AutoLoginException(e);
        }
        _log.error(e, e);
        return null;
    }

    @Override
    public String[] login(HttpServletRequest req, HttpServletResponse res) throws AutoLoginException {

        User user;
        String[] credentials = null;
        HttpSession session = req.getSession(false);
        long companyId = PortalUtil.getCompanyId(req);

        try {
            ShibbolethConfiguration configuration = getShibbolethConfiguration(companyId);
            _log.info("Shibboleth Autologin [modified 2]");

            if (!configuration.enabled()) {
                return credentials;
            }

            user = loginFromSession(companyId, session);
            if (Validator.isNull(user)) {
                return credentials;
            }

            credentials = new String[3];
            credentials[0] = String.valueOf(user.getUserId());
            credentials[1] = user.getPassword();
            credentials[2] = Boolean.TRUE.toString();
            return credentials;

        } catch (NoSuchUserException e) {
            logError(e);
        } catch (Exception e) {
            logError(e);
            throw new AutoLoginException(e);
        }

        return credentials;
    }

    private User loginFromSession(long companyId, HttpSession session) throws Exception {
        String login;
        User user = null;

        login = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_LOGIN);
        if (Validator.isNull(login)) {
            return null;
        }
        ShibbolethConfiguration configuration = getShibbolethConfiguration(companyId);
        String authType = PrefsPropsUtil.getString(
                companyId, PropsKeys.COMPANY_SECURITY_AUTH_TYPE,
                PropsValues.COMPANY_SECURITY_AUTH_TYPE);

        try {
            if (authType.equals(CompanyConstants.AUTH_TYPE_SN)) {
                _log.info("Trying to find user with screen name: " + login);
                user = userLocalService.getUserByScreenName(companyId, login);
            } else if (authType.equals(CompanyConstants.AUTH_TYPE_EA)) {

                String emailAddress = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_EMAIL);
                if (Validator.isNull(emailAddress)) {
                    return null;
                }

                _log.info("Trying to find user with email: " + emailAddress);
                user = userLocalService.getUserByEmailAddress(companyId, emailAddress);
            } else {
                throw new NoSuchUserException();
            }

            _log.info("User found: " + user.getScreenName() + " (" + user.getEmailAddress() + ")");

            if (configuration.autoUpdateUsers()) {
                _log.info("Auto-updating user...");
                updateUserFromSession(user, session);
            }

        } catch (NoSuchUserException e) {
            _log.error("User " + login + " not found");

            if (configuration.autoCreateUsers()) {
                _log.info("Importing user from session...");
                user = createUserFromSession(companyId, session);
                _log.info("Created user with ID: " + user.getUserId());
            } else if (configuration.importFromLDAP()) {
                _log.info("Importing user from LDAP...");
                try {
                    if (authType.equals(CompanyConstants.AUTH_TYPE_SN)) {
                        user = userImporter.importUser(
                                companyId, StringPool.BLANK, login);
                    }
                    else {
                        user = userImporter.importUser(
                                companyId, login, StringPool.BLANK);
                    }
                }
                catch (SystemException se) {
                    _log.error("Exception while importing user from ldap: " + se.getMessage());
                }
            }
        }

        try {
            updateUserRolesFromSession(companyId, user, session,configuration);
        } catch (Exception e) {
            _log.error("Exception while updating user roles from session: " + e.getMessage());
        }

        return user;
    }

    private ShibbolethConfiguration getShibbolethConfiguration(long companyId) throws ConfigurationException {
        return _configurationProvider.getConfiguration(
                ShibbolethConfiguration.class,
                new CompanyServiceSettingsLocator(
                        companyId, ShibbolethConstants.SERVICE_NAME));
    }

    /**
     * Create user from session
     */
    protected User createUserFromSession(long companyId, HttpSession session) throws Exception {
        User user = null;

        String screenName = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_LOGIN);
        if (Validator.isNull(screenName)) {
            _log.error("Cannot create user - missing screen name");
            return user;
        }

        String emailAddress = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_EMAIL);
        if (Validator.isNull(emailAddress)) {
            _log.error("Cannot create user - missing email");
            return user;
        }

        String firstname = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_FIRSTNAME);
        if (Validator.isNull(firstname)) {
            _log.error("Cannot create user - missing firstname");
            return user;
        }

        String surname = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_SURNAME);
        if (Validator.isNull(surname)) {
            _log.error("Cannot create user - missing surname");
            return user;
        }

        _log.info("Creating user: screen name = [" + screenName + "], emailAddress = [" + emailAddress
                + "], first name = [" + firstname + "], surname = [" + surname + "]");

        return addUser(companyId, screenName, emailAddress, firstname, surname);
    }

    /**
     * Store user
     */
    private User addUser(long companyId, String screenName, String emailAddress, String firstName, String lastName)
            throws Exception {

        long creatorUserId = 0;
        boolean autoPassword = true;
        String password1 = null;
        String password2 = null;
        boolean autoScreenName = false;
        long facebookId = 0;
        String openId = StringPool.BLANK;
        Locale locale = Locale.US;
        String middleName = StringPool.BLANK;
        int prefixId = 0;
        int suffixId = 0;
        boolean male = true;
        int birthdayMonth = Calendar.JANUARY;
        int birthdayDay = 1;
        int birthdayYear = 1970;
        String jobTitle = StringPool.BLANK;

        long[] groupIds = null;
        long[] organizationIds = null;
        long[] roleIds = null;
        long[] userGroupIds = null;

        boolean sendEmail = false;
        ServiceContext serviceContext = null;

        return userLocalService.addUser(creatorUserId, companyId, autoPassword, password1, password2,
                autoScreenName, screenName, emailAddress, facebookId, openId, locale, firstName, middleName, lastName,
                prefixId, suffixId, male, birthdayMonth, birthdayDay, birthdayYear, jobTitle, groupIds,
                organizationIds, roleIds, userGroupIds, sendEmail, serviceContext);
    }

    protected void updateUserFromSession(User user, HttpSession session) throws Exception {
        boolean modified = false;

        String emailAddress = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_EMAIL);
        if (Validator.isNotNull(emailAddress) && !user.getEmailAddress().equals(emailAddress)) {
            _log.info("User [" + user.getScreenName() + "]: update email address [" + user.getEmailAddress()
                    + "] --> [" + emailAddress + "]");
            user.setEmailAddress(emailAddress);
            modified = true;
        }

        String firstname = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_FIRSTNAME);
        if (Validator.isNotNull(firstname) && !user.getFirstName().equals(firstname)) {
            _log.info("User [" + user.getScreenName() + "]: update first name [" + user.getFirstName() + "] --> ["
                    + firstname + "]");
            user.setFirstName(firstname);
            modified = true;
        }

        String surname = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_SURNAME);
        if (Validator.isNotNull(surname) && !user.getLastName().equals(surname)) {
            _log.info("User [" + user.getScreenName() + "]: update last name [" + user.getLastName() + "] --> ["
                    + surname + "]");
            user.setLastName(surname);
            modified = true;
        }

        if (modified) {
            userLocalService.updateUser(user);
        }
    }

    private void updateUserRolesFromSession(long companyId, User user, HttpSession session, ShibbolethConfiguration configuration) throws Exception {

        if (!configuration.autoAssignUserRole()) {
            return;
        }

        List<Role> currentFelRoles = getRolesFromSession(companyId, session,configuration);
        long[] currentFelRoleIds = roleListToLongArray(currentFelRoles);

        List<Role> felRoles = getAllRolesWithConfiguredSubtype(configuration);
        long[] felRoleIds = roleListToLongArray(felRoles);

        roleLocalService.unsetUserRoles(user.getUserId(), felRoleIds);
        roleLocalService.addUserRoles(user.getUserId(), currentFelRoleIds);

        _log.info("User '" + user.getScreenName() + "' has been assigned " + currentFelRoleIds.length + " role(s): "
                + Arrays.toString(currentFelRoleIds));
    }

    private long[] roleListToLongArray(List<Role> roles) {
        long[] roleIds = new long[roles.size()];

        for (int i = 0; i < roles.size(); i++) {
            roleIds[i] = roles.get(i).getRoleId();
        }

        return roleIds;
    }

    private List<Role> getAllRolesWithConfiguredSubtype(ShibbolethConfiguration configuration) throws Exception {
        String roleSubtype = configuration.autoAssignUserRoleSubType();
        return roleLocalService.getSubtypeRoles(roleSubtype);
    }

    private List<Role> getRolesFromSession(long companyId, HttpSession session,ShibbolethConfiguration configuration) throws SystemException {
        List<Role> currentFelRoles = new ArrayList<Role>();
        String affiliation = (String) session.getAttribute(ShibbolethPropsKeys.SHIBBOLETH_HEADER_AFFILIATION);

        if (Validator.isNull(affiliation)) {
            return currentFelRoles;
        }

        String[] affiliationList = affiliation.split(";");
        for (String roleName : affiliationList) {
            Role role;
            try {
                role = roleLocalService.getRole(companyId, roleName);
            } catch (PortalException e) {
                _log.info("Exception while getting role with name '" + roleName + "': " + e.getMessage());
                try {
                    if (configuration.autoCreateRole()) {
                        List<Role> roleList = roleLocalService.getRoles(companyId);
                        long[] roleIds = roleListToLongArray(roleList);
                        Arrays.sort(roleIds);
                        long newId = roleIds[roleIds.length - 1];
                        newId = newId + 1;
                        role = roleLocalService.createRole(newId);

                        long classNameId = 0;
                        try {
                            classNameId = roleLocalService.getRole(roleIds[roleIds.length - 1]).getClassNameId();
                        } catch (PortalException ex) {
                            _log.info("classname error");
                        }
                        role.setClassNameId(classNameId);
                        role.setCompanyId(companyId);
                        role.setClassPK(newId);
                        role.setDescription(null);
                        role.setTitleMap(null);
                        role.setName(roleName);
                        role.setType(1);
                        roleLocalService.addRole(role);
                    } else continue;
                } catch (Exception exc) {
                    continue;
                }
            }

            currentFelRoles.add(role);
        }

        return currentFelRoles;
    }

    private void logError(Exception e) {
        _log.error("Exception message = " + e.getMessage() + " cause = " + e.getCause());
        if (_log.isDebugEnabled()) {
            _log.error(e);
        }

    }
    @Reference(unbind = "-")
    protected void setUserImporter(UserImporter userImporter) {
        this.userImporter = userImporter;
    }

    @Reference(unbind = "-")
    protected void setUserLocalService(UserLocalService userLocalService) {
        this.userLocalService = userLocalService;
    }


    @Reference(unbind = "-")
    protected void setRoleLocalService(RoleLocalService roleLocalService) {
        this.roleLocalService = roleLocalService;
    }


    @Reference(unbind = "-")
    protected void setConfigurationProvider(
            ConfigurationProvider configurationProvider) {

        _configurationProvider = configurationProvider;
    }

    private ConfigurationProvider _configurationProvider;
    private UserImporter userImporter;

}