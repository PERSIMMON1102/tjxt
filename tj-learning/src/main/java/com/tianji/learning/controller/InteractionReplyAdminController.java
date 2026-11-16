package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 管理端前端控制器
 * </p>
 *
 * @author Tom
 * @since 2026-11-15
 */
@RestController
@RequestMapping("/admin/replies")
@Api(tags = "互动问答相关接口")
@RequiredArgsConstructor
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @ApiOperation("管理端分页查询回答或评论列表")
    @GetMapping("/page")
    public PageDTO<ReplyVO> queryReplyPageAdmin(ReplyPageQuery query) {
        return replyService.queryReplyPage(query, true);
    }

    @ApiOperation("管理端显示或隐藏回答或评论")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateReplyHidden(
            @ApiParam(value = "回答或评论id", example = "1") @PathVariable("id") Long id,
            @ApiParam(value = "是否隐藏", example = "true") @PathVariable("hidden") Boolean hidden) {
        replyService.updateReplyHidden(id, hidden);
    }
}