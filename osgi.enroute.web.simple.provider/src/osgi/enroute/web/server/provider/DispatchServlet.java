package osgi.enroute.web.server.provider;

import java.io.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.osgi.service.component.annotations.*;

import osgi.enroute.rootservlet.api.*;

@Component(property="path=/", service=Servlet.class)
public class DispatchServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	@Reference (target = "(path=/)")
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