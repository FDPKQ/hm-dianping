package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.MinIOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import javax.annotation.Resource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("upload")
public class MinioUploadController {

    private final static String IMAGE_BASE_URL = "https://minio.lan.luoxianjun.com/hmdp/";
    private final static String HMDP_BUCKET_NAME = "hmdp";

    @Resource
    MinIOUtils minIOUtils;


    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            // 保存文件
            minIOUtils.uploadFile(image.getInputStream(), HMDP_BUCKET_NAME, fileName);
            // 返回结果
            log.debug("文件上传成功，{}", IMAGE_BASE_URL + fileName);
            return Result.ok(IMAGE_BASE_URL + fileName);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.fail("文件上传失败");
        }

    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        try {
            minIOUtils.deleteObject(HMDP_BUCKET_NAME, filename);
            return Result.ok();
        } catch (Exception e) {
            log.error("文件删除失败");
            return Result.fail(e.getMessage());
        }

    }

    @GetMapping("/download")
    public Result DownloadBlogImg(@RequestParam("name") String filename) {
        try {
            String url = minIOUtils.getPresignedObjectUrl(HMDP_BUCKET_NAME, filename, 30, TimeUnit.MINUTES);
            return Result.ok(url);
        } catch (Exception e) {
            return Result.fail("获取URL失败");
        }

    }


    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 生成文件名
        return StrUtil.format("blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
