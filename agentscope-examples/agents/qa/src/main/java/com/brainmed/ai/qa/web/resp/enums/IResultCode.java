package com.brainmed.ai.qa.web.resp.enums;

import java.io.Serializable;

/**
 * 业务代码接口
 *
 * @author chenygs
 */
public interface IResultCode extends Serializable {

	/**
	 * 消息
	 *
	 * @return String
	 */
	String getMessage();

	/**
	 * 状态码
	 *
	 * @return int
	 */
	int getCode();

}
