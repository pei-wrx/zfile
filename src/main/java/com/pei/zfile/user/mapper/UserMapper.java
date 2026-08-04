package com.pei.zfile.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pei.zfile.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

}
