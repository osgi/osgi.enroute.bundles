package osgi.enroute.web.server.provider;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.framework.*;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.whiteboard.*;

import osgi.enroute.servlet.api.*;

@Component(
		property 	=
		{
			    HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/", 
			    "name=DispatchServlet", 
			    "no.index=true",
				Constants.SERVICE_RANKING + ":Integer=100"
		},
		service		= Servlet.class,
		immediate	= true )
public class DispatchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Reference (
			target 		= "(" + HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/)",
			cardinality	= ReferenceCardinality.AT_LEAST_ONE)
	volatile List<ConditionalServlet> targets;

	public void service(HttpServletRequest rq, HttpServletResponse rsp) throws ServletException, IOException {
		for (ConditionalServlet cs : targets) {
			try {
				if (cs.doConditionalService(rq, rsp))
					return;
			} catch (Exception e) {
				throw new ServletException("Exception thrown by ConditionalServlet", e);
			}
		}

		// No ConditionalServlets were found. Since we don't know what to do, we return a 404.
		rsp.setStatus(HttpServletResponse.SC_NOT_FOUND);
	}
}