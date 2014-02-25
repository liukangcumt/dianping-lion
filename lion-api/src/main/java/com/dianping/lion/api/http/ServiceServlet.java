package com.dianping.lion.api.http;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;

import com.dianping.lion.entity.Environment;
import com.dianping.lion.entity.Project;
import com.dianping.lion.entity.Service;
import com.dianping.lion.entity.User;

public class ServiceServlet extends AbstractLionServlet {

	private enum Key {env, id, project, service, address, group, ip, port};
	
	private enum Action {get, set, publish, unpublish};
	
	public ServiceServlet () {
		this.requestIdentityRequired = false;
	}
	
	@Override
	protected void doService(HttpServletRequest request, HttpServletResponse response, String querystr) throws Exception {
		String pathInfo = request.getPathInfo();
		if(pathInfo == null) 
			throw new RuntimeException("Action is null");
		
		pathInfo = pathInfo.toLowerCase().substring(1);
		Action action = null;
		try {
			action = Action.valueOf(pathInfo);
		} catch(RuntimeException e) {
			throw new RuntimeException("Unknown action " + pathInfo);
		}
		
		String env, id, project, service, address, group, ip, port, result = null;
		switch (action) {
		case get:
			// getServiceAddress(env, service, group)
			env = getParam(request, Key.env);
			service = getParam(request, Key.service);
			group = getParam(request, Key.group, DEFAULT_GROUP);
			result = getServiceAddress(env, service, group);
			response.getWriter().write("0|" + result);
			break;
		case set:
			// setServiceAddress(env, id, service, group, address)
			env = getParam(request, Key.env);
			id = getParam(request, Key.id);
			project = getParam(request, Key.project, "");
			service = getParam(request, Key.service);
			group = getParam(request, Key.group, DEFAULT_GROUP);
			address = getParam(request, Key.address);
			result = setServiceAddress(env, id, project, service, group, address);
			response.getWriter().write("0|" + result);
			break;
		case publish:
			// publishService(env, id, service, group, ip, port)
			env = getParam(request, Key.env);
			id = getParam(request, Key.id);
			project = getParam(request, Key.project, "");
			service = getParam(request, Key.service);
			group = getParam(request, Key.group, DEFAULT_GROUP);
			ip = getParam(request, Key.ip);
			port = getParam(request, Key.port);
			result = publishService(env, id, project, service, group, ip, port);
			response.getWriter().write("0|" + result);
			break;
		case unpublish:
			// unpublishService(env, id, service, group, ip, port)
			env = getParam(request, Key.env);
			id = getParam(request, Key.id);
			service = getParam(request, Key.service);
			group = getParam(request, Key.group, DEFAULT_GROUP);
			ip = getParam(request, Key.ip);
			port = getParam(request, Key.port);
			result = unpublishService(env, id, service, group, ip, port);
			response.getWriter().write("0|" + result);
			break;
		}
	}

    private String getServiceAddress(String env, String service, String group) {
        int envId = getEnvId(env);
        Service srv = serviceService.getService(envId, service, group);
        if(srv==null) {
            StringBuilder sb = new StringBuilder("No service ");
            sb.append(service);
            if(StringUtils.isNotBlank(group))
                sb.append(" for group ").append(group);
            sb.append(" in env ").append(env);
            throw new RuntimeException(sb.toString());
        }
        return srv.getHosts();
    }

    private String setServiceAddress(String env, String id, String project, 
            String service, String group, String address) throws Exception {
        int envId = getEnvId(env);
        verifyIdentity(id);
        
        Service srv = serviceService.getService(envId, service, group);
        if(srv != null) {
            srv.setHosts(address);
            serviceService.updateService(srv);
        } else {
            int projectId = getProjectId(service, project);
            
            srv = new Service();
            srv.setEnvId(envId);
            srv.setProjectId(projectId);
            srv.setName(service);
            srv.setGroup(group);
            srv.setHosts(address);
            serviceService.createService(srv);
        }
        return address;
    }

