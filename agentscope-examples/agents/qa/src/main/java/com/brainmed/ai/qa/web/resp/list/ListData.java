package com.brainmed.ai.qa.web.resp.list;

import com.brainmed.ai.qa.web.resp.view.BaseJsonView;
import com.fasterxml.jackson.annotation.JsonView;

import java.util.List;

/**
 * @author: chenygs
 * @date: 2024/5/20 14:00
 */
public class ListData<T> {
    @JsonView(value = {BaseJsonView.class})
    private List<T> list;

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public ListData(List<T> list) {
        this.list = list;
    }
}
