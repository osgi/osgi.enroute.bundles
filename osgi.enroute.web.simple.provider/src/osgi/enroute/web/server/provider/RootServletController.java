package osgi.enroute.web.server.provider;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.service.component.annotations.*;
import org.osgi.service.http.whiteboard.*;
import org.osgi.service.log.*;

import osgi.enroute.http.capabilities.*;
import osgi.enroute.rootservlet.api.*;

@RequireHttpImplementation
@Component(
		service = Servlet.class, 
		immediate = true, property = {
				HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=" + "/", 
				"name=" + RootServletController.NAME, 
				"no.index=true"
		}, 
		name = RootServletController.NAME )
public class RootServletController extends HttpServlet {
	static final String 				NAME = "osgi.enroute.simple.rootcontroller";
	private static final long 			serialVersionUID = 1L;
	private ReentrantLock				lock = new ReentrantLock();
	private Map<Integer,RootServlet>	servlets = new TreeMap<>();
	private LogService					logger;

	@Override
	protected void service(HttpServletRequest rq, HttpServletResponse rsp) throws ServletException, IOException {
		boolean isProcessed = false;
		try {
			lock.lock();
			Iterator<RootServlet> i = servlets.values().iterator();
			while (i.hasNext() && !isProcessed) {
				RootServlet servlet = i.next();
				try {
					isProcessed = servlet.doConditionalService(rq, rsp);					
				}
				catch (Exception e) {
					logger.log(LogService.LOG_ERROR, "A RootServlet threw an Exception and will be removed from the queue");
					unbindRootServlet(servlet);
				}
			}
		}
		finally {
			lock.unlock();
		}

		// TODO: What happens if NONE process the request? What should the default behaviour be??
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE)
	void bindRootServlet(RootServlet servlet, Map<String,Object> properties) {
		Integer ranking = (Integer)properties.get("service.ranking");
		if(ranking == null || ranking < 0) {
			logger.log(LogService.LOG_ERROR, "Bad ranking value");
		}
		try {
			lock.lock();
			servlets.put(ranking, servlet);
		}
		finally {
			lock.unlock();
		}
		
	}

	void unbindRootServlet(RootServlet servlet) {
		try {
			lock.lock();
			servlets.values().remove(servlet);
		}
		finally {
			lock.unlock();
		}
	}

	@Reference
	void bindLogService(LogService logger) {
		this.logger = logger;
	}
}
