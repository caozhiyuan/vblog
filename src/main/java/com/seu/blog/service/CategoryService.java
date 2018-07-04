package com.seu.blog.service;

import com.baomidou.mybatisplus.service.IService;
import com.seu.common.utils.PageUtils;
import com.seu.blog.entity.CategoryEntity;

import java.util.Map;

/**
 * 文章类别表
 *
 * @author liangfeihu
 * @email liangfhhd@163.com
 * @date 2018-07-04 15:00:54
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

