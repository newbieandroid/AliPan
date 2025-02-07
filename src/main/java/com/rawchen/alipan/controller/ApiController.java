package com.rawchen.alipan.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rawchen.alipan.config.Constants;
import com.rawchen.alipan.entity.PanFile;
import com.rawchen.alipan.utils.FileUtil;
import com.rawchen.alipan.utils.HttpClientUtil;
import com.rawchen.alipan.utils.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin
@Controller
public class ApiController {

	@Value("${alipan.parent_file_id}")
	String parentFileId;

	@Value("${alipan.api_url}")
	String apiUrl;

	@Value("${alipan.referer_url}")
	String refererURL;

	@Value("${alipan.password_file_name}")
	String passwordFileName;

	/**
	 * 文件对象
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getFile/{fileId}")
	public PanFile getFile(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Referer", refererURL);
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/get",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		PanFile file = new PanFile();
		file.setFileId((String) jsonObject.get("file_id"));
		file.setType((String) jsonObject.get("type"));
		file.setName((String) jsonObject.get("name"));
		file.setCreatedAt((String) jsonObject.get("created_at"));
		file.setFileExtension((String) jsonObject.get("file_extension"));
		file.setParentFileId((String) jsonObject.get("parent_file_id"));
		file.setSize(((Number) jsonObject.get("size")).longValue());
		file.setUrl((String) jsonObject.get("url"));
		return file;
	}

	/**
	 * 文件列表
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getFolder/{fileId}")
	public List<PanFile> getFolder(@PathVariable("fileId") String fileId, @RequestParam(required = false) String password) {
		JSONObject requestJson = new JSONObject();
		requestJson.put("param1", 30);
		requestJson.put("all", false);
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("fields", "*");
		requestJson.put("marker", "");
//		requestJson.put("image_thumbnail_process", "image/resize,w_50");
		requestJson.put("image_thumbnail_process", "image/resize,w_400/format,jpeg");
		requestJson.put("image_url_process", "image/resize,w_1920/format,jpeg");
		requestJson.put("limit", 100);
		requestJson.put("url_expire_sec", 14400);
		requestJson.put("order_by", "name");
		requestJson.put("order_direction", "ASC");
		requestJson.put("parent_file_id", fileId);
//		requestJson.put("video_thumbnail_process", "video/snapshot,t_0,f_jpg,w_50");
		requestJson.put("video_thumbnail_process", "video/snapshot,t_0,f_jpg,w_400");

		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Referer", refererURL);
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/list", requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		if (jsonObject == null) {
//			System.out.println("{api}/file/list 响应为空: " + DateUtil.date());
			result = HttpClientUtil.doPost(apiUrl + "/file/list", requestJson.toString(), headerMap);
			jsonObject = JSONObject.parseObject(result);
		}
		//如果请求到json体不是空且不可用就刷新token
		if (jsonObject != null && "AccessTokenInvalid".equals(jsonObject.get("code"))) {
			refresh();
			result = HttpClientUtil.doPost(apiUrl + "/file/list", requestJson.toString(), headerMap);
			jsonObject = JSONObject.parseObject(result);
		}

		ArrayList<PanFile> panFiles = new ArrayList<>();
		JSONArray items = jsonObject.getJSONArray("items");
		for (int i = 0; i < items.size(); i++) {
			PanFile file = new PanFile();
			file.setFileId((String) items.getJSONObject(i).get("file_id"));
			file.setType((String) items.getJSONObject(i).get("type"));
			file.setName((String) items.getJSONObject(i).get("name"));
			file.setParentFileId((String) items.getJSONObject(i).get("parent_file_id"));
			file.setCreatedAt((String) items.getJSONObject(i).get("created_at"));
			file.setEncrypted(false);
			if ("file".equals(items.getJSONObject(i).get("type"))) {
				file.setPreviewUrl((String) items.getJSONObject(i).get("thumbnail"));
				file.setFileExtension((String) items.getJSONObject(i).get("file_extension"));
				file.setSize(((Number) items.getJSONObject(i).get("size")).longValue());
				file.setUrl((String) items.getJSONObject(i).get("url"));
			}
			panFiles.add(file);
		}

		//文件列表中密码文件的位置
		int passwordIndex = -1;

		for (int i = 0; i < panFiles.size(); i++) {
			//列表中如果有
			if (passwordFileName.equals(panFiles.get(i).getName())) {
				//找到一个名字为password的文件，如果传参为空就说明没传密码，直接返回一个文件且encrypted为true
				if (password == null || "".equals(password)) {
					panFiles.clear();
					panFiles.add(new PanFile(passwordFileName, true, "file"));
					break;
				} else {
					//找到一个名字为password的文件，但是传了密码参数
					String folderPasswd = StringUtil.clearStr(HttpClientUtil.doGet(panFiles.get(i).getUrl(), null, new HashMap<>(), null));
					//如果密码没对上
					if (!password.equals(folderPasswd)) {
						panFiles.clear();
						panFiles.add(new PanFile(passwordFileName, true, "file"));
						break;
					} else {
						//密码对上了，在文件列表中删除这个密码文件(同一文件夹只能一个)
						 passwordIndex = i;
					}
				}
			}
		}
		if (passwordIndex != -1) {
			panFiles.remove(passwordIndex);
		}
		return panFiles;
	}

	/**
	 * 获取下载链接，过期4小时
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getDownloadUrl/{fileId}")
	public PanFile getDownloadUrl(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("expire_sec", 14400);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Referer", refererURL);
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/get_download_url",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);

		PanFile file = new PanFile();
		file.setFileId(fileId);
		file.setSize(((Number) jsonObject.get("size")).longValue());
		file.setUrl((String) jsonObject.get("url"));
		return file;
	}

	/**
	 * 获取下载链接，过期4小时
	 *
	 * @param fileId
	 * @return
	 */
	@GetMapping(value = "/d/{fileId}")
	public String redirectUrl(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		requestJson.put("expire_sec", 14400);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Referer", refererURL);
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/get_download_url",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		return "redirect:" + jsonObject.get("url");
	}