	private String publishService(String env, String id, String project, 
	        String service, String group, String ip, String port) throws Exception {
	    int envId = getEnvId(env);
	    verifyIdentity(id);
	    
	    Service srv = serviceService.getService(envId, service, group);
	    if(srv != null) {
	        srv.setHosts(addHost(srv.getHosts(), ip, port));
	        serviceService.updateService(srv);
	    } else {
	        int projectId = getProjectId(service, project);
	        
	        srv = new Service();
	        srv.setEnvId(envId);
	        srv.setProjectId(projectId);
	        srv.setName(service);
	        srv.setGroup(group);
	        srv.setHosts(addHost(null, ip, port));
	        serviceService.createService(srv);
	    }
	    return srv.getHosts();
	}
	
	private String unpublishService(String env, String id, String service,
			String group, String ip, String port) throws Exception {
	    int envId = getEnvId(env);
        verifyIdentity(id);
        
        Service srv = serviceService.getService(envId, service, group);
        if(srv == null) {
            throw new RuntimeException("Env " + env + " service " + service + " group " + group + " is not found");
        }
        srv.setHosts(removeHost(srv.getHosts(), ip, port));
        serviceService.updateService(srv);
        return srv.getHosts();
	}
	
	private String addHost(String hosts, String ip, String port) {
	    if(!checkIpAddress(ip)) {
	        throw new RuntimeException("Invalid ip " + ip);
	    }
	    if(!checkNumber(port, 1, 65535)) {
	        throw new RuntimeException("Invalid port " + port);
	    }
	    String host = ip + ":" + port;
	    if(hosts != null && hosts.indexOf(host) != -1) {
	        // if already exists, just return	        
	        logger.error("Host " + host + " already in service host list " + hosts);
	        return hosts;
	    }
	    hosts = (hosts==null ? "" : hosts.trim());
	    StringBuilder sb = new StringBuilder(hosts);
	    if(hosts.length()>0 && !hosts.endsWith(","))
	        sb.append(',');
	    sb.append(host);
	    return sb.toString();
	}

	private String removeHost(String hosts, String ip, String port) {
	    if(!checkIpAddress(ip)) {
            throw new RuntimeException("Invalid ip " + ip);
        }
        if(!checkNumber(port, 1, 65535)) {
            throw new RuntimeException("Invalid port " + port);
        }
	    String host = ip + ":" + port;
	    int idx = -1;
	    if(hosts==null || (idx = hosts.indexOf(host)) == -1) {
	        // if not exist, ignore
	        logger.error("Host " + host + " is not in service host list " + hosts);
	        return hosts;
	    }
	    int idx2 = hosts.indexOf(',', idx);
	    String newHosts = hosts.substring(0, idx) + 
	            ((idx2==-1 || idx2==hosts.length()-1) ? "" : hosts.substring(idx2 + 1));
	    return newHosts;
	}
	
	private boolean checkIpAddress(String ip) {
	    if(null == ip)
	        return false;
	    return ip.indexOf('.') != -1;
	}
	
	private boolean checkNumber(String number, int min, int max) {
	    try {
	        int n = Integer.parseInt(number);
	        return (n>=min && n<=max);
	    } catch(NumberFormatException e) {
	        return false;
	    }
	}

	private int getProjectId(String service, String project) {
		Integer projectId = serviceService.getProjectId(service);
		if(projectId != null)
			return projectId.intValue();
		
		if(StringUtils.isBlank(project)) {
			throw new RuntimeException("Project is null");
		}
		
		Project prj = projectService.findProject(project);
		if(prj != null) {
			return prj.getId();
		}
		throw new RuntimeException("Invalid project " + project);
	}
	
	private void verifyIdentity(String id) {
		int userId = 0;
		try {
			userId = Integer.parseInt(id);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid id " + id);
		}
		User user = userService.findById(userId);
		if (user == null) {
			throw new RuntimeException("Invalid id " + id);
		}
		if (!user.isSystem()) {
			throw new RuntimeException("Only support user with system level");
		}
	}

	private int getEnvId(String env) {
		Environment environment = environmentService.findEnvByName(env);
		if (environment == null) {
			throw new RuntimeException("Invalid environment " + env);
		}
		return environment.getId();
	}

	private String getParam(HttpServletRequest request, Key key) {
		String param = request.getParameter(key.name());
		if(param == null)
			throw new RuntimeException(key.name() + " is null");
		return param;
	}

	private String getParam(HttpServletRequest request, Key key, String defaultValue) {
		String param = request.getParameter(key.name());
		return param == null ? defaultValue : param;
	}
	
}
