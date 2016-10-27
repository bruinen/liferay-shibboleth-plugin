package com.liferay.portal.security.shibboleth.configuration;


import aQute.bnd.annotation.metatype.Meta;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * User: Romeo Sheshi <a href="rsheshi@gmail.com">Romeo Sheshi</a>
 * Date: 26/10/2016
 * Time: 15:21
 */

@ExtendedObjectClassDefinition(
        category = "foundation", scope = ExtendedObjectClassDefinition.Scope.COMPANY
)
@Meta.OCD(
        id = "com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration",
        localization = "content/Language", name = "shibboleth.configuration.name"
)

public interface ShibbolethConfiguration{

    @Meta.AD(deflt = "false", description = "shibboleth-enable", required = false)
    public boolean enabled();

    @Meta.AD(deflt = "false", description = "shibboleth-logout-enable", required = false)
    public boolean logoutEnabled();

    @Meta.AD(deflt = "false", description = "shibboleth-headers-enable", required = false)
    public boolean headersEnabled();

    @Meta.AD(deflt = "false", description = "shibboleth-affiliation-truncate-enable", required = false)
    public boolean affiliationTruncateEnabled();

    @Meta.AD(deflt = "false", description = "shibboleth-screenname-transform-enable", required = false)
    public boolean screenameTransformEnabled();

    @Meta.AD(
            deflt = "false", description = "import-shibboleth-users-from-ldap", required = false
    )
    public boolean importFromLDAP();

    @Meta.AD(deflt = "", description = "shibboleth-user-header", required = false)
    public String userHeader();


    @Meta.AD(deflt = "", description = "shibboleth-user-header-email", required = false)
    public String userEmailHeader();


    @Meta.AD(deflt = "", description = "shibboleth-user-header-firstname", required = false)
    public String firstname();
    @Meta.AD(deflt = "", description = "shibboleth-user-header-surname", required = false)
    public String surname();

    @Meta.AD(deflt = "", description = "shibboleth-user-header-affiliation", required = false)
    public String userHeaderAffiliation();

    @Meta.AD(deflt = "false", description = "auto-create-users", required = false)
    public boolean autoCreateUsers();
    @Meta.AD(deflt = "false", description = "auto-update-users", required = false)
    public boolean autoUpdateUsers();
    @Meta.AD(deflt = "false", description = "auto-create-roles", required = false)
    public boolean autoCreateRole();
    @Meta.AD(deflt = "false", description = "auto-assign-user-role", required = false)
    public boolean autoAssignUserRole();
    @Meta.AD(deflt = "", description = "auto-assign-user-role-subtype", required = false)
    public String autoAssignUserRoleSubType();


    @Meta.AD(deflt = "/Shibboleth.sso/Logout?return=/", required = false)
    public String logoutUrl();
    @Meta.AD(deflt = "/c/portal/login/shibboleth",  required = false)
    public String loginUrl();




}
