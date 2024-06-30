package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        String stringBlog  = stringRedisTemplate.opsForList().leftPop("blog:viewCount:"+id);
        Blog blog= JSONUtil.toBean(stringBlog, Blog.class);
        if (stringBlog !=null){
            return Result.ok(blog);
        }
        blog = getById(id);
        if (blog==null){
            return Result.fail("博客不存在");
        }
        stringBlog = JSONUtil.toJsonStr(blog);
        stringRedisTemplate.opsForList().leftPush("blog:viewCount:"+id, stringBlog);
        return Result.ok(blog);
    }
}
