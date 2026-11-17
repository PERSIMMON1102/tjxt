package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.NoteFormDTO;
import com.tianji.learning.domain.po.Note;
import com.tianji.learning.domain.query.NoteAdminPageQuery;
import com.tianji.learning.domain.query.NotePageQuery;
import com.tianji.learning.domain.vo.NoteAdminDetailVO;
import com.tianji.learning.domain.vo.NoteAdminVO;
import com.tianji.learning.domain.vo.NoteVO;

/**
 * <p>
 * 学习笔记 服务类
 * </p>
 *
 * @author Tom
 */
public interface INoteService extends IService<Note> {

    /**
     * 新增笔记
     *
     * @param noteDTO 笔记信息
     */
    void saveNote(NoteFormDTO noteDTO);

    /**
     * 采集笔记
     *
     * @param id 被采集的笔记id
     */
    void gatherNote(Long id);

    /**
     * 取消采集笔记
     *
     * @param id 被采集的笔记id
     */
    void removeGatherNote(Long id);

    /**
     * 更新笔记
     *
     * @param noteDTO 笔记信息
     */
    void updateNote(NoteFormDTO noteDTO);

    /**
     * 用户端分页查询笔记
     *
     * @param query 查询条件
     * @return 笔记分页结果
     */
    PageDTO<NoteVO> queryNotePage(NotePageQuery query);

    /**
     * 管理端分页查询笔记
     *
     * @param query 查询条件
     * @return 笔记分页结果
     */
    PageDTO<NoteAdminVO> queryNotePageForAdmin(NoteAdminPageQuery query);

    /**
     * 管理端查询笔记详情
     *
     * @param id 笔记id
     * @return 笔记详情
     */
    NoteAdminDetailVO queryNoteDetailForAdmin(Long id);

    /**
     * 隐藏或显示笔记
     *
     * @param id     笔记id
     * @param hidden 是否隐藏
     */
    void hiddenNote(Long id, boolean hidden);

    /**
     * 删除我的笔记
     *
     * @param id 笔记id
     */
    void removeMyNote(Long id);
}