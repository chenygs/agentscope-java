package com.brainmed.ai.qa.web.resp;

import com.brainmed.ai.qa.web.resp.enums.ResultCode;
import com.brainmed.ai.qa.web.resp.list.ListData;
import com.brainmed.ai.qa.web.resp.page.Pager;
import com.brainmed.ai.qa.web.resp.view.BaseJsonView;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.List;

/**
 * @author: chenygs
 * @date: 2024/2/29 12:38
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> implements Serializable {
    /**
     * 返回处理消息
     */
    @Schema(description = "备注")
    @JsonView(value = {BaseJsonView.class})
    private String msg;

    /**
     * 返回代码
     */
    @Schema(description = "返回代码")
    @JsonView(value = {BaseJsonView.class})
    private Integer code;

    /**
     * 返回数据对象 data
     */
    @Schema(description = "返回数据")
    @JsonView(value = {BaseJsonView.class})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;

    /**
     * 时间戳
     */
    @Schema(description = "时间戳")
    @JsonView(value = {BaseJsonView.class})
    private long timestamp;

    public static <T> R<Pager<T>> ok(Page<T> data) {
        Pager<T> tPaging = new Pager<>(data);
        return ok(tPaging);
    }

    public static <T> R<T> ok(T data) {
        return data(data, "操作成功！");
    }

    public static <T> R<ListData<T>> list(List<T> data) {
        ListData<T> list = new ListData<>(data);
        return data(list, "操作成功！");
    }

    public static <T> R<T> ok() {
        return new R<>(ResultCode.oK.getCode(), null, "操作成功");
    }

    public static <T> R<T> data(T data, String msg) {
        return new R<>(ResultCode.oK.getCode(), data, msg);
    }

    public static <T> R<T> data(int code, T data, String msg) {
        return new R<>(code, data, data == null ? "暂无承载数据" : msg);
    }

    public static R error(String error) {
        return error(ResultCode.ERROR.getCode(), error);
    }

    public static <T> R<T> error(int code, String msg) {
        return new R<>(code, null, msg);
    }

    private R(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isSuccess() {
        return code == ResultCode.oK.getCode();
    }

    public static <T> R<T> forbidden() {
        return error(ResultCode.ERROR.getCode(), "非法请求!");
    }
}
