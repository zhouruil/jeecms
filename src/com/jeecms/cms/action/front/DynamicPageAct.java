package com.jeecms.cms.action.front;

import static com.jeecms.common.web.Constants.INDEX;
import static  com.jeecms.cms.Constants.TPLDIR_INDEX;

import java.io.File;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.jeecms.cms.entity.main.Channel;
import com.jeecms.cms.entity.main.Content;
import com.jeecms.cms.entity.main.ContentCheck;
import com.jeecms.cms.manager.assist.CmsKeywordMng;
import com.jeecms.cms.manager.main.ChannelMng;
import com.jeecms.cms.manager.main.ContentMng;
import com.jeecms.common.page.Paginable;
import com.jeecms.common.page.SimplePage;
import com.jeecms.common.web.springmvc.RealPathResolver;
import com.jeecms.core.entity.CmsConfig;
import com.jeecms.core.entity.CmsGroup;
import com.jeecms.core.entity.CmsSite;
import com.jeecms.core.entity.CmsUser;
import com.jeecms.core.manager.CmsConfigMng;
import com.jeecms.core.web.util.CmsUtils;
import com.jeecms.core.web.util.FrontUtils;
import com.jeecms.core.web.util.URLHelper;
import com.jeecms.core.web.util.URLHelper.PageInfo;

@Controller
public class DynamicPageAct {
	private static final Logger log = LoggerFactory
			.getLogger(DynamicPageAct.class);
	/**
	 * 首页模板名称
	 */
	public static final String TPL_INDEX = "tpl.index";
	public static final String GROUP_FORBIDDEN = "login.groupAccessForbidden";
	public static final String CONTENT_STATUS_FORBIDDEN ="content.notChecked";
	

	/**
	 * TOMCAT的默认路径
	 * 
	 * @param request
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String index(HttpServletRequest request,HttpServletResponse response, ModelMap model) {
		CmsSite site = CmsUtils.getSite(request);
		FrontUtils.frontData(request, model, site);
	//	return FrontUtils.getTplPath(request, site.getSolutionPath(),TPLDIR_INDEX, TPL_INDEX);
		//带有其他路径则是非法请求(非内网)
		String uri=URLHelper.getURI(request);
		if(StringUtils.isNotBlank(uri)&&(!(uri.equals("/")||uri.equals("/index.jhtml")))){
			CmsConfig config=configMng.get();
			if(!config.getInsideSite()){
				return FrontUtils.pageNotFound(request, response, model);
			}
		}
		//使用静态首页而且静态首页存在
		if(site.getStaticIndex()&&new File(realPathResolver.get(site.getStaticDir()+INDEX)).exists()){
			return FrontUtils.getTplPath("", site.getStaticDir(), INDEX);
		}else{
			String tpl = site.getTplIndex();
			if (!StringUtils.isBlank(tpl)) {
				return tpl;
			} else {
				return FrontUtils.getTplPath(request, site.getSolutionPath(),TPLDIR_INDEX, TPL_INDEX);
			}
		}
	}

	/**
	 * WEBLOGIC的默认路径
	 * 
	 * @param request
	 * @param model
	 * @return
	 */
	@RequestMapping(value = "/index.jhtml", method = RequestMethod.GET)
	public String indexForWeblogic(HttpServletRequest request,HttpServletResponse response, ModelMap model) {
		return index(request, response,model);
	}

	/**
	 * 动态页入口
	 */
	@RequestMapping(value = "/**/*.*", method = RequestMethod.GET)
	public String dynamic(HttpServletRequest request,
			HttpServletResponse response, ModelMap model) {
		// 尽量不要携带太多参数，多使用标签获取数据。
		// 目前已知的需要携带翻页信息。
		// 获得页号和翻页信息吧。
		int pageNo = URLHelper.getPageNo(request);
		String[] params = URLHelper.getParams(request);
		PageInfo info = URLHelper.getPageInfo(request);
		String[] paths = URLHelper.getPaths(request);
		CmsConfig config=configMng.get();
		if(config.getInsideSite()){
			return network(paths, params, info, pageNo, request, response, model);
		}else{
			return extranet(paths, params, info, pageNo, request, response, model);
		}
	}
	
	private String network(String[] paths,String[] params,PageInfo info,Integer pageNo,HttpServletRequest request,
			HttpServletResponse response, ModelMap model){
		int len = paths.length;
		if (len == 2) {
			//首页
			if(paths[1].equals(INDEX)){
				return index(request,response, model);
			}else{
				// 单页
				return channel(paths[1],true, pageNo, params, info, request, response,
						model);
			}
		} else if (len == 3) {
			if (paths[2].equals(INDEX)) {
				// 栏目页
				return channel(paths[1],false, pageNo, params, info, request,
						response, model);
			} else {
				// 内容页
				try {
					Integer id = Integer.parseInt(paths[2]);
					return content(id, pageNo, params, info, request, response,
							model);
				} catch (NumberFormatException e) {
					log.debug("Content id must String: {}", paths[1]);
					return FrontUtils.pageNotFound(request, response, model);
				}
			}
		} else {
			log.debug("Illegal path length: {}, paths: {}", len, paths);
			return FrontUtils.pageNotFound(request, response, model);
		}
	}
	
