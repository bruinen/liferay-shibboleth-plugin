package com.liferay.portal.security.shibboleth.internal.module.configuration.definition;

import com.liferay.portal.kernel.settings.definition.ConfigurationBeanDeclaration;
import com.liferay.portal.security.shibboleth.configuration.ShibbolethConfiguration;
import org.osgi.service.component.annotations.Component;


/**
 * User: Romeo Sheshi <a href="rsheshi@gmail.com">Romeo Sheshi</a>
 * Date: 26/10/2016
 * Time: 15:21
 */
@Component
public class ShibbolethCompanyServiceConfigurationBeanDeclaration
		implements ConfigurationBeanDeclaration {

	@Override
	public Class<?> getConfigurationBeanClass() {
		return ShibbolethConfiguration.class;
	}

}