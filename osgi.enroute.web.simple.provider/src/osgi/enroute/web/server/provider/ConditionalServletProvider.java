package osgi.enroute.web.server.provider;

import java.util.*;
import java.util.concurrent.*;

import javax.servlet.*;

import org.osgi.framework.*;
import org.osgi.namespace.extender.*;
import org.osgi.service.component.*;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.whiteboard.*;
import org.osgi.service.log.*;

import aQute.bnd.annotation.headers.*;
import osgi.enroute.rootservlet.api2.*;

// TODO: Put these values in WebServerConstants
@ProvideCapability(ns = ExtenderNamespace.EXTENDER_NAMESPACE, name = "osgi.enroute.simple.conditionalservlet", version = "1.0.0")
@Component(immediate = true, name = ConditionalServletProvider.NAME)
public class ConditionalServletProvider {
	static final String 								NAME = "osgi.enroute.simple.conditionalservlet";
	private Map<String,ServiceRegistration<Servlet>>	registrations = new ConcurrentHashMap<>();
	private Map<String,ConditionalServletController>	controllers = new ConcurrentHashMap<>();
	private LogService									logger;

	@Reference(cardinality = ReferenceCardinality.MULTIPLE)
	void bindConditionalServlet(ConditionalServlet servlet, Map<String,Object> properties, ComponentContext context) {
		String servletPattern = (String)properties.get(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN);
		ConditionalServletController controller = controllers.get(servletPattern);
		if(controller == null) {
			controller = new ConditionalServletController(logger);
			BundleContext bundleContext = context.getBundleContext();
			Dictionary<String,Object> serviceProperties = new Hashtable<>();
			serviceProperties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, servletPattern);
			serviceProperties.put("name", NAME + servletPattern);
			serviceProperties.put("no.index", true);
			ServiceRegistration<Servlet> registration = bundleContext.registerService(Servlet.class, controller, serviceProperties);
			registrations.put(servletPattern, registration);
		}

		controller.bindServlet(servlet, properties);
	}

	void unbindConditionalServlet(ConditionalServlet servlet, Map<String,Object> properties, ComponentContext context) {
		String servletPattern = (String)properties.get(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN);
		ConditionalServletController controller = controllers.get(servletPattern);
		controller.unbindServlet(servlet);
		if(controller.servlets().isEmpty()) {
			BundleContext bundleContext = context.getBundleContext();
			ServiceRegistration<Servlet> registration = registrations.get(servletPattern);
			bundleContext.ungetService(registration.getReference());
			registrations.remove(servletPattern);
		}
	}

	@Reference
	void bindLogService(LogService logger) {
		this.logger = logger;
	}
}