	private String extranet(String[] paths,String[] params,PageInfo info,Integer pageNo,HttpServletRequest request,
			HttpServletResponse response, ModelMap model){
		int len = paths.length;
		if (len == 1) {
			// 单页
			return channel(paths[0],true, pageNo, params, info, request, response,
					model);
		} else if (len == 2) {
			if (paths[1].equals(INDEX)) {
				// 栏目页
				return channel(paths[0],false, pageNo, params, info, request,
						response, model);
			} else {
				// 内容页
				try {
					Integer id = Integer.parseInt(paths[1]);
					return content(id, pageNo, params, info, request, response,
							model);
				} catch (NumberFormatException e) {
					log.debug("Content id must String: {}", paths[1]);
					return FrontUtils.pageNotFound(request, response, model);
				}
			}
		} else {
			log.debug("Illegal path length: {}, paths: {}", len, paths);
			return FrontUtils.pageNotFound(request, response, model);
		}
	}

	public String channel(String path,boolean checkAlone, int pageNo, String[] params,
			PageInfo info, HttpServletRequest request,
			HttpServletResponse response, ModelMap model) {
		CmsSite site = CmsUtils.getSite(request);
		Channel channel = channelMng.findByPathForTag(path, site.getId());
		if (channel == null) {
			log.debug("Channel path not found: {}", path);
			return FrontUtils.pageNotFound(request, response, model);
		}
		//检查是否单页
		if(checkAlone){
			if(channel.getHasContent()){
				return FrontUtils.pageNotFound(request, response, model);
			}
		}
		model.addAttribute("channel", channel);
		FrontUtils.frontData(request, model, site);
		FrontUtils.frontPageData(request, model);
		String equipment=(String) request.getAttribute("ua");
		if(StringUtils.isNotBlank(equipment)&&equipment.equals("mobile")){
			return channel.getMobileTplChannelOrDef();
		}
		return channel.getTplChannelOrDef();
	}

	public String content(Integer id, int pageNo, String[] params,
			PageInfo info, HttpServletRequest request,
			HttpServletResponse response, ModelMap model) {
		Content content = contentMng.findById(id);
		if (content == null) {
			log.debug("Content id not found: {}", id);
			return FrontUtils.pageNotFound(request, response, model);
		}
		Integer pageCount=content.getPageCount();
		if(pageNo>pageCount||pageNo<0){
			return FrontUtils.pageNotFound(request, response, model);
		}
		//非终审文章
		CmsConfig config=CmsUtils.getSite(request).getConfig();
		Boolean preview=config.getConfigAttr().getPreview();
		if(config.getViewOnlyChecked()&&!content.getStatus().equals(ContentCheck.CHECKED)){
			return FrontUtils.showMessage(request, model, CONTENT_STATUS_FORBIDDEN);
		}
		CmsUser user = CmsUtils.getUser(request);
		CmsSite site = content.getSite();
		Set<CmsGroup> groups = content.getViewGroupsExt();
		int len = groups.size();
		// 需要浏览权限
		if (len != 0) {
			// 没有登录
			if (user == null) {
				return FrontUtils.showLogin(request, model, site);
			}
			// 已经登录但没有权限
			Integer gid = user.getGroup().getId();
			boolean right = false;
			for (CmsGroup group : groups) {
				if (group.getId().equals(gid)) {
					right = true;
					break;
				}
			}
			//无权限且不支持预览
			if (!right&&!preview) {
				String gname = user.getGroup().getName();
				return FrontUtils.showMessage(request, model, GROUP_FORBIDDEN,
						gname);
			}
			//无权限支持预览
			if(!right&&preview){
				model.addAttribute("preview", preview);
				model.addAttribute("groups", groups);
			}
		}
		String txt = content.getTxtByNo(pageNo);
		// 内容加上关键字
		txt = cmsKeywordMng.attachKeyword(site.getId(), txt);
		Paginable pagination = new SimplePage(pageNo, 1, content.getPageCount());
		model.addAttribute("pagination", pagination);
		FrontUtils.frontPageData(request, model);
		model.addAttribute("content", content);
		model.addAttribute("channel", content.getChannel());
		model.addAttribute("title", content.getTitleByNo(pageNo));
		model.addAttribute("txt", txt);
		model.addAttribute("pic", content.getPictureByNo(pageNo));
		FrontUtils.frontData(request, model, site);
		String equipment=(String) request.getAttribute("ua");
		if(StringUtils.isNotBlank(equipment)&&equipment.equals("mobile")){
			return content.getMobileTplContentOrDef(content.getModel());
		}
		return content.getTplContentOrDef(content.getModel());
	}
	

	@Autowired
	private ChannelMng channelMng;
	@Autowired
	private ContentMng contentMng;
	@Autowired
	private CmsKeywordMng cmsKeywordMng;
	@Autowired
	private CmsConfigMng configMng;
	@Autowired
	private RealPathResolver realPathResolver;
}