	/**
	 * 获取Office文件在线预览url和token
	 * @see <a href="https://help.aliyun.com/document_detail/396287.html">阿里云智能媒体管理</a>
	 *
	 * @param fileId
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/getOfficePreviewUrl/{fileId}")
	public Map<String, String> getOfficePreviewUrl(@PathVariable("fileId") String fileId) {

		JSONObject requestJson = new JSONObject();
		requestJson.put("drive_id", Constants.DEFAULT_DRIVE_ID);
		requestJson.put("file_id", fileId);
		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("Referer", refererURL);
		headerMap.put("Authorization", "Bearer " + Constants.ACCESS_TOKEN);

		String result = HttpClientUtil.doPost(apiUrl + "/file/get_office_preview_url",
				requestJson.toString(), headerMap);
		JSONObject jsonObject = JSONObject.parseObject(result);
		String previewUrl = "";
		String accessToken = "";
		if (jsonObject != null) {
			previewUrl = (String) jsonObject.get("preview_url");
			accessToken = (String) jsonObject.get("access_token");
		}
		Map<String, String> map = new HashMap();
		map.put("preview_url", previewUrl);
		map.put("access_token", accessToken);
		return map;
	}

	/**
	 * 刷新token
	 *
	 * @return
	 */
	@ResponseBody
	@GetMapping(value = "/refresh")
	public String refresh() {
		String s = FileUtil.textFileToString(new File(System.getProperty("user.dir") +
				File.separator + "AliPanConfig"));
		Constants.setRefreshToken(s);
		JSONObject requestJson = new JSONObject();
		requestJson.put("grant_type", "refresh_token");
		requestJson.put("refresh_token", Constants.getRefreshToken());

		String result = HttpClientUtil.doPost(apiUrl + "/account/token", requestJson.toString());
		JSONObject jsonObject = JSONObject.parseObject(result);
		if (jsonObject.get("code") != null) {
			return "确认配置文件 AliPanConfig 首行是否为你的 refresh_token！";
		}

		if (jsonObject.get("code") == null && jsonObject.get("access_token") != null) {
			//刷新一次refresh_token到AliPanConfig
			String refreshToken = (String) jsonObject.get("refresh_token");
			if (refreshToken != null  && !"".equals(refreshToken)) {
				FileUtil.stringToTextFile(refreshToken, new File(System.getProperty("user.dir") +
						File.separator + "AliPanConfig"));
			}
			//更新一次access_token到Constants
			Constants.setAccessToken((String) jsonObject.get("access_token"));
			Constants.setRefreshToken(refreshToken);
			Constants.setDefaultDriveId((String) jsonObject.get("default_drive_id"));
			return "刷新配置文件成功，刷新 access_token 成功！";
		}
		return "其它问题，联系软件作者。";
	}
}