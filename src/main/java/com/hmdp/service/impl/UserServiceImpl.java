package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合——返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合——生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.保存验证码到session
        //session.setAttribute("code", code);
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        opsForValue.set(RedisConstants.LOGIN_CODE_KEY +phone,code,5, TimeUnit.MINUTES);
        //5.发送验证码到手机
        log.debug("发送短信验证码：{}",code);
        //6.返回成功信息
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码
        //Object cacheCode=session.getAttribute("code");
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String cacheCode = ops.get(RedisConstants.LOGIN_CODE_KEY+loginForm.getPhone());
        String code = loginForm.getCode();
        if (cacheCode==null || !cacheCode.equals(code)) {
            //3.不一致，报错
            return Result.fail("验证码错误");
        }
        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5.判断用户是否存在
        if (user==null) {
            //6.不存在，创建新用户并保存
            user=createWithPhone(phone);
        }
        String uuid = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //7.存在，保存用户信息到Session
        //session.setAttribute("user", user);
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(
                userDTO,
                new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())
        );
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+uuid,userDTOMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+uuid, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(uuid);
    }

    private User createWithPhone(String phone) {
        //创建用户对象
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        //保存用户信息到数据库
        save(user);
        return user;
    }
}
