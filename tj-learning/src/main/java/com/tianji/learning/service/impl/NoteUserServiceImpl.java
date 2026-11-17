package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.domain.po.NoteUser;
import com.tianji.learning.mapper.NoteUserMapper;
import com.tianji.learning.service.INoteUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 笔记与用户的关联关系 服务实现类
 * </p>
 *
 * @author Tom
 */
@Service
public class NoteUserServiceImpl extends ServiceImpl<NoteUserMapper, NoteUser> implements INoteUserService {

}