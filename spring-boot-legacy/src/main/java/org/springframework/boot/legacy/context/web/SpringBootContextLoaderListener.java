/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.legacy.context.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.StandardServletEnvironment;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * A {@link ContextLoaderListener} that uses {@link SpringApplication} to initialize an
 * application context. Allows Servlet 2.5 applications (with web.xml) to take advantage
 * of all the initialization extras in Spring Boot even if they don't use an embedded
 * container.
 *
 * @author Daniel Cruver
 * @author Dave Syer
 */
public class SpringBootContextLoaderListener extends ContextLoaderListener {

	private static final Log logger = LogFactory.getLog(SpringBootContextLoaderListener.class);

	private static final String INIT_PARAM_DELIMITERS = ",; \t\n";

	@Override
	public WebApplicationContext initWebApplicationContext(
			final ServletContext servletContext) {
		String configLocationParam = servletContext.getInitParameter(CONFIG_LOCATION_PARAM);
		String[] classNames = StringUtils.tokenizeToStringArray(configLocationParam, INIT_PARAM_DELIMITERS);

		Class[] classes = new Class[classNames.length];
		for (int i = 0; i < classes.length; i++) {
			try {
				classes[i] = ClassUtils.forName(classNames[i], null);
			} catch (ClassNotFoundException e) {
				throw new ApplicationContextException(
						"Failed to load custom context class [" + classNames[i] + "]", e);
			}
		}

		SpringApplicationBuilder builder = new SpringApplicationBuilder(classes);

		StandardServletEnvironment environment = new StandardServletEnvironment();
		environment.initPropertySources(servletContext, (ServletConfig) null);
		builder.environment(environment);

		@SuppressWarnings("unchecked")
		Class<? extends ConfigurableApplicationContext> contextClass = (Class<? extends ConfigurableApplicationContext>) determineContextClass(servletContext);
		builder.contextClass(contextClass);

		WebApplicationContext context;
		ApplicationContext parent = getExistingRootWebApplicationContext(servletContext);
		if (parent != null) {
			logger.info("Root context already created (using as parent).");
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, null);
			builder.initializers(new ParentContextApplicationContextInitializer(parent));

			context = (WebApplicationContext) builder.run();
		} else {
			logger.info("No existing root context; will created one.");

			builder.initializers(new ApplicationContextInitializer<GenericWebApplicationContext>() {
				@Override
				public void initialize(GenericWebApplicationContext applicationContext) {
					applicationContext.setServletContext(servletContext);
				}
			});
			context = (WebApplicationContext) builder.run();
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
		}

		return context;
	}

	private ApplicationContext getExistingRootWebApplicationContext(ServletContext servletContext) {
		Object context = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (context instanceof ApplicationContext) {
			return (ApplicationContext) context;
		}
		return null;
	}

	@Override
	protected Class<?> determineContextClass(ServletContext servletContext) {
		String contextClassName = servletContext.getInitParameter(CONTEXT_CLASS_PARAM);
		if (contextClassName != null) {
			try {
				return ClassUtils.forName(contextClassName, null);
			}
			catch (Exception e) {
				throw new ApplicationContextException(
						"Failed to load custom context class [" + contextClassName + "]",
						e);
			}
		}
		return AnnotationConfigNonEmbeddedWebApplicationContext.class;
	}

}
