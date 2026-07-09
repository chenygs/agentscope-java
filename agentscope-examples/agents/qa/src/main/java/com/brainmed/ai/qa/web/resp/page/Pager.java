package com.brainmed.ai.qa.web.resp.page;

import com.brainmed.ai.qa.web.resp.view.BaseJsonView;
import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * @author: chenygs
 * @date: 2024/2/29 12:41
 */
@Getter
@Setter
public class Pager<T> {
    @JsonView(value = {BaseJsonView.class})
    @Schema(description = "集合")
    private List<T> list;
    @Schema(description = "总记录")
    @JsonView(value = {BaseJsonView.class})
    private long total;
    @JsonView(value = {BaseJsonView.class})
    @Schema(description = "每页条数")
    private int size;
    @JsonView(value = {BaseJsonView.class})
    @Schema(description = "页码")
    private int current;
    @JsonView(value = {BaseJsonView.class})
    @Schema(description = "总页数")
    private int totalPage;

    public Pager(Page<T> data) {
        if(data == null){
            this.total = 0;
            this.totalPage = 0;
            this.current =  1;
            this.size = 10;
            this.list = new ArrayList<>(0);
        }else{
            this.total = data.getTotalElements();
            this.totalPage = data.getTotalPages();
            this.current = data.getNumber() + 1;
            this.size = data.getSize();
            this.list = data.getContent();
        }

    }
}
