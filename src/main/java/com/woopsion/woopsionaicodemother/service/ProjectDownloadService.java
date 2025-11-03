package com.woopsion.woopsionaicodemother.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @author wangpengcan
 * @date 2025/11/3
 * @time 14:36
 * @description
 */
public interface ProjectDownloadService {

    /**
     * 下载项目
     *
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
