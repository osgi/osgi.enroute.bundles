package osgi.enroute.web.server.provider;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.framework.*;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.whiteboard.*;
import org.osgi.service.log.*;

import osgi.enroute.servlet.api.*;

@Component(
		property 	=
		{
			    HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/", 
			    "name=DispatchServlet", 
			    "no.index=true",
				Constants.SERVICE_RANKING + ":Integer=100",
				ServletConstants.BLACKLIST_TIMEOUT + ":Long=300000" 
		},
		service		= Servlet.class,
		configurationPolicy 	= ConfigurationPolicy.OPTIONAL,
		immediate	= true )
public class DispatchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@interface Config {
		long timeout();
	}

	Config											config;

	// Blacklist badly behaving servlets for a certain period of time.
	private final Map<ConditionalServlet, Long> 	blacklist = new ConcurrentHashMap<>();

	@Reference (
			target 		= "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/)",
			cardinality	= ReferenceCardinality.AT_LEAST_ONE)
	volatile List<ConditionalServlet> 				targets;
	@Reference LogService							log;

	@Activate
	void activate(Config config, Map<String,Object> props, BundleContext context) throws Exception {
		this.config = config;
	}

	public void service(HttpServletRequest rq, HttpServletResponse rsp) throws ServletException, IOException {
		for (ConditionalServlet cs : targets) {
			try {
				if (!isBlacklisted(cs) && cs.doConditionalService(rq, rsp))
					return;
			} catch (Exception e) {
				String message = "Exception thrown by ConditionalServlet.";
				if(config.timeout() != 0) {
					// Blacklist this servlet by adding to the blacklist
					long now = System.currentTimeMillis();
					long unlistingTime = now + config.timeout();
					blacklist.put(cs, unlistingTime);
					message += " This servlet has been blacklisted!";
				}

				log.log(LogService.LOG_ERROR, message, e);
				throw new ServletException(message, e);
			}
		}

		// No ConditionalServlets were found. Since we don't know what to do, we return a 404.
		rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}

	/*
	 * Return true if blacklisted, false otherwise.
	 * 
	 * While we're checking... if the blacklist timeout has expired, remove from the blacklist.
	 */
	private boolean isBlacklisted(ConditionalServlet cs) {
		// If the servlet is not in the blacklist, then we're good to go!
		if(!blacklist.containsValue(cs))
			return false;

		// If the value is -1, then the blacklist lasts forever
		if(config.timeout() == -1)
			return true;

		// If the blacklist timeout has not yet expired, then this servlet should be ignored.
		long unlistingTime = blacklist.get(cs);
		long now = System.currentTimeMillis();
		boolean isExpired = unlistingTime > now;
		if(!isExpired)
			// The blacklist has not yet expired, so the servlet remains blacklisted for now.
			return true;

		// The blacklist has expired, so remove it from the list.
		blacklist.remove(cs);
		return false;
	}
}