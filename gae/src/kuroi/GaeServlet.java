package kuroi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class GaeServlet extends HttpServlet 
{
	String script_template;
	String bootstrap_html_dark;
	String bootstrap_html_light;
	
	String streamToString(InputStream in) throws IOException 
	{
		StringBuilder out = new StringBuilder();
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		for (String line = br.readLine(); line != null; line = br.readLine())
			out.append(line);
		br.close();
		return out.toString();
	}
	
	public void init(ServletConfig config) throws ServletException
    {
		ServletContext sc = config.getServletContext();
		
		try {
			script_template = streamToString(sc.getResourceAsStream("/ssi_content.html"));
			bootstrap_html_dark = streamToString(sc.getResourceAsStream("/themes/dark/bootstrap.html"));
			bootstrap_html_light = streamToString(sc.getResourceAsStream("/themes/light/bootstrap.html"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		super.init(config);
    }
	 
	String sanitize(String s)
	{
		return s.replace("<", "&lt;")
				.replace("&", "&amp;")
				.replace("\"", "\\\"");
	}
	
	void getPage(String resource, HttpServletRequest req, HttpServletResponse resp)
	{
		String domain = req.getServerName();
		String referer = req.getHeader("Referer");
		
		if (referer == null || (referer != null 
					 			&& (referer.isEmpty() 
					 				|| (referer.indexOf(domain) < 0))))
			return;
		
        try 
        {
            URL url = new URL(resource);
            byte buffer[] = new byte[4096];
            InputStream is = url.openStream();
            OutputStream os = resp.getOutputStream(); 
            
            int n = 0;          
            while ((n = is.read(buffer)) > 0) 
            {
                os.write(buffer, 0, n);
            }

        } catch (MalformedURLException e) {
        } catch (IOException e) {
        }
	}
	
	void serveContent(String uri, HttpServletRequest req, HttpServletResponse resp) 
			throws IOException
	{
		String entry_point = "";
		String scripts = script_template;
		
		String theme = "dark";
		Cookie [] cookies = req.getCookies();
		if (cookies != null)
			for (Cookie c : cookies)
				if ("theme".equals(c.getName()))
					theme = c.getValue();
			
		String bootstrap_html = "light".equals(theme)
				? bootstrap_html_light
				: bootstrap_html_dark;
		
		if ("/".equals(uri) || uri.isEmpty())
		{
			entry_point = "urlbar";
		}
		else if (uri.startsWith("/_/"))
		{
			entry_point = "main";
			String resource = sanitize(uri);
			scripts = scripts.replace("$resource", resource.substring(3));
		}
		else
		{	
			Pattern pattern = Pattern.compile("/:([a-z]+)");
	        Matcher matcher = pattern.matcher(uri);
	        
	        if (matcher.find())
	        {
	        	entry_point = matcher.group(1);
	        }
		}
		
		scripts = scripts.replace("$entry_point", entry_point);
		String html = bootstrap_html.replace("<!--ssi_content-->", scripts);
		
		resp.setContentType("text/html");
		resp.getWriter().println(html);
		
	}

	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		
		String uri = req.getRequestURI();
		
		if (uri.startsWith("/get/"))
		{
			getPage(uri.substring(5), req, resp);
		}
		else
		{
			serveContent(uri, req, resp);
		}
	}
}
