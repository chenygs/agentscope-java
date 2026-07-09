package com.brainmed.ai.qa.web.resp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;


/**
 * 业务代码枚举
 *
 * @author chenygs
 */
@Getter
@AllArgsConstructor
public enum ResultCode implements IResultCode {
    /**
     * 业务成功
     */
    oK(1, "操作成功"),
    ERROR(-1, "操作失败"),
    /**
     * 操作成功
     */
    SUCCESS(200, "操作成功"),
//
//	/**
//	 * 业务异常
//	 */
//	FAILURE(400, "业务异常"),
    /**
     * 缺少必要的请求参数 400
     */
    PARAM_MISS(400, "缺少必要的请求参数"),

    /**
     * 请求参数类型错误
     */
    PARAM_TYPE_ERROR(400, "请求参数类型错误"),

    /**
     * 请求参数绑定错误
     */
    PARAM_BIND_ERROR(400, "请求参数绑定错误"),

    /**
     * 参数校验失败
     */
    PARAM_VALID_ERROR(400, "参数校验失败"),
    /**
     * 请求未授权 401
     */
    UN_AUTHORIZED(401, "请求未授权"),
    /**
     * 请求被拒绝 403
     */
    FORBIDDEN(403, "请求被拒绝"),

    /**
     * 404 没找到请求
     */
    NOT_FOUND(404, "404 没找到请求"),

//	/**
//	 * 消息不能读取
//	 */
//	MSG_NOT_READABLE(400, "消息不能读取"),

    /**
     * 不支持当前请求方法 405
     */
    METHOD_NOT_SUPPORTED(405, "不支持当前请求方法"),

    /**
     * 不支持当前媒体类型 415
     */
    MEDIA_TYPE_NOT_SUPPORTED(415, "不支持当前媒体类型"),


    /**
     * 服务器异常 500
     */
    INTERNAL_SERVER_ERROR(500, "服务器异常"),

    alert_meeting_password(-2000, "会议需要输入密码"),
    custom_meeting_error(-2001, "自定义会议异常处理"),
    meeting_password_error(-2002, "会议密码错误"),
    meeting_need_apply_host_mic(-2003, "您需要向主持人申请开麦"),
    meeting_need_apply_host_camera(-2004, "您需要向主持人申请开启摄像头"),
    meeting_apply_submitted(-2005, "申请已提交"),
    meeting_audience_need_apply_host_mic(-2006, "普通观众申请开麦"),


    cns_experts_is_exists(-3000, "指导专家已存在"),
    custom_cns_error(-3001, "自定义术中指导异常处理"),
    cns_device_conflict(-3002, "导管室被占用"),
    cns_device_conflict_ur(-3003, "导管室被占用"),



    etm_create_or_update_etm_meeting(-4000, "创建或修改腾讯会议异常"),
    etm_sync_user_failed(-4001, "同步至腾讯用户失败"),

    ebs_exceed_backup_quantity(-5000, "您已超出可备份病例数量"),

    ;


    /**
     * code编码
     */
    final int code;
    /**
     * 中文信息描述
     */
    final String message;

}
